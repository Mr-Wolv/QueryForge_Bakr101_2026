package com.bakr.queryforge.experiment;

import java.math.BigDecimal;

/**
 * Difference for one metric between a baseline (left) and candidate (right) run.
 *
 * <p>{@code deltaPercent} is {@code null} when the baseline is zero, because a
 * percentage change from zero is undefined rather than infinite.
 */
public record MetricDelta(
        BigDecimal left,
        BigDecimal right,
        BigDecimal delta,
        BigDecimal deltaPercent,
        String unit
) {
}
