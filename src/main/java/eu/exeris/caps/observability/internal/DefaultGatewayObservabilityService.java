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

    private final StripedMetricsCollector metrics = new StripedMetricsCollector();
    private final FastW3CTraceContext traceManager = new FastW3CTraceContext();
    private final RingBufferAccessLog ringBuffer;

    public DefaultGatewayObservabilityService(int ringBufferSize) {
        this.ringBuffer = new RingBufferAccessLog(ringBufferSize);
    }

    @Override
    public void recordRequest(String method, String path, int statusCode, long durationNanos, String traceId) {
        durationNanos = Math.max(0L, durationNanos);
        metrics.record(statusCode, durationNanos);
        ringBuffer.add(System.currentTimeMillis(), method, path, statusCode, durationNanos / 1000L, traceId);
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
