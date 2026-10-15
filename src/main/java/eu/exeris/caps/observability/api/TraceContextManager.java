package eu.exeris.caps.observability.api;

/**
 * High-performance parser and generator for {@link TraceContext}.
 *
 * @since 0.1.0
 */
public interface TraceContextManager {

    /**
     * Extracts existing traceparent or generates a new root trace context.
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
     * @param parent non-null parent context
     * @return child TraceContext with fresh spanId
     */
    TraceContext createChild(TraceContext parent);
}
