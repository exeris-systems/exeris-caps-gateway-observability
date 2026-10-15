package eu.exeris.caps.observability.internal;

import eu.exeris.caps.observability.api.AccessLogEntry;
import eu.exeris.caps.observability.api.GatewayObservabilityService;
import eu.exeris.caps.observability.api.MetricsSnapshot;
import eu.exeris.caps.observability.api.TraceContextManager;

import java.util.List;

/**
 * Default implementation of {@link GatewayObservabilityService}.
 *
 * @since 0.1.0
 */
public final class DefaultGatewayObservabilityService implements GatewayObservabilityService {

    private static final int MAX_LOGGED_PATH_LENGTH = 2048;

    private final StripedMetricsCollector metrics = new StripedMetricsCollector();
    private final FastW3CTraceContext traceManager = new FastW3CTraceContext();
    private final RingBufferAccessLog ringBuffer;

    public DefaultGatewayObservabilityService(int ringBufferSize) {
        this.ringBuffer = new RingBufferAccessLog(ringBufferSize);
    }

    @Override
    public void recordRequest(String method, String path, int statusCode, long durationNanos, String traceId) {
        metrics.record(statusCode, durationNanos);
        long nowMillis = System.currentTimeMillis();
        long durationMicros = durationNanos / 1000L;
        ringBuffer.add(new AccessLogEntry(nowMillis, method, pathOnly(path), statusCode, durationMicros, traceId));
    }

    /** The log keeps the path alone: no query string or fragment, and a bounded length. */
    private static String pathOnly(String path) {
        if (path == null) {
            return null;
        }
        int end = path.length();
        int q = path.indexOf('?');
        if (q >= 0) {
            end = q;
        }
        int f = path.indexOf('#');
        if (f >= 0 && f < end) {
            end = f;
        }
        return path.substring(0, Math.min(end, MAX_LOGGED_PATH_LENGTH));
    }

    @Override
    public void incrementActiveRequests() {
        metrics.incrementActive();
    }

    @Override
    public void decrementActiveRequests() {
        metrics.decrementActive();
    }

    @Override
    public MetricsSnapshot snapshot() {
        List<AccessLogEntry> recent = ringBuffer.recent(50);
        return new MetricsSnapshot(
                metrics.total(),
                metrics.get2xx(),
                metrics.get3xx(),
                metrics.get4xx(),
                metrics.get5xx(),
                metrics.active(),
                metrics.estimateQuantile(0.50),
                metrics.estimateQuantile(0.90),
                metrics.estimateQuantile(0.95),
                metrics.estimateQuantile(0.99),
                metrics.maxMillis(),
                metrics.avgMillis(),
                recent
        );
    }

    @Override
    public String exportPrometheus() {
        return DashboardRenderer.renderPrometheus(metrics);
    }

    @Override
    public String exportJson() {
        return DashboardRenderer.renderJson(snapshot());
    }

    @Override
    public String exportDashboardHtml() {
        return DashboardRenderer.renderHtmlDashboard(snapshot());
    }

    @Override
    public TraceContextManager traceContextManager() {
        return traceManager;
    }

    @Override
    public void close() {
        // No persistent resources to release
    }
}
