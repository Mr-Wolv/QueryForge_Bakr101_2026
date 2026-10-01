package com.bakr.queryforge;

import com.bakr.queryforge.service.Cursor;
import com.bakr.queryforge.service.SearchParams;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorTest {

    @Test
    void roundTrips() {
        Instant t = Instant.parse("2024-06-01T12:34:56.789Z");
        String encoded = Cursor.encode(t, 4242L);
        Cursor.Decoded d = Cursor.decode(encoded);
        assertThat(d.createdAt()).isEqualTo(t);
        assertThat(d.id()).isEqualTo(4242L);
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> Cursor.decode("@@@not-base64@@@"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Cursor.decode("aGVsbG8")) // base64 of "hello", no separator
                .isInstanceOf(IllegalArgumentException.class);
    }
}

class SearchParamsTest {

    @Test
    void defaultsSortToCreatedAt() {
        SearchParams p = new SearchParams(null, null, null, null, null, false, 0, 50);
        assertThat(p.sortField()).isEqualTo("createdAt");
    }

    @Test
    void acceptsCanonicalSortAliases() {
        assertThat(new SearchParams(null, null, null, null, "created_at", false, 0, 50).sortField()).isEqualTo("createdAt");
        assertThat(new SearchParams(null, null, null, null, "PRICE", false, 0, 50).sortField()).isEqualTo("price");
    }

    @Test
    void rejectsInvalidSizeAndPriceRange() {
        assertThatThrownBy(() -> new SearchParams(null, null, null, null, null, false, 0, 500))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SearchParams(null, null, null, null, null, false, -1, 50))
                .isInstanceOf(IllegalArgumentException.class);
        java.math.BigDecimal five = java.math.BigDecimal.TEN.subtract(java.math.BigDecimal.valueOf(5));
        java.math.BigDecimal one = java.math.BigDecimal.ONE;
        assertThatThrownBy(() -> new SearchParams(null, null, five, one, null, false, 0, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankStatusBecomesNull() {
        SearchParams p = new SearchParams(null, "  ", null, null, null, false, 0, 50);
        assertThat(p.status()).isNull();
    }
}
