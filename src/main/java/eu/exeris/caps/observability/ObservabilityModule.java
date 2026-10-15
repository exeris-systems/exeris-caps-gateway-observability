package eu.exeris.caps.observability;

import eu.exeris.caps.observability.api.GatewayObservabilityService;
import eu.exeris.sdk.annotation.capability.CapabilityModule;
import eu.exeris.sdk.annotation.capability.Provides;

/**
 * Capability declaration for {@code exeris-caps-gateway-observability}.
 *
 * <p>Provides the {@link GatewayObservabilityService} service contract for gateway policy pipelines.
 *
 * @since 0.1.0
 */
@CapabilityModule
@Provides(service = GatewayObservabilityService.class, version = "0.1.0")
public final class ObservabilityModule {

    private ObservabilityModule() {
        // Declaration carrier only — never constructed.
    }
}
