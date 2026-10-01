package com.bakr.queryforge.repo;

import com.bakr.queryforge.service.ProductRow;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Keyset (cursor) pagination over products, used by the Phase 6 OFFSET-vs-keyset experiment.
 *
 * The sort column is injected into the SQL string but strictly validated against a
 * whitelist (never user input verbatim). Filter predicates are added only when the
 * filter is actually present, so the generated SQL stays simple and plan-friendly.
 *
 * Cursor predicate for DESC order on column C with id tiebreaker:
 *   (C < :cv) OR (C = :cv AND id < :cid)
 * and the mirror-image with > for ASC.
 */
@Repository
public class ProductKeysetRepository {

    public record KeysetQuery(Long categoryId, String status, BigDecimal minPrice, BigDecimal maxPrice,
                               String sortColumn, boolean asc) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public ProductKeysetRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ProductRow> find(KeysetQuery q, Instant cursorValue, long cursorId, int limitPlusOne) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> where = new ArrayList<>();

        if (q.categoryId() != null) {
            where.add("category_id = :categoryId");
            params.addValue("categoryId", q.categoryId());
        }
        if (q.status() != null) {
            where.add("status = :status");
            params.addValue("status", q.status());
        }
        if (q.minPrice() != null) {
            where.add("price >= :minPrice");
            params.addValue("minPrice", q.minPrice());
        }
        if (q.maxPrice() != null) {
            where.add("price <= :maxPrice");
            params.addValue("maxPrice", q.maxPrice());
        }
        if (cursorValue != null) {
            String cmp = q.asc() ? ">" : "<";
            where.add("(" + q.sortColumn() + " " + cmp + " :cv OR (" + q.sortColumn() + " = :cv AND id " + cmp + " :cid))");            params.addValue("cv", Timestamp.from(cursorValue));
            params.addValue("cid", cursorId);
        }

        params.addValue("limit", limitPlusOne);

        String dir = q.asc() ? "ASC" : "DESC";
        String sql = """
                SELECT id, sku, name, category_id, price, stock_quantity, status, created_at, updated_at
                FROM products
                %s
                ORDER BY %s %s, id %s
                LIMIT :limit
                """.formatted(where.isEmpty() ? "" : "WHERE " + String.join(" AND ", where),
                q.sortColumn(), dir, dir);

        return jdbc.query(sql, params, ProductKeysetRepository::mapRow);
    }

    private static ProductRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        return new ProductRow(
                rs.getLong("id"),
                rs.getString("sku"),
                rs.getString("name"),
                rs.getLong("category_id"),
                rs.getBigDecimal("price"),
                rs.getInt("stock_quantity"),
                rs.getString("status"),
                created == null ? null : created.toInstant(),
                updated == null ? null : updated.toInstant());
    }

    /** Whitelist for sort columns accepted from the API layer. */
    public static String validatedColumn(String sortField) {
        return switch (sortField == null ? "" : sortField.toLowerCase(Locale.ROOT)) {
            case "createdat", "created_at" -> "created_at";
            case "price" -> "price";
            case "id" -> "id";
            default -> throw new IllegalArgumentException(
                    "Unsupported sort field: " + sortField + " (allowed: createdAt, price, id)");
        };
    }
}
