package com.cozy.planner.service.auth;

import org.springframework.stereotype.Service;

@Service
public class TokenEncryptionService {

    /**
     * Encrypts a refresh token before persisting.
     * TODO: Replace with real encryption (e.g., AES-GCM with a key management system).
     */
    public String encrypt(String rawToken) {
        if (rawToken == null) return null;
        return rawToken;
    }

    /**
     * Decrypts a refresh token after loading from DB.
     * TODO: Replace with real decryption matching the encryption strategy.
     */
    public String decrypt(String encryptedToken) {
        if (encryptedToken == null) return null;
        return encryptedToken;
    }
}
