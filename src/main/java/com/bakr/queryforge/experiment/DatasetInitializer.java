package com.bakr.queryforge.experiment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class DatasetInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(DatasetInitializer.class);

    private static final String[] STATUSES = {"ACTIVE", "INACTIVE", "DISCONTINUED"};
    private static final int[] STATUS_WEIGHTS = {55, 30, 15};

    private final JdbcTemplate jdbc;

    public DatasetInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public InitResult initialize(int rowCount) {
        long existing = jdbc.queryForObject("SELECT count(*) FROM products", Long.class);
        if (existing > 0) {
            return InitResult.alreadyExists(existing);
        }

        LOG.info("Initializing product dataset with {} rows", rowCount);
        long start = System.currentTimeMillis();

        String sql = """
            INSERT INTO products (id, sku, name, category_id, price, stock_quantity, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        int batchSize = 10_000;
        List<Object[]> batch = new ArrayList<>(batchSize);
        Random rng = new Random(42);

        for (int i = 1; i <= rowCount; i++) {
            int categoryId = rng.nextInt(5) + 1;
            BigDecimal price = BigDecimal.valueOf(5.00 + rng.nextDouble() * 495.99);
            price = price.setScale(2, BigDecimal.ROUND_HALF_UP);
            int stock = rng.nextInt(1001);
            String status = weightedStatus(rng);
            Instant created = Instant.ofEpochSecond(1704067200L + i); // 2024-01-01T00:00:00Z + i seconds
            Instant updated = created.plusSeconds(3600);

            batch.add(new Object[]{
                i,
                String.format("SKU%08d", i),
                "Product " + i,
                categoryId,
                price,
                stock,
                status,
                Timestamp.from(created),
                Timestamp.from(updated)
            });

            if (batch.size() >= batchSize) {
                jdbc.batchUpdate(sql, batch);
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            jdbc.batchUpdate(sql, batch);
        }

        long elapsed = System.currentTimeMillis() - start;
        long count = jdbc.queryForObject("SELECT count(*) FROM products", Long.class);
        LOG.info("Dataset initialization complete: {} rows in {} ms", count, elapsed);

        return InitResult.success(count, elapsed);
    }

    private static String weightedStatus(Random rng) {
        int total = 0;
        for (int w : STATUS_WEIGHTS) total += w;
        int pick = rng.nextInt(total);
        int cumulative = 0;
        for (int i = 0; i < STATUSES.length; i++) {
            cumulative += STATUS_WEIGHTS[i];
            if (pick < cumulative) return STATUSES[i];
        }
        return STATUSES[STATUSES.length - 1];
    }

    public record InitResult(boolean alreadyExists, long rowCount, long elapsedMs, String message) {
        static InitResult alreadyExists(long count) {
            return new InitResult(true, count, -1, "Dataset already contains " + count + " rows.");
        }

        static InitResult success(long count, long elapsedMs) {
            return new InitResult(false, count, elapsedMs, "Initialized " + count + " rows in " + elapsedMs + " ms.");
        }
    }
}
