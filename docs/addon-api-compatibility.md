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
| `Region.settings` / `GeoScope.settings` | Not scheduled | `RegionDataApi` for reads; `PlayerInteractionApi` setting operations for writes | Compatibility surface | No removal scheduled | Constructor, getter, and setter descriptors remain; getter returns a detached snapshot |
| `Region.geometryScope` / `Region.ownershipHistoryByScope` | R10 (unreleased) | `RegionDataApi` for reads; supported interaction APIs for writes | Compatibility surface | No removal scheduled | Constructor, getter, and setter descriptors remain; getters return detached snapshots |
| `ScopeId` compatibility encoding | R10 (unreleased) | `RegionDataApi.parseScopeId` and ScopeId query methods | Compatible encoding fix | No removal scheduled | Existing raw IDs remain parseable; newly migrated legacy scopes use a marker bit and full local index without changing the persisted `Long` field |
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
