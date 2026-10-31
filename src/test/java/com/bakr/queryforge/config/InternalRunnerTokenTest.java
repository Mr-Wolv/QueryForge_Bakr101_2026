package com.bakr.queryforge.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InternalRunnerTokenTest {

    @Test
    void issuesAnUnpredictableTokenAndAcceptsOnlyExactMatches() {
        InternalRunnerToken first = new InternalRunnerToken();
        InternalRunnerToken second = new InternalRunnerToken();

        assertThat(first.value()).hasSizeGreaterThanOrEqualTo(32);
        assertThat(first.value()).isNotEqualTo(second.value());
        assertThat(first.matches(first.value())).isTrue();
        assertThat(first.matches(second.value())).isFalse();
        assertThat(first.matches(null)).isFalse();
        assertThat(first.matches("")).isFalse();
    }
}
