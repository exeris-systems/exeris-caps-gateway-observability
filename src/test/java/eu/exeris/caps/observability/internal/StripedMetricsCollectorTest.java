package eu.exeris.caps.observability.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StripedMetricsCollector Unit Tests")
class StripedMetricsCollectorTest {

    @Test
    @DisplayName("Counts total requests and partitions by HTTP status code classes")
    void countsAndPartitionsStatuses() {
        StripedMetricsCollector collector = new StripedMetricsCollector();

        collector.record(200, 1_000_000L); // 1 ms
        collector.record(204, 500_000L);   // 0.5 ms
        collector.record(301, 200_000L);   // 0.2 ms
        collector.record(400, 800_000L);   // 0.8 ms
        collector.record(404, 300_000L);   // 0.3 ms
        collector.record(500, 5_000_000L); // 5 ms
        collector.record(503, 2_000_000L); // 2 ms

        assertThat(collector.total()).isEqualTo(7);
        assertThat(collector.get2xx()).isEqualTo(2);
        assertThat(collector.get3xx()).isEqualTo(1);
        assertThat(collector.get4xx()).isEqualTo(2);
        assertThat(collector.get5xx()).isEqualTo(2);
        assertThat(collector.maxMillis()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("Tracks active in-flight requests correctly")
    void tracksActiveRequests() {
        StripedMetricsCollector collector = new StripedMetricsCollector();

        collector.incrementActive();
        collector.incrementActive();
        assertThat(collector.active()).isEqualTo(2);

        collector.decrementActive();
        assertThat(collector.active()).isEqualTo(1);

        collector.decrementActive();
        assertThat(collector.active()).isEqualTo(0);

        // Never goes negative
        collector.decrementActive();
        assertThat(collector.active()).isEqualTo(0);
    }

    @Test
    @DisplayName("Calculates approximate quantiles from histogram buckets")
    void calculatesQuantiles() {
        StripedMetricsCollector collector = new StripedMetricsCollector();

        // Add 100 requests of 0.5 ms (500_000 ns)
        for (int i = 0; i < 90; i++) {
            collector.record(200, 500_000L);
        }
        // Add 10 requests of 10 ms (10_000_000 ns)
        for (int i = 0; i < 10; i++) {
            collector.record(200, 10_000_000L);
        }

        assertThat(collector.total()).isEqualTo(100);
        assertThat(collector.estimateQuantile(0.50)).isLessThanOrEqualTo(1.0);
        assertThat(collector.estimateQuantile(0.99)).isGreaterThanOrEqualTo(5.0);
    }

    @Test
    @DisplayName("An unmatched decrement and a negative duration leave the counters sane")
    void guardsAgainstUnderflow() {
        StripedMetricsCollector collector = new StripedMetricsCollector();
        collector.decrementActive();
        collector.incrementActive();
        assertThat(collector.active()).isEqualTo(1);

        collector.record(200, -5_000_000L);
        assertThat(collector.totalLatencyNanos()).isZero();
        assertThat(collector.maxMillis()).isZero();
    }
}
