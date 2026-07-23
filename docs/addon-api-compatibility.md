# Addon API compatibility

This document defines compatibility expectations for addon authors and records APIs that are being retired.

## API tiers

1. `com.imyvm.iwg.inter.api` is the supported addon API. Breaking changes require a migration path.
2. Public domain types such as `Region`, `GeoScope`, and `Setting` are compatibility surfaces because existing addons may already reference their JVM signatures. Their unsafe mutable behavior is not guaranteed.
3. `application`, `infra`, `mixin`, and other implementation packages are not supported addon APIs, even when Kotlin/JVM visibility makes a declaration public.

## Deprecation lifecycle

Deprecation does not schedule automatic deletion.

1. **Deprecated:** keep the old JVM signature, delegate to the replacement, and document the migration.
2. **Removal candidate:** after at least two released versions, maintainers may evaluate removal. The API may remain indefinitely.
3. **Removed:** delete only with explicit maintainer approval in a breaking release, and list the change in the upgrade notes.

Before removing an API, verify that its replacement covers every old use case, inspect compiled JVM descriptors, and search known addons. `DeprecationLevel.ERROR` or `HIDDEN` must not be used as an automatic timer.

## Current compatibility ledger

| Compatibility surface | Deprecated since | Replacement | State | Earliest removal | Compatibility retained |
|---|---|---|---|---|---|
| `RegionDataApi.getPermissionValueRegion` | 26.2-1.5.2 | Explicit default/Region/Scope and global/player permission methods | Deprecated | Two released versions, then maintainer review | JVM method delegates |
| `RegionDataApi.getExtensionPermissionValueRegion` | 26.2-1.5.2 | Explicit extension permission methods | Deprecated | Two released versions, then maintainer review | JVM method delegates |
| `RegionDataApi.getRuleValueForRegion` | 26.2-1.5.2 | `getDefaultRuleValue`, `getRegionRuleValue`, or `getScopeRuleValue` | Deprecated | Two released versions, then maintainer review | JVM method delegates |
| `RegionDataApi.getEffectValueForRegion` | 26.2-1.5.2 | `getRegionEffectValue` or `getScopeEffectValue` | Deprecated | Two released versions, then maintainer review | JVM method delegates |
| `RegionDataApi.getActiveEffectsForRegion` | 26.2-1.5.2 | `getRegionActiveEffects` or `getScopeActiveEffects` | Deprecated | Two released versions, then maintainer review | JVM method delegates |
| `PlayerInteractionApi.getPermissionValueRegion` | 26.2-1.5.2 | Explicit default/Region/Scope and global/player methods | Deprecated | Two released versions, then maintainer review | JVM method delegates |
| `PlayerInteractionApi.toggleTeleportPointAccessibility(GeoScope)` | 26.2-1.5.2 | `toggleTeleportPointAccessibility(ServerPlayer, Region, GeoScope)` | Deprecated | Two released versions, then maintainer review | JVM method delegates after resolving the canonical database owner; detached/orphan/unassigned scopes are rejected |
| `Region.settings` / `GeoScope.settings` | Not scheduled | `RegionDataApi` for reads; `PlayerInteractionApi` setting operations for writes | Compatibility surface | No removal scheduled | Constructor, getter, and setter descriptors remain; getter returns a detached snapshot |
| `Region.geometryScope` / `Region.ownershipHistoryByScope` | 26.2-1.5.2 | `RegionDataApi` for reads; supported interaction APIs for writes | Compatibility surface | No removal scheduled | Constructor, getter, and setter descriptors remain; getters return detached snapshots |
| `GeoShape.geoShapeType` / `GeoShape.shapeParameter` | 26.2-1.5.2 | Use the named `GeoShape` factories and `PlayerInteractionApi.replaceScopeShape` | Compatibility surface | No removal scheduled | Raw constructor, getter, and setter descriptors remain; the getter returns a detached snapshot and state-changing setter calls fail fast |
| Mutable `GeoScope` state properties | 26.2-1.5.2 | `RegionDataApi` for reads; supported Region and interaction operations for writes | Compatibility surface | No removal scheduled | Constructor and property accessor descriptors remain; uncontrolled state changes through legacy setters are rejected |
| Mutable `Region` state properties and child mutation methods | Unreleased | `RegionDataApi` for reads; `PlayerInteractionApi` for supported Region/Scope/SubSpace writes | Compatibility surface | No removal scheduled | JVM setters and child mutation methods remain linkable, but direct live-object writes now fail fast; Dynmap visibility and ownership history stay server-owned internals |
| `ScopeId` compatibility encoding | 26.2-1.5.2 | `RegionDataApi.parseScopeId` and ScopeId query methods | Compatible encoding fix | No removal scheduled | Existing raw IDs remain parseable; newly migrated legacy scopes use a marker bit and full local index without changing the persisted `Long` field |
| `ScopeId` query and overlay methods | 26.2-1.5.2 | `AssignedScopeId` and the corresponding `RegionDataApi` methods | Deprecated | Two released versions, then maintainer review | Existing `ScopeId` methods remain and validate/delegate; `GeoScope` constructor and scopeId getter/setter descriptors remain |
| `Setting` / `BaseKey` | Not scheduled | Typed permission/rule/effect keys through supported APIs | Compatibility surface | No removal scheduled | Existing classes and JVM methods remain; unknown setting subclasses are rejected by persistence |
| `WorldGeoBehaviorEvent.eventId` / `quantity` / `source` | — (new in 26.2-1.5.5) | — | New fields with defaults | — | Default values preserve all existing callers; `eventId` auto-generated, `quantity` defaults to 1, `source` defaults to `"BEHAVIOR"` |
| `NaturalPeriodKind.YEAR` | — (new in 26.2-1.5.5) | — | New enum value | — | `YEAR` appended to enum; old period files missing YEAR use current year as default |
| `RegionDataApi.getRealTimeSnapshot(zoneId)` | — (new in 26.2-1.5.5) | — | New API | — | Returns `RealTimeSnapshot` for any valid `ZoneId` string |
| `RegionDataApi.listPlayersInRegion/Scope/SubSpace` | — (new in 26.2-1.5.5) | — | New API | — | Uses `PlayerRegionChecker` cache with online-player instant-resolution fallback |
| `PlayerInteractionApi.setLocationActionBarVisible` / `isLocationActionBarVisible` / `toggleLocationActionBar` | — (new in 26.2-1.5.5) | — | New API | — | `toggleLocationActionBar` delegates to existing `toggleActionBar`; `set`/`is` operate directly |
| `RegionDataApi.sendRegionSpaceMessage` / `sendScopeSpaceMessage` / `sendSubSpaceMessage` `(messageKey, args)` overloads | — (new in 26.2-1.5.5) | — | New API | — | messageKey resolved via `Translator.tr`; `null` result logs an error and returns 0 |
| `PlayerInteractionApi.openSpaceDebugView` | — (new in 26.2-1.5.5) | — | New API | — | Accepts Region/Scope/SubSpace; OP check via `PlayerList.isOp` |
| `RegionDataApi.getSubSpaceGlobalSettings/getSubSpacePersonalSettings` and `ByType` overloads | — (new in 26.2-1.5.5) | — | New API | — | Completes raw SubSpace setting-list reads to match Region and Scope |
| `PlayerInteractionApi.startSelectionForModifySubSpace` | — (new in 26.2-1.5.5) | — | New API | — | Starts a SubSpace shape selection for an existing canonical SubSpace; use `replaceSubSpaceShape` to commit a direct `GeoShape` replacement |
| `PlayerInteractionApi.createRegion/createAndGetRegion/addScope/createAndGetRegionScopePair` with `GeoShape` | — (new in 26.2-1.5.5) | — | New API | — | Direct shape creation bypasses selection session; existing selection-based methods unchanged |

