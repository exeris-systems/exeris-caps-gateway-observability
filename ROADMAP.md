---
title: "exeris-caps-gateway-observability — Roadmap"
type: roadmap
visibility: public
owning-repo: exeris-caps-gateway-observability
status: active
last-verified: 2026-10-15
---

# exeris-caps-gateway-observability — Roadmap

Status: **poc**. Version line: **0.1.x**. Licence: Exeris Commercial License (ADR-023), entitlement id
`exeris-caps-gateway-observability`.

Milestones are versions, not dates. Dates appear only on what has shipped; nothing has shipped yet.

> **`1.0.0` is not on this roadmap.** It is a gate, described in the last section. Status moves
> `poc` → `snapshot` → `released`.

---

## Where this sits

Layer 2 gateway building block. Provides `GatewayObservabilityService`: request counters by status class, a striped
latency collector with percentiles (`StripedMetricsCollector`), a bounded ring-buffer access log, W3C
`traceparent` handling (`TraceContextManager`), and three renderings of the same snapshot — Prometheus text,
JSON and a self-contained HTML dashboard. The access log keeps the path only: no query string or fragment.
One compile dependency, `exeris-kernel-spi`, used only for the `ObservabilityEntitlement` requirement; no
`@Requires`. Test totals are in the surefire report of the current head.

Gaps between the inventory and the code, found in the code:

- **The HLA §3.2 table does not list the cap yet.** The inventory has `exeris-caps-observability-bridge`
  (`community`) and the HLA §3.3 API Gateway row lists that, not this repository. The decision is taken:
  this is a separate commercial cap, registered in `exeris-docs#194` at layer 2. It is not merged into
  `observability-bridge`.
- Exposure of the renderings (`/metrics`, `/actuator/metrics`, the dashboard) is wired in the SKU, not here.

**Upstream constraints this repo does not own:**

| Constraint | Effect here | Owner |
|---|---|---|
| Kernel licence gate (ADR-088, ADR-089) in kernel `0.13.0-SNAPSHOT` | Entitlement resolvable only from that line | `exeris-kernel` |
| `RequestFilterChain` contract (ADR-099) | 0.2.0 binds to it | `exeris-caps-service-boundary-core` |
| ADR on the response-side seam (gateway-core 0.2.0) | Entry condition: no policy cap starts its 0.2.0 before it is accepted | `exeris-caps-gateway-core`, `exeris-docs` |
| HLA §3.2 row for this cap (separate commercial cap, layer 2, registered in `exeris-docs#194`) | The registry carries its status once the row lands | `exeris-docs` |
| Kernel telemetry is JFR-based (ADR-018) | This cap stays a separate in-process collector; the boundary to kernel telemetry is documented | `exeris-docs`, `exeris-kernel` |

---

## 0.1.0 — the code PR

- [ ] Defects found in the pre-import review are fixed in the import, each with a test
- [ ] Code imported; repository files and ADR link stubs added
- [ ] `@Provides(GatewayObservabilityService)` at contract `0.1.0`
- [ ] The access log keeps the path only (no query string or fragment), with a test
- [ ] Kernel `0.13.0-SNAPSHOT`; SDK `0.12.0` and tooling `0.10.0` pins aligned
- [ ] Artefacts stay `0.1.0-SNAPSHOT` until kernel `0.13.0` is released; a non-snapshot `0.1.0` is cut only after that
- [ ] Licence enforcement: `ObservabilityEntitlement` (id `exeris-caps-gateway-observability`),
      `META-INF/services` registration, TCK test and boot test against the kernel's Phase 0 gate
- [ ] `build.yml` green on JDK 25 and 26; `cap-manifest.json` committed through the two-pass build
- [ ] `exeris-docs` PR adding the HLA §3.2 row for the separate commercial cap

**Exit gate:** `mvn verify` green on both JDK rows; entitlement test fails without the id; the cap has a registry row.

## 0.2.0 — bind to `RequestFilterChain`

Follows the gateway-core PR that replaces its `PolicyChain`.

- [ ] Optional `@Requires(RequestFilterChain, "[0.1.0,0.2.0)")` and an observability binding in `api`, on
      the pattern of `exeris-caps-rate-limiting`; replaces the SKU-side `ObservabilityGatewayPolicy`
- [ ] Recording status and duration happens after the handler runs; `AdmissionFilter` is pre-handler only, so this
      depends on the response-side ADR (entry condition: accepted before this milestone starts; see gateway-core 0.2.0)
- [ ] Configurable redaction of sensitive headers and parameters, and a setting for whether client addresses are recorded
- [ ] `activeRequests` increment/decrement must stay paired on every exit path, including a filter short-circuit and an exception

## 0.3.0 — Independent security review

- [ ] Covered by the API Gateway SKU review (one review; scope: the SKU and every composed cap); this
      repository's public API and documented behaviour are listed in that scope
- [ ] Threat model written and committed alongside the code
- [ ] Findings handled through `SECURITY.md` private reporting
- [ ] Fixes released, each with a regression test

## 0.4.0 — re-measurement and snapshot

- [ ] Recording overhead measured in the `exeris-benchmarks` harness on the reference profile, published there; no figure stated here
- [ ] Percentile accuracy of the latency collector checked against a reference distribution in a test
- [ ] Snapshot artefact on GitHub Packages; status `poc` → `snapshot`

---

## 1.0.0 — a gate, not a milestone

Entry condition: the boundary to kernel telemetry is documented, the
API Gateway SKU has run on the post-0.2.0 binding, and the review is closed. Then
`GatewayObservabilityService` freezes, a semver gate is added, and the status becomes `released`.

## Deliberately out of scope

- Exporters to external systems (OTLP, StatsD): not in this repository.
- Kernel JFR telemetry and its decoder: `exeris-telemetry-spec`, `exeris-enterprise-observability`.
