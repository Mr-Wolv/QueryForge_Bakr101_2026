package com.bakr.queryforge.experiment;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthSessionController {

    private final boolean authenticationEnabled;

    public AuthSessionController(@Value("${queryforge.auth.enabled:false}") boolean authenticationEnabled) {
        this.authenticationEnabled = authenticationEnabled;
    }

    @GetMapping("/session")
    public SessionResponse session(
            @AuthenticationPrincipal OidcUser user,
            CsrfToken csrfToken) {
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        if (user == null) {
            return new SessionResponse(authenticationEnabled, false, !authenticationEnabled, "");
        }
        boolean operator = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> Objects.equals(authority, "ROLE_OPERATOR"));
        String displayName = user.getFullName();
        if (displayName == null || displayName.isBlank()) {
            displayName = user.getName();
        }
        return new SessionResponse(true, true, operator, displayName);
    }

    public record SessionResponse(
            boolean authEnabled,
            boolean authenticated,
            boolean operator,
            String displayName) {
    }
}
