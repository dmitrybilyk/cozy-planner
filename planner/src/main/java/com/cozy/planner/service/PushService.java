package com.cozy.planner.service;

import com.cozy.planner.model.entity.PushSubscription;
import com.cozy.planner.repositories.PushSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private static final String SUB_MAILTO = "mailto:admin@cozyplanner.app";
    private static final int AUTH_TAG_BITS = 128;
    private static final int SALT_LEN = 16;
    private static final int RECORD_SIZE = 4096;

    private final HttpClient httpClient;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private PrivateKey vapidPrivateKey;
    private PublicKey vapidPublicKey;
    private boolean initialized = false;

    public PushService(PushSubscriptionRepository pushSubscriptionRepository) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.httpClient = HttpClient.newHttpClient();
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
            kpg.initialize(ecSpec);
            KeyPair kp = kpg.generateKeyPair();
            this.vapidPrivateKey = kp.getPrivate();
            this.vapidPublicKey = kp.getPublic();
            this.initialized = true;
            log.info("VAPID keys generated");
        } catch (Exception e) {
            log.error("Failed to init VAPID keys", e);
        }
    }

    public Mono<Void> sendNotification(PushSubscription sub, String title, String message) {
        return sendNotification(sub, title, message, null, null);
    }

    public Mono<Void> sendNotification(PushSubscription sub, String title, String message, Long sessionId, String actionType) {
        if (!initialized) return Mono.empty();
        return Mono.fromRunnable(() -> {
            try {
                StringBuilder json = new StringBuilder();
                json.append("{\"title\":\"").append(esc(title)).append("\",\"message\":\"").append(esc(message)).append("\"");
                json.append(",\"url\":\"/\"");
                if (sessionId != null) {
                    json.append(",\"sessionId\":").append(sessionId);
                }
                if (actionType != null) {
                    json.append(",\"actionType\":\"").append(esc(actionType)).append("\"");
                }
                json.append("}");
                byte[] payload = json.toString().getBytes("UTF-8");
                byte[] salt = new byte[SALT_LEN];
                SecureRandom.getInstanceStrong().nextBytes(salt);

                KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
                kpg.initialize(new ECGenParameterSpec("secp256r1"));
                KeyPair ephemeral = kpg.generateKeyPair();
                byte[] serverRawPubKey = rawPublicKey((ECPublicKey) ephemeral.getPublic());

                byte[] clientRawPubKey = base64UrlDecode(sub.getP256dhKey());
                byte[] authSecret = base64UrlDecode(sub.getAuthKey());

                PublicKey clientPubKey = decodeRawPublicKey(clientRawPubKey);
                KeyAgreement ecdh = KeyAgreement.getInstance("ECDH");
                ecdh.init(ephemeral.getPrivate());
                ecdh.doPhase(clientPubKey, true);
                byte[] sharedSecret = ecdh.generateSecret();

                Mac hmac = Mac.getInstance("HmacSHA256");
                hmac.init(new SecretKeySpec(authSecret, "HmacSHA256"));
                byte[] prk = hmac.doFinal(sharedSecret);

                byte[] cek = hkdfExpand(prk, "aesgcm\0", clientRawPubKey, serverRawPubKey, 16);
                byte[] nonce = hkdfExpand(prk, "nonce\0", clientRawPubKey, serverRawPubKey, 12);

                byte[] plaintext = buildPlaintext(payload);
                byte[] aad = buildHeader(salt, serverRawPubKey);

                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"),
                        new GCMParameterSpec(AUTH_TAG_BITS, nonce));
                cipher.updateAAD(aad);
                byte[] ciphertext = cipher.doFinal(plaintext);

                byte[] body = concat(buildHeader(salt, serverRawPubKey), ciphertext);

                String jwt = createVapidJwt(sub.getEndpoint());
                String pubKeyB64 = base64UrlEncode(vapidPublicKey.getEncoded());

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(sub.getEndpoint()))
                        .header("Content-Type", "application/octet-stream")
                        .header("Content-Encoding", "aes128gcm")
                        .header("TTL", "86400")
                        .header("Urgency", "high")
                        .header("Authorization", "vapid t=" + jwt + ", k=" + pubKeyB64)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();

                HttpResponse<Void> resp = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                log.debug("Push result {}: {}", resp.statusCode(), sub.getEndpoint());
                if (resp.statusCode() == 410 || resp.statusCode() == 404) {
                    log.warn("Push subscription gone: {}", sub.getEndpoint());
                }
            } catch (Exception e) {
                log.error("Failed to send push", e);
            }
        });
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static byte[] buildPlaintext(byte[] payload) {
        byte[] result = new byte[1 + payload.length];
        result[0] = 0x02;
        System.arraycopy(payload, 0, result, 1, payload.length);
        return result;
    }

    private static byte[] buildHeader(byte[] salt, byte[] rawServerKey) {
        byte[] h = new byte[SALT_LEN + 4 + 1 + rawServerKey.length];
        System.arraycopy(salt, 0, h, 0, SALT_LEN);
        h[16] = (byte) ((RECORD_SIZE >> 24) & 0xFF);
        h[17] = (byte) ((RECORD_SIZE >> 16) & 0xFF);
        h[18] = (byte) ((RECORD_SIZE >> 8) & 0xFF);
        h[19] = (byte) (RECORD_SIZE & 0xFF);
        h[20] = (byte) rawServerKey.length;
        System.arraycopy(rawServerKey, 0, h, 21, rawServerKey.length);
        return h;
    }

    private static byte[] hkdfExpand(byte[] prk, String label, byte[] clientPub, byte[] serverPub, int len) throws Exception {
        byte[] info = concat(label.getBytes("UTF-8"), clientPub, serverPub);
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(prk, "HmacSHA256"));
        return Arrays.copyOf(hmac.doFinal(concat(info, new byte[]{1})), len);
    }

    private static byte[] rawPublicKey(ECPublicKey key) {
        byte[] encoded = key.getEncoded();
        for (int i = 0; i < encoded.length; i++) {
            if (encoded[i] == 0x04 && i + 65 <= encoded.length) {
                return Arrays.copyOfRange(encoded, i, i + 65);
            }
        }
        throw new RuntimeException("Cannot extract raw public key");
    }

    private static PublicKey decodeRawPublicKey(byte[] raw) throws Exception {
        if (raw[0] != 0x04) throw new IllegalArgumentException("Expected uncompressed EC key (0x04)");
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(raw, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(raw, 33, 65));
        KeyFactory kf = KeyFactory.getInstance("EC");
        return kf.generatePublic(new ECPublicKeySpec(new ECPoint(x, y), getECParams()));
    }

    private static ECParameterSpec getECParams() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        return ((ECPublicKey) kpg.generateKeyPair().getPublic()).getParams();
    }

    private static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] a : arrays) total += a.length;
        byte[] result = new byte[total];
        int pos = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        return result;
    }

    private String createVapidJwt(String endpoint) throws Exception {
        URI uri = URI.create(endpoint);
        String origin = uri.getScheme() + "://" + uri.getHost();
        if (uri.getPort() > 0 && uri.getPort() != 443 && uri.getPort() != 80) {
            origin += ":" + uri.getPort();
        }
        long exp = Instant.now().getEpochSecond() + 43200;
        String header = base64UrlEncode("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes("UTF-8"));
        String payload = base64UrlEncode(("{\"aud\":\"" + origin + "\",\"exp\":" + exp + ",\"sub\":\"" + SUB_MAILTO + "\"}").getBytes("UTF-8"));
        String signingInput = header + "." + payload;
        Signature sig = Signature.getInstance("SHA256withECDSA");
        sig.initSign(vapidPrivateKey);
        sig.update(signingInput.getBytes("UTF-8"));
        byte[] rawSig = derToFlat(sig.sign());
        return header + "." + payload + "." + base64UrlEncode(rawSig);
    }

    private static byte[] derToFlat(byte[] der) {
        int offset = 2;
        int rLen = der[offset + 1] & 0xFF;
        offset += 2;
        byte[] r = new byte[rLen];
        System.arraycopy(der, offset, r, 0, rLen);
        offset += rLen;
        int sLen = der[offset + 1] & 0xFF;
        offset += 2;
        byte[] s = new byte[sLen];
        System.arraycopy(der, offset, s, 0, sLen);
        byte[] flat = new byte[64];
        copyToRight(r, flat, 0);
        copyToRight(s, flat, 32);
        return flat;
    }

    private static void copyToRight(byte[] src, byte[] dest, int destOffset) {
        if (src.length == 33) System.arraycopy(src, 1, dest, destOffset, 32);
        else if (src.length >= 32) System.arraycopy(src, src.length - 32, dest, destOffset, 32);
        else System.arraycopy(src, 0, dest, destOffset + 32 - src.length, src.length);
    }

    private static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static byte[] base64UrlDecode(String data) {
        return Base64.getUrlDecoder().decode(data);
    }

    public Flux<Void> sendToTrainee(Long traineeId, String title, String message) {
        return sendToTrainee(traineeId, title, message, null, null);
    }

    public Flux<Void> sendToTrainee(Long traineeId, String title, String message, Long sessionId, String actionType) {
        return pushSubscriptionRepository.findAllByTraineeId(traineeId)
                .flatMap(sub -> sendNotification(sub, title, message, sessionId, actionType));
    }

    public Flux<Void> sendToMentor(Long mentorId, String title, String message) {
        return sendToMentor(mentorId, title, message, null, null);
    }

    public Flux<Void> sendToMentor(Long mentorId, String title, String message, Long sessionId, String actionType) {
        return pushSubscriptionRepository.findAllByMentorId(mentorId)
                .flatMap(sub -> sendNotification(sub, title, message, sessionId, actionType));
    }

    public String getVapidPublicKeyRawB64() {
        if (!initialized || !(vapidPublicKey instanceof ECPublicKey)) return null;
        byte[] encoded = vapidPublicKey.getEncoded();
        for (int i = 0; i < encoded.length; i++) {
            if (encoded[i] == 0x04 && i + 65 <= encoded.length) {
                return base64UrlEncode(Arrays.copyOfRange(encoded, i, i + 65));
            }
        }
        return null;
    }
}
