package eu.exeris.caps.observability.api;

/**
 * Lightweight access log record for real-time aggregation and live preview. An entry is a copy made when a
 * snapshot or export reads the access log; recording a request does not create one.
 *
 * @since 0.1.0
 */
public record AccessLogEntry(
        long timestampMillis,
        String method,
        String path,
        int statusCode,
        long durationMicros,
        String traceId
) {
}
