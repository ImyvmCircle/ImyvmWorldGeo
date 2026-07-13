# Repository agent guide

This file defines the default working rules for automated coding agents in this repository. Follow user instructions first, then this file. Do not weaken correctness, persistence safety, or addon compatibility to reduce a diff.

## Project context

- ImyvmWorldGeo is a Fabric Minecraft mod written primarily in Kotlin, with Java mixins.
- `com.imyvm.iwg.domain` contains domain state.
- `com.imyvm.iwg.application` contains use cases and runtime behavior.
- `com.imyvm.iwg.infra` contains persistence and external integrations.
- `com.imyvm.iwg.inter.api` is the supported addon API.
- Public declarations outside `inter.api` are not automatically supported APIs, but existing JVM signatures may still require compatibility.

## Working approach

- Before implementation, provide a complete, reviewable work route covering scope, phases, expected files, compatibility or rollback concerns, verification, and explicit exclusions.
- Until the user gives an explicit instruction to execute that route, perform read-only investigation only. Do not modify source code, tests, configuration, resources, or formal documentation. Revisions to local notes under `docs/plan/` are allowed only to support review of the proposed route.
- Read the complete call chain before editing: constructor or API boundary, application logic, resolver, persistence, and tests.
- Search every caller before changing a shared function. Fix the root cause at the narrowest shared boundary.
- Keep changes vertically complete. A domain invariant change must update its resolver, mutation path, codec, public API, compatibility layer, and tests together.
- Prefer the smallest correct change. Reuse existing code and standard library features before adding abstractions or dependencies.
- Do not add speculative interfaces, factories, repositories, DSLs, caches, or configuration.
- Preserve unrelated user changes in a dirty worktree. Never use destructive Git commands unless explicitly requested.
- Do not commit, push, rebase, or create a pull request unless the user explicitly asks. The user normally creates signed commits.

## Kotlin and Java style

- Give each function one clear responsibility.
- Prefer explicit, non-null parameters for normal Minecraft runtime states.
- Prevent state explosion: do not encode modes through combinations of nullable parameters, booleans, or values whose validity depends on each other. Split operations or model the valid states with a sealed type, enum, or validated value object so illegal combinations are not representable.
- Avoid marker interfaces without semantics. An interface must define a capability, invariant, or API constraint that callers actually use. Prefer a sealed union, enum, annotation, or no abstraction when the interface only labels a type.
- Keep `Any` out of the domain model. `Any`, unchecked casts, broad base keys, and raw collections may exist only at serialization, reflection, command parsing, or legacy compatibility boundaries; validate and convert them to a concrete domain type immediately.
- Do not let a compatibility facade's `Any` or nullable protocol flow back into production resolvers, stores, or mutations.
- Permission code accepts permission keys; rule code accepts rule keys; effect code accepts effect keys.
- Use exhaustive `when` expressions for closed domain types.
- Avoid `!!`. If a value is required, validate it once at the boundary; otherwise handle the nullable result directly.
- Avoid contradictory null handling such as `!!?.`, `!! ?:`, or checking a value for null after asserting it.
- Prefer immutable results and snapshots at API boundaries. Keep mutable collections private to their owning aggregate.
- Use data/value types where equality is part of the domain meaning.
- Keep Java mixins thin: collect Minecraft callback context, call application logic, and apply the result.
- Match the existing formatting and naming in touched files. Do not perform unrelated formatting rewrites.

## Domain invariants

- A `GeoScope` passed with a `Region` must belong to that Region. Validate ownership at public and mutation boundaries.
- Do not pass a Region and an unrelated child object or child collection as independent parameters when a typed owner/target can prevent mismatch.
- Global and player-specific settings are distinct subjects. Do not represent this distinction with an unexplained nullable UUID inside production logic.
- Built-in permission inheritance applies only to `PermissionKey`. Extension permission keys are exact-match keys unless a future reviewed API explicitly adds inheritance.
- Extension permission and rule keys must be registered before supported API queries or mutations use them.
- Rule and entry/exit settings do not support personal subjects.
- Duplicate settings are identified by setting type/key and subject. Do not restore list-order-dependent duplicate behavior.
- `Region.settings` and `GeoScope.settings` are legacy ABI snapshots. Mutating a returned list must not be used as a write path.
- All setting mutations go through the controlled store/application boundary.
- A failed persistence operation must not report success. Restore in-memory state when a mutation cannot be saved.
- Preserve existing database tags, enum encoding, and legacy record layout unless a migration and compatibility test are included.
- `GeoShape` internal geometries must be fully validated at the construction boundary. Do not accept unvalidated index-based parameter slices into production logic; transform them into descriptive geometry records immediately at the input codec edge.

