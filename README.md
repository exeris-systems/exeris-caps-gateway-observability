# exeris-caps-gateway-observability

Request metrics, trace context and a live preview for the Gateway family as an Exeris capability. `GatewayObservabilityService` records requests, keeps counters and a short access log, and exports them as Prometheus text, JSON or a self-contained HTML dashboard.

Status: proof of concept, built against `exeris-kernel` 0.13.0-SNAPSHOT on JDK 25 LTS. The API is not stable and no artefact has been published.

## Where it sits

- **Layer:** Layer 2, a gateway building block, in [`high-level-architecture.md`](https://github.com/exeris-systems/exeris-docs/blob/main/high-level-architecture.md) §3.2. Other gateway code calls it; it is not a policy and never changes a request.
- **Depends on:** `exeris-kernel-spi` (the entitlement requirement) and `exeris-sdk-annotations` (`provided`).
- **Provides** (`@Provides`, contract version 0.1.0): `GatewayObservabilityService`.
- **Requires:** no other capability.
- **Wall (ADR-055):** `eu.exeris.caps.observability.api` is the cross-cap surface; `eu.exeris.caps.observability.internal` is private, and a sibling cap reaching into it fails the build. No Spring, servlet, Netty or reactive type may appear (ADR-006).

## What the source contains

- `recordRequest(method, path, statusCode, durationNanos, traceId)` feeds `StripedMetricsCollector` (striped counters and a latency histogram) and `RingBufferAccessLog` (the most recent requests, in preallocated slots overwritten in place; `AccessLogEntry` records are copied out when a snapshot or export is built). `recordRequest` allocates nothing, which a test on the kernel allocation TCK proves; `incrementActiveRequests` and `decrementActiveRequests` track in-flight requests.
- `snapshot()` returns a `MetricsSnapshot`; `exportPrometheus()`, `exportJson()` and `exportDashboardHtml()` render it (`DashboardRenderer`).
- `TraceContextManager` extracts a W3C `traceparent` header or creates a root context, and derives child contexts (`FastW3CTraceContext`).

## Build & test

Requires JDK 25 and Maven 3.9+.

```bash
mvn compile -Dexeris.codegen.skip=true   # pass 1 — the processor writes capability metadata
mvn verify                               # pass 2 — generate cap-manifest.json, validate the graph, scan the Wall
```

Both passes are needed after a `clean`: `exeris:generate` runs before `compile`, and `compile` is when the processor writes the metadata it reads. `src/main/generated/java/cap-manifest.json` is committed, so a clean `git status` after `mvn verify` shows the manifest matches the declaration.

The upstreams are not on Maven Central. `exeris-sdk` and `exeris-tooling` are built from source, and `exeris-kernel` 0.13.0-SNAPSHOT is resolved from GitHub Packages, which needs a token with `read:packages`.

## Documentation

- [`AGENTS.md`](AGENTS.md) — the repository's operating contract and invariants.
- [Capability Author Guide](https://github.com/exeris-systems/exeris-docs/blob/main/cap-author-guide.md) — how a cap declares `@CapabilityModule`, `@Provides`, `@Requires` and `@CapabilityLifecycle`.
- [Capability licence registry](https://github.com/exeris-systems/exeris-docs/blob/main/cap-license-registry.md) — the licence value of every cap.

Where they disagree, the ADRs below and the HLA outrank this README.

## Governing ADRs

- [ADR-006](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-006-spring-free-kernel-boundary.md) — Spring-free kernel boundary.
- [ADR-023](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-023-capability-licensing-taxonomy.md) — capability licensing taxonomy.
- [ADR-024](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-024-capability-composition-model.md) — capability composition model.
- [ADR-053](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-053-sku-composition-manifest-format.md) — composition manifest format.
- [ADR-055](https://github.com/exeris-systems/exeris-tooling/blob/main/docs/adr/ADR-055-cap-tier-wall-guard.md) — cap-tier Wall guard.
- [ADR-088](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-088-cryptographic-license-manifest-and-offline-verification.md) — signed licence manifest.
- [ADR-089](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-089-capability-entitlement-enforcement-and-runtime-contract.md) — capability entitlement enforcement.

Registry: [`adr-index.md`](https://github.com/exeris-systems/exeris-docs/blob/main/adr-index.md).

## License

Exeris Commercial License 1.1 — see [`LICENSE`](LICENSE). `exeris-caps-gateway-observability` is a `commercial` capability under [ADR-023](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-023-capability-licensing-taxonomy.md): the source is available to read, build, test and evaluate, and production use requires a commercial licence.

Its entitlement id is `exeris-caps-gateway-observability`, the artifactId ([ADR-088](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-088-cryptographic-license-manifest-and-offline-verification.md), [ADR-089](https://github.com/exeris-systems/exeris-docs/blob/main/adr/ADR-089-capability-entitlement-enforcement-and-runtime-contract.md)).

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md). Report a suspected vulnerability as described in [`SECURITY.md`](SECURITY.md), never in a public issue.
