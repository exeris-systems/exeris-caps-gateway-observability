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
     * A negative duration is recorded as zero.
     *
     * <p>The call allocates nothing: the metrics counters are striped and the access log overwrites a
     * preallocated slot in place. An entry is dropped, with no effect on the metrics, only if writers are a
     * full lap of the access log apart. {@code AccessLogEntry} objects are created when a snapshot or an export
     * copies the slots out.
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
     * <p>The list of recent requests holds at most the 50 most recent entries, whatever the access-log
     * capacity. The status-class counters cover 2xx to 5xx; a status below 200 counts in the total and the latency
     * histogram only.
     *
     * @return current metrics snapshot
     */
    MetricsSnapshot snapshot();

    /**
     * Renders the request counters and latency histogram as Prometheus text exposition.
     *
     * @return metrics in the classic Prometheus text exposition format
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
     * @return self-contained HTML page that reloads itself every second
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
