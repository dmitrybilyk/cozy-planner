package com.cozy.planner.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("google_oauth2_credentials")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleOAuth2Credentials {

    @Id
    private Long id;

    private String principalName;

    private String accessToken;

    private String refreshToken;

    private String tokenType;

    private Instant issuedAt;

    private Instant expiresAt;

    private String scopes;

    private Instant createdAt;

    private Instant updatedAt;
}