Deprecated helpers under implementation packages are retained only to avoid immediate linkage failures. Addons should migrate to `com.imyvm.iwg.inter.api`; those helpers are not promoted to supported API by this ledger.

## R9 setting migration

Do not mutate `region.settings` or `scope.settings` directly:

```kotlin
// Legacy: modifies only a detached compatibility snapshot.
region.settings.add(PermissionSetting(PermissionKey.PVP, false))

// Supported mutation boundary.
PlayerInteractionApi.addSettingRegion(player, region, "PVP", "false", null)
```

Use the explicit query matching the intended target instead of nullable parameter combinations:

```kotlin
val global = RegionDataApi.getRegionGlobalPermissionValue(region, PermissionKey.PVP)
val playerValue = RegionDataApi.getScopePlayerPermissionValue(region, scope, playerUuid, PermissionKey.PVP)
```

Extension permission and rule keys must be registered through `RegionDataApi` before commands or queries use them.

## R10 Region collection migration

Do not mutate `Region.geometryScope` or `Region.ownershipHistoryByScope` directly. Their getters now return detached compatibility snapshots, so collection mutations do not change the Region. Read scopes through `RegionDataApi.getRegionScopes(region)` and perform supported mutations through `PlayerInteractionApi`.

## R15 Region live mutation migration

