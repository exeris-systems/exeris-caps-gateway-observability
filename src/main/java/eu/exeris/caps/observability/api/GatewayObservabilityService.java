package eu.exeris.caps.observability.api;

/**
 * Enterprise service coordinating metrics collection, W3C distributed tracing,
 * and live aggregated preview for Exeris Gateway.
 *
 * <p>Thread-safety: all methods may be called concurrently from request threads. Recording and the
 * in-flight counters take no locks; a snapshot or export is a consistent-enough view of counters that keep
 * moving, not an atomic cut across all of them.
 *
 * @since 0.1.0
 */
public interface GatewayObservabilityService extends AutoCloseable {

    /**
     * Records the execution of an inbound request.
     *
     * <p>The access log keeps the path alone: the query string and fragment are dropped and the path is
     * truncated to 2048 characters. The method is truncated to 32 and the trace ID to 128 characters.
     *
     * @param method        HTTP method (e.g. GET, POST)
     * @param path          Request path; the query string and fragment are not retained
     * @param statusCode    Response status code
     * @param durationNanos Request duration in nanoseconds
     * @param traceId       Associated trace ID, or {@code null}
     */
    void recordRequest(String method, String path, int statusCode, long durationNanos, String traceId);

    /**
     * Increments the count of in-flight active requests.
     */
    void incrementActiveRequests();

    /**
     * Decrements the count of in-flight active requests.
     */
    void decrementActiveRequests();

    /**
     * Returns a point-in-time snapshot of the metrics and the most recent requests.
     *
     * @return current metrics snapshot
     */
    MetricsSnapshot snapshot();

    /**
     * Renders the request counters and latency histogram as Prometheus text exposition.
     *
     * @return metrics exported in Prometheus / OpenMetrics format
     */
    String exportPrometheus();

    /**
     * Renders a snapshot of the metrics and the most recent requests as a single JSON document.
     *
     * @return metrics and recent events exported in JSON format
     */
    String exportJson();

    /**
     * Renders a snapshot as a self-contained HTML page; request-derived text is HTML-escaped.
     *
     * @return self-contained interactive HTML dashboard
     */
    String exportDashboardHtml();

    /**
     * Returns the W3C trace context manager that belongs to this service.
     *
     * @return trace context manager
     */
    TraceContextManager traceContextManager();

    @Override
    void close();
}
