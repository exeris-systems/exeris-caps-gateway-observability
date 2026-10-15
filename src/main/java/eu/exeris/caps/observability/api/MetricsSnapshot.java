package eu.exeris.caps.observability.api;

import java.util.List;

/**
 * Point-in-time snapshot of gateway operational metrics.
 *
 * @since 0.1.0
 */
public record MetricsSnapshot(
        long totalRequests,
        long count2xx,
        long count3xx,
        long count4xx,
        long count5xx,
        long activeRequests,
        double p50Millis,
        double p90Millis,
        double p95Millis,
        double p99Millis,
        double maxMillis,
        double avgMillis,
        List<AccessLogEntry> recentRequests
) {
}
