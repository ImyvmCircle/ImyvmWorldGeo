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

- When a task involves a design choice, tradeoff, or direction that is not mechanically obvious, state your assessment — what you recommend, what alternatives exist, and what could go wrong — before acting. The user may be wrong; challenge assumptions when your analysis suggests a different path. Execute only after alignment, or when the task is purely mechanical.
- Before implementation, provide a complete, reviewable work route covering scope, phases, expected files, compatibility or rollback concerns, verification, and explicit exclusions.
- Until the user gives an explicit instruction to execute that route, perform read-only investigation only. Do not modify source code, tests, configuration, resources, or formal documentation. Revisions to local notes under `docs/plan/` are allowed only to support review of the proposed route.
- Read the complete call chain before editing: constructor or API boundary, application logic, resolver, persistence, and tests.
- Search every caller before changing a shared function. Fix the root cause at the narrowest shared boundary.
- Keep changes vertically complete. A domain invariant change must update its resolver, mutation path, codec, public API, compatibility layer, and tests together.
- Prefer the smallest correct change. Reuse existing code and standard library features before adding abstractions or dependencies.
- Do not add speculative interfaces, factories, repositories, DSLs, caches, or configuration.
- Preserve unrelated user changes in a dirty worktree. Never use destructive Git commands unless explicitly requested.
- Do not commit, push, rebase, or create a pull request unless the user explicitly asks. The user normally creates signed commits.

## Type system first

Make illegal states unrepresentable. When choosing between runtime validation and a type that structurally excludes the invalid case, prefer the type. This principle governs all specific rules below — when no explicit rule covers a situation, ask: "can the compiler reject the bad state?" If yes, encode it in the type; if not, validate at the nearest boundary and convert to a narrow type immediately.

Corollaries:

- A function that only makes sense for a subset of a type's values should accept the subset type, not the broad type plus a precondition comment.
- Prefer sealed hierarchies, enums, and value classes over stringly-typed or Int-coded dispatch.
- A nullable parameter is a valid design choice only when absence is a meaningful, documented domain state — not when it encodes "this parameter is irrelevant for some callers."
- **Boundary narrowing.** At every system boundary — persistence codecs, NBT/JSON deserialization, command parsing, network packets, compatibility adapters — convert the external representation (`Any`, `String`, raw `NbtCompound`, `JsonElement`, database row) into a validated domain type immediately. No downstream code should operate on the wire format; the boundary function's return type is the narrowest correct domain type. This is the type-system-first principle applied to I/O edges.

## Design drift prevention

These degradation patterns do not appear in initial design; they accumulate over successive changes. Before extending an existing file, check whether the change introduces or deepens one:

- **Growing dispatch.** A `when`/`if-else` chain that routes by type or key and grows with each feature. If a new branch is being added to an already-large dispatch, consider whether the behavior belongs on the type itself (polymorphism), in a registry looked up by key, or in a strategy extracted to its own file. A centralized exhaustive dispatch is acceptable when it represents a closed domain model.
- **Responsibility creep.** A class or function that accumulates unrelated duties because it is convenient to reach from there. If the new logic does not serve the same single purpose as the existing code, extract it — even if it means one more file.
- **Aggregate bloat.** A domain aggregate that absorbs child concerns (formatting, notification, permission checks) because it holds the data. Keep aggregates focused on invariant enforcement; push derived behavior to application-layer services or extension points.
- **Compatibility layer leakage.** A shim or adapter introduced for backwards compatibility whose types, nullability, or `Any` usage begins appearing in non-compatibility code paths. Compatibility boundaries must remain quarantined; if production code needs the same shape, define a proper domain type and convert at both edges.

When a change would deepen one of these patterns beyond a minor increment, flag it in the work route and propose a bounded refactor scope before proceeding.

## Design decision order

When multiple designs satisfy the functional requirement, prefer the first applicable option:

1. Existing type
2. Existing abstraction
3. Narrow value type
4. Sealed hierarchy
5. New abstraction
6. Compatibility wrapper
7. Runtime validation only

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

## i18n

- Player-visible text uses `Translator.tr()`. Do not use `Text.literal()` unless no translation key applies.
- Maintain all language resource files in sync.
- Messages must name the actor, Region, GeoScope, attribute, target player, and operation clearly.
- MOTD-style rich formatting (colors, bold) is encouraged; emoji and special Unicode are not.
- Enum values must be converted to human-readable translated text.
- Language file parameters must not be wrapped in single quotes.
- In `MessageFormat` texts, write a literal English single quote as two single quotes (`''`).

