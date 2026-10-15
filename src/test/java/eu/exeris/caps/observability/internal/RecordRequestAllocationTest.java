package eu.exeris.caps.observability.internal;

import eu.exeris.caps.observability.api.GatewayObservabilities;
import eu.exeris.caps.observability.api.GatewayObservabilityService;
import eu.exeris.kernel.tck.contract.AbstractSubsystemZeroAllocTck;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves, with the kernel allocation TCK (JFR events for {@code eu.exeris.*} objects plus the exact per-thread
 * allocated-bytes delta), that steady-state {@code recordRequest} allocates nothing: the striped metrics and
 * the preallocated access-log slots, with a path that carries a query string, a null trace ID, a wrapped ring
 * and the in-flight counters.
 */
@DisplayName("recordRequest: zero-alloc TCK")
class RecordRequestAllocationTest extends AbstractSubsystemZeroAllocTck {

    private static final String[] PATHS = {"/api/orders/42?expand=lines#top", "/health", "/" + "p".repeat(3000)};
    private static final String[] TRACE_IDS = {"4bf92f3577b34da6a3ce929d0e0e4736", null};
    private static final int[] STATUSES = {200, 204, 301, 404, 503, 100};

    private GatewayObservabilityService service;
    private int round;

    @Override
    protected String subsystemName() {
        return "GatewayObservability";
    }

    @Override
    protected String hotPathDescription() {
        return "recordRequest + incrementActiveRequests/decrementActiveRequests";
    }

    @Override
    protected boolean supportsZeroGcHotPath() {
        return true;
    }

    @Override
    protected void bootstrapSubsystem() {
        service = GatewayObservabilities.newService(64);
    }

    @Override
    protected void runSingleIteration() {
        int n = round++;
        service.incrementActiveRequests();
        service.recordRequest(n % 2 == 0 ? "GET" : "POST", PATHS[n % PATHS.length], STATUSES[n % STATUSES.length],
                1_000L + n, TRACE_IDS[n % TRACE_IDS.length]);
        service.decrementActiveRequests();
    }

    @Override
    protected void tearDownSubsystem() {
        assertThat(service.snapshot().totalRequests()).isEqualTo(round);
        assertThat(service.snapshot().recentRequests()).hasSize(50);
        service.close();
    }
}