`Region` keeps its JVM-linkable setters and child mutation methods for compatibility, but direct writes through a live Region are no longer a supported addon path. `region.name = ...`, `region.showOnDynmap = ...`, `region.addScope(...)`, `region.removeScope(...)`, `region.restoreScope(...)`, `region.addSubSpace(...)`, `region.removeSubSpace(...)`, `region.restoreSubSpace(...)`, `region.renameScope(...)`, `region.renameSubSpace(...)`, `region.replaceScopeGeometry(...)`, `region.replaceSubSpaceGeometry(...)`, and `region.recordScopeOwnership(...)` now fail fast instead of mutating the live Region.

Resolve the current live objects through `RegionDataApi`, then cross the supported write boundary through `PlayerInteractionApi`:

```kotlin
val liveRegion = RegionDataApi.getRegion(staleRegion.numberID) ?: return
val liveScope = RegionDataApi.getScopeByAssignedId(scopeId)
    ?.takeIf { (owner, _) -> owner === liveRegion }
    ?.second
    ?: return
val liveSubSpace = RegionDataApi.getSubSpaceById(subSpaceId)
    ?.takeIf { (owner, scope, _) -> owner === liveRegion && scope === liveScope }
    ?.third
    ?: return

PlayerInteractionApi.renameRegion(player, liveRegion, "NewName")
PlayerInteractionApi.addScope(player, liveRegion, "Annex")
PlayerInteractionApi.replaceScopeShape(player, liveRegion, liveScope, rectangle)
PlayerInteractionApi.renameSubSpace(player, liveRegion, liveScope, liveSubSpace, "Plot-A")
```

Dynmap visibility and ownership history stay server-owned internals. Addon code should treat those fields as read-only facts.

## R11 Scope identity migration

Use `AssignedScopeId` whenever an operation requires a persisted Scope. `ScopeId(0)` remains only as the legacy representation of a not-yet-added Scope; positive raw values are invalid.

```kotlin
val scopeId = RegionDataApi.getAssignedScopeIdOrNull(scope) ?: return
val resolved = RegionDataApi.getScopeByAssignedId(scopeId)
val parsed = RegionDataApi.parseAssignedScopeId(scopeId.toIdString())
```

Supported `PlayerInteractionApi` mutations require the exact live `Region` and `GeoScope` objects
currently owned by the database. Equal IDs, names, or copied fields do not make a detached or stale
object a valid mutation target. Resolve retained references again after a database reload:

```kotlin
val liveRegion = RegionDataApi.getRegion(staleRegion.numberID) ?: return
val liveScope = RegionDataApi.getScopeByAssignedId(scopeId)
    ?.takeIf { (owner, _) -> owner === liveRegion }
    ?.second
    ?: return

PlayerInteractionApi.resetTeleportPoint(player, liveRegion, liveScope)
```

Detached targets now fail before mutation or persistence. Existing method descriptors remain unchanged;
unsafe mutation through copied or stale domain objects is not retained as compatibility behavior.

Construct timed overlays through `RegionDataApi.createTimedEffectOverlay`. Raw `scopeIdRaw` constructors remain binary compatible, but invalid assigned IDs, blank overlay/source IDs, empty or duplicate effect lists, amplifiers outside `0..255`, and non-positive durations (`startMillis >= endMillis`) are rejected. The API and overlay service defensively snapshot effect lists, so later mutation of an addon-owned list cannot change an active overlay.

Overlay APIs remain safe for synchronous calls from arbitrary addon threads. Applying an overlay is serialized with Region and Scope deletion: a successful deletion clears the Scope's transient overlays before another apply can complete, while a failed save restores the Scope without clearing its overlays. Queries and clearing an individual overlay remain thread-safe and do not wait for Region persistence.

## B3 domain input invariants

Existing JVM constructors and API method descriptors remain available, but invalid inputs now fail
fast instead of being normalized or persisted:

- Region and Scope names must start with a supported letter. Later characters may be supported
  letters or ASCII digits; space, `_`, `-`, and `'` are allowed only as isolated separators and
  cannot end the name.
