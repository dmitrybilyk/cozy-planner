package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.GoogleOAuth2Credentials;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface GoogleOAuth2CredentialsRepository extends ReactiveCrudRepository<GoogleOAuth2Credentials, Long> {

    Mono<GoogleOAuth2Credentials> findByPrincipalName(String principalName);
}
