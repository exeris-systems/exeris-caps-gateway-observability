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
     * Creates a new GatewayObservabilityService with custom recent requests capacity.
     *
     * @param ringBufferSize maximum recent requests to retain in memory for dashboard
     * @return non-null service
     */
    public static GatewayObservabilityService newService(int ringBufferSize) {
        return new DefaultGatewayObservabilityService(ringBufferSize);
    }
}