- A Region must contain at least one assigned Scope when constructed or persisted.
- Region `idMark` values must be from `0` to `9`; creation and filtered queries reject other values.
- `EffectSetting` amplifiers must be from `0` to `255`.
- Replacing a legacy settings snapshot rejects duplicate type/key/subject identities before changing
  the live store.
- Ownership history records require positive, distinct source/target Region IDs, non-negative ordered
  timestamps, an unbroken transfer chain, and a final owner matching the Region that stores the chain.

Malformed database records and overflowing player-stat ledgers are rejected without rewriting their
source files. Unsafe addon inputs that previously relied on clamping, defaulting to mark `0`, duplicate
last-write-wins behavior, or empty Regions are not retained as behavioral compatibility.

## R11 geometry mutation migration

`GeoShape` is immutable. `shapeParameter` is a detached legacy ABI and persistence snapshot, not a
mutation protocol. Mutating the returned list has no effect, and assigning different parameters or a
different type through the retained setters fails fast. The raw `(GeoShapeType, MutableList<Int>)`
constructor remains linkable for old addons and the database codec, but new code should use the named
factories:

```kotlin
val circle = GeoShape.circle(GeoPoint(100, 200), radius = 48)
val rectangle = GeoShape.rectangle(GeoPoint(0, 0), GeoPoint(100, 100))
val polygon = GeoShape.polygon(listOf(
    GeoPoint(0, 0),
    GeoPoint(100, 0),
    GeoPoint(50, 100)
))
```

To replace live Scope geometry, pass a complete same-type shape through the owner-explicit API:

```kotlin
PlayerInteractionApi.replaceScopeShape(player, region, scope, polygon)
```

The Region and Scope must be the exact canonical database objects. The operation checks the configured
size policy and intersections before mutation, excludes the target Scope from its own intersection
check, and restores the previous shape if persistence fails. Circle, Rectangle, and Polygon type
conversion is not supported by this API.

Polygon geometry is limited to 256 vertices. The named factory and raw compatibility constructor
reject larger polygons before storing them, and persisted polygons above this safety limit fail fast
during loading. The database tag and parameter-count encoding are unchanged; addons that generate
larger polygons must simplify them before construction.

`GeoShape.generateTeleportPoint(Level)` remains JVM-linkable but no longer scans every block in a shape. It checks one deterministic representative surface position and returns `null` when that position is unsafe. Normal Region and Scope creation instead validates the player's position and uses the configured bounded fallback search; addons that require a specific location should set and validate that location explicitly.

`GeoScope` identity, owner-dependent name, dimension, geometry, teleport point, accessibility, and Dynmap visibility are no longer independent mutation channels. Their legacy JVM setters remain linkable, but reject changes made outside the owning Region or application operation so ownership checks, rollback, and persistence cannot be bypassed.

Use the owner-explicit teleport accessibility operation for new addon code:

```kotlin
PlayerInteractionApi.toggleTeleportPointAccessibility(player, region, scope)
```

The deprecated Scope-only overload remains linkable, but succeeds only for the exact live Scope object resolved from `RegionDatabase`; copying an assigned ID into another object does not grant a mutation path.

`PlayerInteractionApi.teleportPlayerToScope(player, region, scope)` is the ordinary player-facing entry and now requires the teleport point to be public. Use the explicit administrator entry only when an addon has already authorized the operation:

```kotlin
PlayerInteractionApi.teleportPlayerToScopeAsAdministrator(player, region, scope)
```

The administrator entry bypasses only accessibility. Both entries still require the exact live Region/Scope pair and enforce dimension availability, physical safety, bounded fallback, persistence, and rollback. The retained ordinary method descriptor is unchanged; unrestricted use of private teleport points is not retained as compatibility behavior.

## R14 immutable space snapshot API

Use `RegionDataApi.getSpaceSnapshot(type, id)` for unified Region, Scope, and SubSpace lookup by stable ID. Use `listScopeSnapshots(regionId)`, `listSubSpaceSnapshots(scopeId)`, and `getSubSpaceSnapshotByName(scopeId, name)` when addon code needs immutable menu or rule inputs without retaining live domain objects.

`WorldGeoSpaceSnapshot` now includes `displayName`, `shapeType`, and copied `shapeParameters`. Region snapshots have no shape. Scope and SubSpace snapshots expose the current geometry as immutable values. Existing live-object methods such as `getRegionScopes`, `getRegionSubSpaces`, and `getSubSpaceById` remain available for compatibility and owner-explicit mutation flows, but new read-only addon code should prefer snapshots.

