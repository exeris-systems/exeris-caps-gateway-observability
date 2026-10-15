package eu.exeris.caps.observability.api;

/**
 * Immutable W3C TraceContext representation.
 *
 * <p>Complies with the W3C Trace Context specification for distributed tracing:
 * {@code traceparent: 00-{trace_id}-{span_id}-{trace_flags}}.
 *
 * @since 0.1.0
 */
public record TraceContext(
        String traceId,
        String spanId,
        boolean sampled
) {
    /**
     * Creates a trace context, rejecting identifiers that W3C Trace Context does not allow.
     *
     * @throws IllegalArgumentException if {@code traceId} is not 32 lower-case hex characters or is all zeros,
     *                                  or {@code spanId} is not 16 lower-case hex characters or is all zeros
     */
    public TraceContext {
        if (!isLowerHex(traceId, 32) || isAllZero(traceId)) {
            throw new IllegalArgumentException("traceId must be 32 lower-case hex characters and not all zeros");
        }
        if (!isLowerHex(spanId, 16) || isAllZero(spanId)) {
            throw new IllegalArgumentException("spanId must be 16 lower-case hex characters and not all zeros");
        }
    }

    private static boolean isLowerHex(String value, int length) {
        if (value == null || value.length() != length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAllZero(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }

    /**
     * Serialises this context as a W3C {@code traceparent} header value, version {@code 00}.
     *
     * @return serialized W3C traceparent header value
     */
    public String toTraceparent() {
        return "00-" + traceId + "-" + spanId + (sampled ? "-01" : "-00");
    }
}
