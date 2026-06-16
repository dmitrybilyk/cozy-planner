package com.linkease.server.web

import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class MeResponse(val email: String, val name: String)

@RestController
class MeController {

    @GetMapping("/api/me")
    fun me(@AuthenticationPrincipal user: OidcUser?): ResponseEntity<MeResponse> =
        if (user == null) ResponseEntity.status(401).build()
        else ResponseEntity.ok(MeResponse(email = user.email, name = user.givenName ?: user.email))
}