## R13 geographic profile API

Use `RegionDataApi.getRegionGeographicProfile`, `getScopeGeographicProfile`, and
`getSubSpaceGeographicProfile` when an addon needs geography for menus, lore,
settlement inputs, or OP diagnostics. These methods return `WorldGeoGeographicProfileResult`
so addons can handle unloaded dimensions and oversized scan ranges without parsing chat text.

Use the snapshot methods when cache state matters:

```kotlin
RegionDataApi.getRegionGeographicProfileSnapshot(server, region)
RegionDataApi.getScopeGeographicProfileSnapshot(server, region, scope)
RegionDataApi.getSubSpaceGeographicProfileSnapshot(server, region, scope, subSpace)
```

`WorldGeoGeographicProfileSnapshot` reports whether the value was computed or read from cache,
the calculation time, and the last cache invalidation reason. `RegionDataApi.getGeographicProfileCacheStatus()`
reports global cache size and invalidation metadata. `RegionDataApi.refreshGeographicProfiles(server)`
clears and warms the cache for current Region, Scope, and SubSpace objects. Region-data saves and
server-session changes invalidate the cache; weekly natural-period transitions warm it again.

`WorldGeoSpaceSnapshot.dominantBiomeId` remains available for compatibility. New code should
prefer the profile result because it exposes the neutral geographic attribute, overworld biome
category ratios, average elevation, orientation, sampling counters, and raw biome weights.
Community and Adventure should interpret these facts in their own modules instead of reading
WorldGeo implementation packages.

## R12 SubSpace API contract

`SubSpace` is a child space owned by a persisted `GeoScope`. Addons read SubSpace data through `RegionDataApi.getRegionSubSpaces`, `getSubSpaceById`, `getSubSpaceByName`, `getRegionScopeSubSpaceByLocation`, and `resolveSubSpaceAtEntity`. Existing Region and Scope location queries keep their descriptors and continue to return Region and Scope pairs.

`RegionDataApi.registerSubSpaceTransitionCallback(Consumer<WorldGeoSubSpaceTransition>)` subscribes addon code
to SubSpace enter and exit transitions. The callback receives immutable `WorldGeoSpaceSnapshot` endpoints plus
the player UUID, player name, and server-time stamp. Delivery uses the same bounded asynchronous queue as the
other addon callbacks; queue overflow drops the newest payload with a server warning.

Supported mutations run through `PlayerInteractionApi.createSubSpace`, `createAndGetSubSpace`, `deleteSubSpace`, `renameSubSpace`, `replaceSubSpaceShape`, SubSpace tag methods, and SubSpace setting methods. The Region, parent Scope, and SubSpace arguments have to be exact live database objects. Detached copies, mismatched parent scopes, duplicate names, duplicate IDs, cross-dimension shapes, and shapes outside the parent Scope fail before persistence.

SubSpace settings resolve before Scope settings and Region settings. Personal settings in the same container resolve before global settings. Built-in permission parent inheritance keeps its existing behavior. Extension permission and rule keys remain exact-match keys.

Persisted Region and Scope data written before the SubSpace format loads with an empty SubSpace list. New database records append SubSpace data after ownership history, preserving the Region, Scope, settings, and ownership block order.

OP commands mirror the addon surface for live-server debugging. Use `subspace create`, `delete`, `rename`, `modify select`, `modify`, `setEntryMessage`, `query`, `tag`, `subspace setting`, and `debug spaceHere` to inspect and mutate SubSpace state in-game.

## Selection API contract

Call `PlayerInteractionApi` selection operations on the Minecraft server thread. A normal selection
may create a Region or Scope, while a modification selection may modify only the exact live Scope it
was started for. These modes are not interchangeable.

`startSelectionForModify` accepts only an assigned canonical Scope currently owned by
`RegionDatabase`, with a supported non-`UNKNOWN` shape, in the executing player's current dimension.
Detached copies, orphaned or unassigned Scopes, cross-dimension targets, and attempts to apply a
selection to another Scope fail without mutation. Disconnecting, changing dimension, or stopping the
server clears transient selection state; deleting a Region or Scope clears selections that reference
it only after the deletion is successfully persisted.

## V2-A time snapshot API

