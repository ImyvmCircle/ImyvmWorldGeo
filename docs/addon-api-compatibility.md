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

`R9 (unreleased)` must be replaced by the actual release version when published.

| Compatibility surface | Deprecated since | Replacement | State | Earliest removal | Compatibility retained |
|---|---|---|---|---|---|
| `RegionDataApi.getPermissionValueRegion` | R9 (unreleased) | Explicit default/Region/Scope and global/player permission methods | Deprecated | Two released versions, then maintainer review | JVM method delegates |
| `RegionDataApi.getExtensionPermissionValueRegion` | R9 (unreleased) | Explicit extension permission methods | Deprecated | Two released versions, then maintainer review | JVM method delegates |
| `RegionDataApi.getRuleValueForRegion` | R9 (unreleased) | `getDefaultRuleValue`, `getRegionRuleValue`, or `getScopeRuleValue` | Deprecated | Two released versions, then maintainer review | JVM method delegates |
| `RegionDataApi.getEffectValueForRegion` | R9 (unreleased) | `getRegionEffectValue` or `getScopeEffectValue` | Deprecated | Two released versions, then maintainer review | JVM method delegates |
| `RegionDataApi.getActiveEffectsForRegion` | R9 (unreleased) | `getRegionActiveEffects` or `getScopeActiveEffects` | Deprecated | Two released versions, then maintainer review | JVM method delegates |
| `PlayerInteractionApi.getPermissionValueRegion` | R9 (unreleased) | Explicit default/Region/Scope and global/player methods | Deprecated | Two released versions, then maintainer review | JVM method delegates |
| `PlayerInteractionApi.toggleTeleportPointAccessibility(GeoScope)` | R11 (unreleased) | `toggleTeleportPointAccessibility(ServerPlayer, Region, GeoScope)` | Deprecated | Two released versions, then maintainer review | JVM method delegates after resolving the canonical database owner; detached/orphan/unassigned scopes are rejected |
| `Region.settings` / `GeoScope.settings` | Not scheduled | `RegionDataApi` for reads; `PlayerInteractionApi` setting operations for writes | Compatibility surface | No removal scheduled | Constructor, getter, and setter descriptors remain; getter returns a detached snapshot |
| `Region.geometryScope` / `Region.ownershipHistoryByScope` | R10 (unreleased) | `RegionDataApi` for reads; supported interaction APIs for writes | Compatibility surface | No removal scheduled | Constructor, getter, and setter descriptors remain; getters return detached snapshots |
| `GeoShape.geoShapeType` / `GeoShape.shapeParameter` | R11 (unreleased) | Use the named `GeoShape` factories and `PlayerInteractionApi.replaceScopeShape` | Compatibility surface | No removal scheduled | Raw constructor, getter, and setter descriptors remain; the getter returns a detached snapshot and state-changing setter calls fail fast |
| Mutable `GeoScope` state properties | R11 (unreleased) | `RegionDataApi` for reads; supported Region and interaction operations for writes | Compatibility surface | No removal scheduled | Constructor and property accessor descriptors remain; uncontrolled state changes through legacy setters are rejected |
| `ScopeId` compatibility encoding | R10 (unreleased) | `RegionDataApi.parseScopeId` and ScopeId query methods | Compatible encoding fix | No removal scheduled | Existing raw IDs remain parseable; newly migrated legacy scopes use a marker bit and full local index without changing the persisted `Long` field |
| `ScopeId` query and overlay methods | R11 (unreleased) | `AssignedScopeId` and the corresponding `RegionDataApi` methods | Deprecated | Two released versions, then maintainer review | Existing `ScopeId` methods remain and validate/delegate; `GeoScope` constructor and scopeId getter/setter descriptors remain |
| `Setting` / `BaseKey` | Not scheduled | Typed permission/rule/effect keys through supported APIs | Compatibility surface | No removal scheduled | Existing classes and JVM methods remain; unknown setting subclasses are rejected by persistence |

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
