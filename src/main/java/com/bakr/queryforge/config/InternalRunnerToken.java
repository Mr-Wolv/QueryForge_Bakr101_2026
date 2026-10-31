package com.bakr.queryforge.config;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Process-local credential for the bundled benchmark runner.
 *
 * <p>k6 executes inside the backend container and calls the same authenticated
 * API as the browser. It has no OIDC session, so under the authenticated
 * profiles every iteration would receive 401 and the run would fail its
 * thresholds. Rather than weakening the public perimeter, the backend issues a
 * random token at startup and passes it only to the child process it spawns.
 * The token authorises read-only product endpoints (the benchmark target) and
 * is never exposed by an HTTP response or persisted to disk.
 */
@Component
public class InternalRunnerToken {

    public static final String HEADER = "X-QueryForge-Internal-Token";

    private final String value;

    public InternalRunnerToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        this.value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String value() {
        return value;
    }

    public boolean matches(String candidate) {
        if (candidate == null || candidate.isEmpty()) return false;
        return MessageDigest.isEqual(
                value.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }
}
