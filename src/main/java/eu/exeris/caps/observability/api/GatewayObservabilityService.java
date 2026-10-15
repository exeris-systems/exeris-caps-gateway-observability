package eu.exeris.caps.observability.api;

/**
 * Enterprise service coordinating metrics collection, W3C distributed tracing,
 * and live aggregated preview for Exeris Gateway.
 *
 * @since 0.1.0
 */
public interface GatewayObservabilityService extends AutoCloseable {

    /**
     * Records the execution of an inbound request.
     *
     * @param method        HTTP method (e.g. GET, POST)
     * @param path          Request path
     * @param statusCode    Response status code
     * @param durationNanos Request duration in nanoseconds
     * @param traceId       Associated trace ID
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
     * @return current metrics snapshot
     */
    MetricsSnapshot snapshot();

    /**
     * @return metrics exported in Prometheus / OpenMetrics format
     */
    String exportPrometheus();

    /**
     * @return metrics and recent events exported in JSON format
     */
    String exportJson();

    /**
     * @return self-contained interactive HTML dashboard
     */
    String exportDashboardHtml();

    /**
     * @return trace context manager
     */
    TraceContextManager traceContextManager();

    @Override
    void close();
}