## Performance and threading

- Minecraft interaction and movement handlers are hot paths. Do not scan or `filterIsInstance` over settings on each event when an indexed resolver exists.
- Avoid temporary lists, composite lookup objects, reflection, and repeated parsing in hot paths.
- Bound work before allocation. Do not enumerate an entire region, shape, chunk set, or search cube before enforcing its limit.
- Use overflow-safe arithmetic for coordinates, areas, squared distances, ticks, and IDs.
- Assume core gameplay mutations run on the server thread unless an API explicitly documents otherwise.
- Do not add concurrent collections or locks without a demonstrated cross-thread caller. Preserve synchronization where addon-facing APIs make thread ownership uncertain.

## Addon compatibility

- Treat `com.imyvm.iwg.inter.api` as the supported addon surface.
- Before changing a public JVM signature, inspect its compiled descriptor and known callers.
- Prefer a typed replacement plus a deprecated delegating wrapper over an immediate signature removal.
- Old addons must continue to load and call retained compatibility methods. Unsafe mutation behavior does not need to remain effective.
- Deprecation does not schedule deletion. An API becomes eligible for removal only after at least two released versions and explicit maintainer approval.
- Do not automatically advance deprecations to `DeprecationLevel.ERROR` or `HIDDEN`.
- Record addon-facing compatibility changes in `docs/addon-api-compatibility.md` and provide a concrete replacement or migration example.
- When an unreleased marker is used in compatibility docs, replace it with the actual version during release preparation.

## Persistence and errors

- Validate untrusted or persisted values before they enter the domain model.
- Reject unknown setting subclasses, invalid enum ordinals, invalid counts, malformed IDs, and invalid geometry parameters at their boundary.
- Keep writes atomic where an existing atomic write helper is available.
- Do not translate arbitrary exception messages as i18n keys. Domain/application failures should expose known message keys or typed errors.
- User-visible success messages must be sent only after the operation and required save both succeed.

## Tests and verification

- Every non-trivial bug fix or domain branch needs the smallest regression test that would fail without the change.
- Prefer focused tests for the touched capability, then run the complete build before handoff.
- On Windows, use `gradlew.bat`; on Unix-like systems, use `./gradlew`.
- Minimum final verification for code changes:

  ```text
  gradlew.bat test build
  git diff --check
  ```

- For persistence changes, include round-trip and malformed-input coverage.
- For addon compatibility changes, inspect JVM descriptors with `javap` or an equivalent ABI check.
- For permission changes, cover Region/Scope, global/player, exact/parent, default, and extension-key behavior.
- Report skipped checks and the reason. Do not claim completion when required verification failed.

## Documentation and repository hygiene

- Put all temporary requirements, execution plans, findings, progress logs, and agent working notes under `docs/plan/`.
- `docs/plan/` is local working state and must not be staged or committed.
- `logs/` is runtime output and must not be staged or committed.
- Formal user/addon documentation belongs elsewhere under `docs/` and may be committed.
- Keep `README.md` focused on released behavior and public usage.
- Before staging, inspect the exact file list. Exclude `docs/plan/`, `logs/`, build output, IDE files, and unrelated user changes.

## Review priorities

Review in this order:

1. Data loss, permission bypass, invalid ownership, and false success reporting.
2. Compilation, binary compatibility, persistence compatibility, and behavior regressions.
3. Hot-path allocations, unbounded computation, overflow, and threading.
4. Nullable-state protocols, overly broad types, duplication, and maintainability.
5. Naming and local style.

Do not block a correct change solely for preference-level formatting or speculative future flexibility.
