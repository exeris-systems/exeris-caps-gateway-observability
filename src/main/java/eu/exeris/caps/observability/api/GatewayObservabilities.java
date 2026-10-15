package eu.exeris.caps.observability.api;

import eu.exeris.caps.observability.internal.DefaultGatewayObservabilityService;

/**
 * Entry point factory for creating {@link GatewayObservabilityService} instances.
 *
 * @since 0.1.0
 */
public final class GatewayObservabilities {

    private GatewayObservabilities() {
    }

    /**
     * Creates a new GatewayObservabilityService with default settings.
     *
     * @return non-null service
     */
    public static GatewayObservabilityService newService() {
        return new DefaultGatewayObservabilityService(500);
    }

    /**
     * Creates a new GatewayObservabilityService with custom recent requests capacity. Every access-log slot
     * is allocated up front (about 4.4 KB each), so the capacity is paid for in memory at creation.
     *
     * @param ringBufferSize maximum recent requests to retain in memory for the access log; positive
     * @return non-null service
     * @throws IllegalArgumentException if {@code ringBufferSize} is not positive
     */
    public static GatewayObservabilityService newService(int ringBufferSize) {
        return new DefaultGatewayObservabilityService(ringBufferSize);
    }
}