## Commands

- Commands are registered in `CommandRegister.register()`.
- Extract parameters in the same file, then delegate to the corresponding application-layer implementation.
- Prefer human-readable name parameters with a `SuggestionProvider` over raw IDs.
- When a Region or GeoScope name contains non-ASCII characters or spaces, wrap it in double quotes before suggesting.

## Domain invariants

- A `GeoScope` passed with a `Region` must belong to that Region. Validate ownership at public and mutation boundaries.
- Do not pass a Region and an unrelated child object or child collection as independent parameters when a typed owner/target can prevent mismatch.
- `RegionIdHandler.idMark` reserves: `1` = WorldGeo-AdventureAddon wilderness, `2` = WorldGeo-CommunityAddon community, `0` = unclassified. Callers pass the mark at `createRegion`; Adventure and Community region sets are mutually exclusive. This project does not enforce marks internally.
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
- Concrete numeric values belong in the appropriate config class under `infra/config/` (`CoreConfig`, `PermissionConfig`, `RuleConfig`, `EffectConfig`, `SelectionConfig`, `GeoConfig`, `TeleportConfig`, `EntryExitConfig`). Do not hard-code them in business logic.
- When a `Region` object's data changes, synchronize the corresponding `RegionDatabase` save logic.
- New persistent fields must provide a backward-compatible default so old data loads without error.
- New fields may only be appended at the end of the current data block; do not insert between existing fields.
- Periodic tasks use `lazyTicker`.

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

## Pre-completion self-check

Before reporting a task as complete, verify that the change does not introduce any of the following. If one is found, fix it before handoff — do not report it as a known issue.

1. **New nullable protocol in domain/application code** — a parameter or return type became nullable without a documented domain reason for absence.
2. **`Any`, unchecked cast, or raw collection escaping a boundary** — the broad type leaks past serialization/compatibility/command-parsing into resolvers, stores, or mutations.
3. **Duplicated state or derived data stored alongside its source** — two fields that must be kept in sync, when one could be computed.
4. **Interface or abstraction without a shared contract, shared behavior, or clear isolation purpose** — speculative extension point. A single-implementation interface is valid when it enforces a capability contract callers depend on, carries default/shared behavior, or provides a tested seam (e.g., persistence boundary); it is not valid when it merely labels a concrete class.
5. **Compatibility layer leakage** — a shim's types, nullability, or `Any` appearing in production code paths.
6. **Growing dispatch deepened without justification** — a new branch added to an already-large `when`/`if-else` without considering extraction.
7. **Aggregate bloat or service gaining an unrelated responsibility** — new logic that does not serve the same single purpose as the host class.
8. **Mutable collection or internal state exposed across a public boundary** — a returned list or map that callers could mutate to bypass the controlled write path.

This checklist supplements, not replaces, the verification steps in "Tests and verification." Run both.

## Documentation and repository hygiene

- Put all temporary requirements, execution plans, findings, progress logs, and agent working notes under `docs/plan/`.
- `docs/plan/` is local working state and must not be staged or committed.
- `logs/` is runtime output and must not be staged or committed.
- Formal user/addon documentation belongs elsewhere under `docs/` and may be committed.
- Write technical documentation for clarity, accuracy, and reviewability. Do not apply academic-paper style rules, AIGC detection, or prose statistics unless the user explicitly requests them.
- Keep `README.md` focused on released behavior and public usage. Use third-person perspective only (no "I", "we", "you"); name specific actors ("player", "visitor", "owner", "admin"). Do not add contexts, caveats, or disclaimers not present in the task prompt.
- Before staging, inspect the exact file list. Exclude `docs/plan/`, `logs/`, build output, IDE files, and unrelated user changes.

## Review priorities

Review in this order:

1. Data loss, permission bypass, invalid ownership, and false success reporting.
2. Compilation, binary compatibility, persistence compatibility, and behavior regressions.
3. Hot-path allocations, unbounded computation, overflow, and threading.
4. Nullable-state protocols, overly broad types, duplication, and maintainability.
5. Naming and local style.

Do not block a correct change solely for preference-level formatting or speculative future flexibility.
