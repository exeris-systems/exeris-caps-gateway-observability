package eu.exeris.caps.observability.api;

/**
 * High-performance parser and generator for {@link TraceContext}.
 *
 * <p>Implementations are thread-safe.
 *
 * @since 0.1.0
 */
public interface TraceContextManager {

    /**
     * Extracts existing traceparent or generates a new root trace context. A missing or malformed header, and a
     * header of any version other than {@code 00}, yields a new root context rather than an error.
     *
     * @param incomingHeader raw W3C traceparent header value (may be null or blank)
     * @return non-null TraceContext
     */
    TraceContext extractOrCreate(CharSequence incomingHeader);

    /**
     * Generates a new random root trace context.
     *
     * @return non-null TraceContext
     */
    TraceContext createRoot();

    /**
     * Derives a child span context from an existing context for upstream dispatching.
     *
     * @param parent parent context; when {@code null} a new root context is returned
     * @return child TraceContext with fresh spanId and the parent's trace ID and sampled flag
     */
    TraceContext createChild(TraceContext parent);
}
