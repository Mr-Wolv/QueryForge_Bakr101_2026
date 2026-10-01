package com.bakr.queryforge.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Opaque pagination cursor: base64url(lastCreatedAt epoch millis : lastId).
 * Ties on the sort column are broken by id so pagination is deterministic.
 */
public final class Cursor {

    private Cursor() {
    }

    public static String encode(Instant createdAt, long id) {
        String raw = createdAt.toEpochMilli() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.US_ASCII));
    }

    public record Decoded(Instant createdAt, long id) {
    }

    public static Decoded decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII);
            int sep = raw.indexOf(':');
            if (sep <= 0) {
                throw new IllegalArgumentException();
            }
            return new Decoded(Instant.ofEpochMilli(Long.parseLong(raw.substring(0, sep))),
                    Long.parseLong(raw.substring(sep + 1)));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed cursor");
        }
    }
}
