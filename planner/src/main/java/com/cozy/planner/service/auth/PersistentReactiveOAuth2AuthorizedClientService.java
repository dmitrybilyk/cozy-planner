package com.cozy.planner.service.auth;

import com.cozy.planner.model.entity.GoogleOAuth2Credentials;
import com.cozy.planner.repositories.GoogleOAuth2CredentialsRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;

@Service
public class PersistentReactiveOAuth2AuthorizedClientService implements ReactiveOAuth2AuthorizedClientService {

    private final GoogleOAuth2CredentialsRepository repository;
    private final ReactiveClientRegistrationRepository clientRegistrationRepository;
    private final TokenEncryptionService encryptionService;

    public PersistentReactiveOAuth2AuthorizedClientService(
            GoogleOAuth2CredentialsRepository repository,
            ReactiveClientRegistrationRepository clientRegistrationRepository,
            TokenEncryptionService encryptionService) {
        this.repository = repository;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.encryptionService = encryptionService;
    }

    @Override
    public Mono<Void> saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal) {
        Assert.notNull(authorizedClient, "authorizedClient cannot be null");
        Assert.notNull(principal, "principal cannot be null");

        String principalName = principal.getName();
        GoogleOAuth2Credentials credentials = toEntity(authorizedClient, principalName);

        return repository.findByPrincipalName(principalName)
                .flatMap(existing -> {
                    credentials.setId(existing.getId());
                    credentials.setCreatedAt(existing.getCreatedAt());
                    credentials.setUpdatedAt(Instant.now());
                    if (credentials.getRefreshToken() == null) {
                        credentials.setRefreshToken(existing.getRefreshToken());
                    }
                    return repository.save(credentials).then();
                })
                .switchIfEmpty(Mono.defer(() -> repository.save(credentials).then()));
    }

    @Override
    public Mono<OAuth2AuthorizedClient> loadAuthorizedClient(String clientRegistrationId, String principalName) {
        Assert.hasText(clientRegistrationId, "clientRegistrationId cannot be empty");
        Assert.hasText(principalName, "principalName cannot be empty");

        return clientRegistrationRepository.findByRegistrationId(clientRegistrationId)
                .switchIfEmpty(Mono.error(() -> new IllegalArgumentException(
                        "Unknown client registration: " + clientRegistrationId)))
                .flatMap(registration -> repository.findByPrincipalName(principalName)
                        .map(creds -> toAuthorizedClient(creds, registration)));
    }

    @Override
    public Mono<Void> removeAuthorizedClient(String clientRegistrationId, String principalName) {
        Assert.hasText(clientRegistrationId, "clientRegistrationId cannot be empty");
        Assert.hasText(principalName, "principalName cannot be empty");

        return repository.findByPrincipalName(principalName)
                .flatMap(repository::delete)
                .then();
    }

    private GoogleOAuth2Credentials toEntity(OAuth2AuthorizedClient client, String principalName) {
        OAuth2AccessToken accessToken = client.getAccessToken();
        OAuth2RefreshToken refreshToken = client.getRefreshToken();

        return GoogleOAuth2Credentials.builder()
                .principalName(principalName)
                .accessToken(accessToken.getTokenValue())
                .refreshToken(refreshToken != null
                        ? encryptionService.encrypt(refreshToken.getTokenValue())
                        : null)
                .tokenType(accessToken.getTokenType().getValue())
                .issuedAt(accessToken.getIssuedAt())
                .expiresAt(accessToken.getExpiresAt())
                .scopes(String.join(",", accessToken.getScopes()))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private OAuth2AuthorizedClient toAuthorizedClient(GoogleOAuth2Credentials creds,
                                                      ClientRegistration registration) {
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                new OAuth2AccessToken.TokenType(creds.getTokenType()),
                creds.getAccessToken(),
                creds.getIssuedAt(),
                creds.getExpiresAt(),
                parseScopes(creds.getScopes())
        );

        OAuth2RefreshToken refreshToken = null;
        if (creds.getRefreshToken() != null) {
            refreshToken = new OAuth2RefreshToken(
                    encryptionService.decrypt(creds.getRefreshToken()),
                    creds.getIssuedAt()
            );
        }

        return new OAuth2AuthorizedClient(
                registration,
                creds.getPrincipalName(),
                accessToken,
                refreshToken
        );
    }

    private static Set<String> parseScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) return Collections.emptySet();
        return Set.of(scopes.split(","));
    }
}