`RegionDataApi.getTimeSnapshot(ServerLevel)` returns neutral WorldGeo time facts for addon code. The
snapshot contains dimension ID, server game tick, derived game day/day tick, weather flags, moon phase,
UNIX time, and default east-eight natural hour/day/week/month/year identifiers.

Use this API as the stable read boundary for Community and Adventure scheduling facts. The in-game OP
entrypoint `/imyvmWorldGeo debug time` prints the same facts for live-server debugging.

`RegionDataApi.getCurrentNaturalPeriodIds()` exposes the current east-eight natural hour/day/week/month
identifiers. `RegionDataApi.registerNaturalPeriodTransitionCallback(Consumer<NaturalPeriodTransition>)`
registers a callback for hour/day/week/month changes detected by the WorldGeo lazy ticker. WorldGeo records
last processed period IDs in `iwg_periods.json`; after restart, the first lazy tick compares persisted IDs with
current IDs and emits each missed boundary in order. Addons can use the previous/current period pair as their
idempotency key for backfill settlement.

Natural-period callbacks now receive immutable payloads through a bounded asynchronous queue. WorldGeo no
longer runs addon callbacks on the server tick for this API. When the queue is full, WorldGeo drops the
newest callback payload and writes a server warning instead of blocking tick progression.

`RegionDataApi.registerBehaviorEventCallback(Consumer<WorldGeoBehaviorEvent>)` subscribes addons to neutral
WorldGeo behavior facts. `RegionDataApi.getRecentBehaviorEvents()` and `RegionDataApi.getRecentBehaviorEvents(Int)` return the latest in-memory debug window.
Producer coverage includes block place, block break, player death, entity damage, entity kill, container
interaction, item use, Region/Scope/SubSpace enter and exit, and the server-operator debug command
`/imyvmWorldGeo debug behavior emit`.

Behavior-event callbacks follow the same bounded asynchronous delivery model. Published
`WorldGeoBehaviorEvent` objects remain immutable facts; queue overflow drops the newest callback payload with
a server warning, while the persisted stats store and recent-event debug window continue to update on the
server thread.

`RegionDataApi.queryBehaviorStats(WorldGeoBehaviorStatsQuery)` and the explicit-parameter overload return
persisted neutral behavior counts from `iwg_behavior_stats.json`. WorldGeo aggregates behavior facts by natural
hour/day/week/month/year, behavior type, Region, Scope, SubSpace, player UUID, and object ID. Version 26.2-1.5.5 adds
`queryBlockDelta`, `queryResidence`, `queryEntityCombat`, and `queryOnlineTime` as typed read models over the same
store. The typed results expose placed and broken block totals, net player contribution, chunk residence millis,
combat counts, and online or AFK millis without adding Community or Adventure semantics to WorldGeo. The
server-operator command `/imyvmWorldGeo debug behavior stats` shows current-hour behavior totals at the executing
player's current space for in-game validation. The stats store uses the server data session, strict malformed-input
rejection, and atomic writes matching the Region database persistence model.


## V3 space support API

`RegionDataApi.getRegionSpaceSnapshot`, `getScopeSpaceSnapshot`, and `getSubSpaceSnapshot` return immutable neutral snapshots for Region, GeoScope, and SubSpace. Snapshots include identity, name, dimension when the space has one, area, parent links, child counts, SubSpace tags, entry-message state, a dominant-biome hint when present, map color suggestion, inline public setting summaries, and a WorldGeo stats-version marker. They do not expose mutable live domain objects.

`RegionDataApi.listRegionSettingSummaries`, `listScopeSettingSummaries`, and `listSubSpaceSettingSummaries` use `WorldGeoSettingVisibility`. WorldGeo defines only `PUBLIC` and `OP_DEBUG`; Community, Adventure, or other addons must map their own manager roles before choosing one of those visibility levels. `PUBLIC` excludes personal settings. `OP_DEBUG` returns complete per-space setting summaries for server-operator diagnostics.

`RegionDataApi.sendRegionSpaceMessage`, `sendScopeSpaceMessage`, and `sendSubSpaceMessage` broadcast a caller-provided Minecraft `Component` to online players whose current resolved WorldGeo location is inside the target Region, GeoScope, or SubSpace. WorldGeo treats the message as neutral transport and does not interpret upper-layer semantics.

In-game validation commands are OP-only under `/imyvmWorldGeo debug`: `spaceSnapshot`, `settingSummaries`, and `sendSpaceMessage` each provide Region, Scope, and SubSpace targets. These commands are the server-side test points for the V3 mechanisms.
