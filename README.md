# IMYVMWorldGeo 26.1 1.5.1

## Changelog

### 1.5.x

This major version (1.5.x) extends RPG setting items and infrastructure APIs.

#### 1.5.1

- feat: add `/imyvmWorldGeo stats` and RegionDataApi natural-stat queries for structure counts, average local difficulty, surface block counts, and biome distribution inside loaded chunks of a region.
- feat: add persistent region player statistics for entry count, stay duration, death count, block place count, and block break count, queryable through `/imyvmWorldGeo stats <regionIdentifier> players` and RegionDataApi.
- feat: add stable `AssignedScopeId` identity carrying scope creation time and the founding Region number ID; Kotlin addons use typed IDs, while Java addons use the validated `...Raw` API.
- feat: add scope ownership history recording each cross-region scope transfer; queryable through `RegionDataApi.getAssignedScopeOwnershipHistory(scopeId)`.
- feat: add `ScopeTransitionEvent` and `RegionTransitionEvent` for downstream listeners to react to player crossings without duplicating the entry/exit tracker.
- feat: add timed effect overlays through `RegionDataApi.applyTimedEffectOverlay / clearTimedEffectOverlay / queryOverlay`, merged at priority `scope personal > region personal > timed overlay > scope global > region global`.
- feat: add `RegionDataApi.resolveScopeAtEntity`, `getEffectiveEffectsForScope`, and `getEffectiveRulesForScope` for scope-level effect/rule queries that do not require a player UUID.
- feat: reserve `idMark = 1` for ImyvmWorldGeo-Adventure regions and `idMark = 2` for ImyvmCommunity regions, with addon-side filtering through `RegionDataApi.getRegionListFiltered(idMark)`.


#### 1.5.0

- feat: add several RPG permissions for common survival interactions, covering item pickup, bow/crossbow use, vehicle mounting, eating/drinking, and fishing.
- feat: add RPG rules `RPG_NATURAL_REGEN`, `RPG_FIRE_SPREAD`, and `RPG_HUNGER` for region-based survival pacing control.
- feat: add namespaced boolean extension permission/rule registration so addon-defined setting keys can be managed through the same setting commands and RegionDataApi without hard-coding them into core enums.
- lang: use immersive denial messages and restricted-area entry notifications for RPG-restricted areas.


## Introduction

This is a mod to provide a geography system framework for Imyvm server players and groups, 
and is also a basement for extensions, exemplified by jungle system, community system and so on.

## Environment Requirements

This mod is **server-side only** and requires the following environment:

- **Minecraft Version:** 26.1  
- **Fabric Loader Version:** 0.18.5 (or compatible with Minecraft 26.1)
- **Java Version:** 25  
- **IMYVM Hoki Mod Version:** 1.1.4

**Optional:**

- **Dynmap Version:** a Fabric build compatible with the installed Minecraft version

> Note: Dynmap is optional. Install it only when a build compatible with the current Minecraft version is available; otherwise leave it absent.

> Note: Client-side players do not need to install this mod, but the server must meet these requirements.

Integer configuration values are validated when loaded and updated. Lazy ticker, effect duration,
selection point limits, and selection display steps must be positive. Teleport search radius,
entry/exit delay, fly countdown, and fall-immunity duration may be zero but not negative.
`selection.min_points` cannot exceed `selection.max_points`, and effect duration must exceed the lazy ticker interval.

---

## Features

### Region

A region is an abstract geographical area defined in the game world.
A region is identified by a unique name and a unique numeric ID, and contains one or more scopes,
while the combination of all scope shapes defines the overall area of the region.

Regions provide game with two main functions.
First, they allow defining and managing specific areas in the game world,
and mark them with meaningful names and boundaries.
Second, they serve as a foundation for building more complex systems and features,
providing them with regional features via settings, and enabling teleportation to regions.

### Scope

A scope is a sub-area within a region, defined by a name unique to other scopes in the same region and a shape.
A shape can be of `RECTANGLE`, `CIRCLE` or `POLYGON` type. Addons construct immutable shapes through
`GeoShape.rectangle`, `GeoShape.circle`, or `GeoShape.polygon`. Polygon shapes support at most 256 vertices.

### Setting

Settings are key-value pairs associated with regions or scopes,
which can be either global (applicable to all players) or personal (specific to individual players).
For the same permission key, resolution order is scope personal, scope global, region personal,
region global, then the configured default.
For permission settings, a parent-child hierarchy applies: if the specific (child) key has an explicit
setting that covers the current scope and subject, it takes full precedence over any ancestor keys.
Only when no explicit setting exists for the child key will the system fall back to the immediate parent,
and then further ancestors in order, until a match is found.

#### Permission Keys

Each built-in `PermissionKey` exposes its `category`, localized `displayTranslationKey`, and
`entryNotification` policy. Addons can derive RPG-specific behavior from this metadata instead of
maintaining a second hard-coded key list. Extension Permission keys are exact-match keys and do not
automatically join the built-in notification catalog.

| Key | Parent | Default | Description |
| --- | --- | --- | --- |
| BUILD_BREAK | - | true | Master switch for block placement and breaking. |
| BUILD | BUILD_BREAK | true | Block placement permission. Also covers non-bucket block placement. |
| BREAK | BUILD_BREAK | true | Block breaking permission. |
| BUCKET_BUILD | BUILD | true | Non-empty bucket fluid placement permission (placing water, lava, etc.). |
| BUCKET_SCOOP | BREAK | true | Empty bucket fluid or creature collection permission. |
| INTERACTION | - | true | State-changing block interaction permission. Covers doors, trapdoors, fence gates, lecterns, chiseled bookshelves, cakes, candle cakes, candles, campfires, decorated pots, flower pots, composters, dragon eggs, respawn anchors, jukeboxes, cauldrons, beehives, vaults, log stripping (axe), copper waxing/dewaxing/deoxidation (honeycomb/axe), grass path creation (shovel), and water bottle to mud conversion. |
| CONTAINER | INTERACTION | true | Container interaction permission. |
| REDSTONE | INTERACTION | true | Redstone device interaction permission (buttons, levers, comparators, repeaters, note blocks, daylight sensors, bells). Excludes ancient city sculk mechanics. |
| TRADE | - | true | Trading with villagers and wandering traders permission. |
| PVP | - | true | Player vs player damage permission. Both the attacker and defender must have this permission for damage to be dealt. |
| FLY | - | false | Flight permission within the region. |
| ANIMAL_KILLING | - | true | Passive animal damage permission. Covers passive animals (excluding goats), fish (cod, salmon, tropical fish, pufferfish, tadpole), squid, glow squid, dolphin, allay, and snow golem. Does not apply to monsters, neutral mobs, or goats. |
| VILLAGER_KILLING | - | true | Villager damage permission. Applies to villager entities only. |
| THROWABLE | - | true | Master switch for throwing projectile items such as eggs, snowballs, and potions. |
| EGG_USE | THROWABLE | true | Egg throwing permission. Controls whether players can throw eggs in the region. |
| SNOWBALL_USE | THROWABLE | true | Snowball throwing permission. Controls whether players can throw snowballs in the region. |
| POTION_USE | THROWABLE | true | Potion throwing permission. Controls whether players can throw splash or lingering potions in the region. |
| WIND_CHARGE_USE | THROWABLE | true | Wind charge throwing permission. Controls whether players can throw wind charges in the region. |
| FARMING | - | true | Farming permission. Controls breaking, planting, and using bone meal on crops (wheat, carrots, potatoes, etc.) on farmland. Independent of BUILD, BREAK, and BUILD_BREAK. |
| IGNITE | - | true | Ignite permission. Controls all use of flint and steel and fire charges, including igniting blocks, TNT, and creepers. Independent of BUILD_BREAK and BUILD: disabling BUILD or BUILD_BREAK does not prevent ignition. |
| ARMOR_STAND | - | true | Armor stand permission. Controls placing armor stand items, breaking armor stand entities by attack, and interacting with their equipment slots. Independent of BUILD_BREAK. |
| ITEM_FRAME | - | true | Item frame permission. Controls placing item frame and glow item frame entities, breaking them by attack, and interacting with their held items (adding, removing, or rotating). Applies to both regular and glow item frames. Independent of BUILD_BREAK. |
| RPG_ITEM_PICKUP | - | true | (RPG) Item pickup permission. Controls whether players can pick up item entities. |
| RPG_BOW_SHOOT | - | true | (RPG) Bow and crossbow permission. Controls whether players can use bows and crossbows. |
| RPG_VEHICLE_USE | - | true | (RPG) Vehicle mounting permission. Controls whether players can mount boats, minecarts, horses, camels, pigs, and striders. |
| RPG_EATING | - | true | (RPG) Eating permission. Controls whether players can consume food and drink items. |
| RPG_FISHING | - | true | (RPG) Fishing permission. Controls whether players can cast a fishing rod. |

#### Rule Keys

Rules control server-side gameplay mechanics within a region or scope. Unlike permissions, rules are not player-specific and have no parent-child hierarchy. The effective value follows the priority: scope setting → region setting → config default.

| Key | Default | Description |
|-----|---------|-------------|
| SPAWN_MONSTERS | true | Controls whether hostile monsters (SpawnGroup MONSTER) spawn in the region. |
| SPAWN_PHANTOMS | true | Controls whether phantoms spawn in the region (overrides SPAWN_MONSTERS for phantoms). |
| TNT_BLOCK_PROTECTION | false | When set to true, explosions from TNT, TNT minecarts, end crystals, beds used in the End, respawn anchors used outside the Nether, wither spawning, and wither skulls do not destroy blocks inside the region. Blocks outside protected regions are still destroyed normally. Entity damage and knockback from the explosion are unaffected. |
| ENDERMAN_BLOCK_PICKUP | true | Controls whether endermen can pick up blocks inside the region. The rule is checked against the actual selected block immediately before removal. |
| SCULK_SPREAD | true | Controls whether sculk growth, substrate conversion, and sculk-vein placement may write to blocks inside the region. World-generation spreading is unchanged. |
| SNOW_GOLEM_TRAIL | true | Controls whether snow golems leave snow trails inside the region. When set to false, the setBlockState call for snow placement is suppressed. |
| DISPENSER | true | Controls whether dispensers can fire into the region. When set to false, any dispenser whose output face points into the region is blocked, including dispensers placed outside the region. |
| PRESSURE_PLATE | true | Controls whether pressure plates inside the region can be activated. When set to false, entity collision with pressure plates in the region is suppressed. |
| PISTON | true | Controls whether pistons can move blocks from or into the region or destroy blocks inside it. A denied source, destination, or destroy target cancels the move, including for pistons outside the region. |
| RPG_NATURAL_REGEN | true | (RPG) Controls whether players naturally regenerate health inside the region. When set to false, the saturation-based heal from food is suppressed. |
| RPG_FIRE_SPREAD | true | (RPG) Controls whether fire may burn or place fire at target blocks inside the region. Source fire still ages and extinguishes normally. |
| RPG_HUNGER | true | (RPG) Controls whether hunger and exhaustion drain for players inside the region. When set to false, all food exhaustion calls are suppressed. |

#### Namespaced Extension Boolean Keys

In addition to the built-in enums above, the core also supports addon-registered namespaced boolean setting keys such as `adventure:anchor_use` or `community:treasury_use`.

The first version only supports two extension categories:

1. extension permissions: boolean, can be global or personal
2. extension rules: boolean, always global

These keys must be registered by an addon through `RegionDataApi` before they can be used in commands or API queries. Once registered, they can be managed through the same `/imyvmWorldGeo setting` and `/imyvmWorldGeo settingScope` commands as built-in settings.

Resolution order:

1. extension permissions: scope personal → scope global → region personal → region global → registered default
2. extension rules: scope global → region global → registered default

Extension settings are persisted by their string key, so addon-defined keys do not depend on core enum ordinal layout.

#### Effect Keys

Effect settings apply Minecraft status effects to players inside a region or scope. The value is an effect amplifier from `0` to `255` (0-indexed: 0 = Level 1, 1 = Level 2, etc.). Effects are refreshed every lazy ticker interval and last for `effect.duration_seconds` (default 5 s) after each application. Effect settings can be global or personal. Scope resolution follows the priority: scope personal → region personal → timed overlay → scope global → region global. Region-only resolution follows region personal → region global.

Effects are applied silently (ambient-style: icon shown, no particles).

| Key | Minecraft Effect | Description |
|-----|-----------------|-------------|
| SPEED | speed | Increases movement speed. |
| JUMP | jump_boost | Increases jump height. |
| DAMAGE_RESISTANCE | resistance | Reduces damage taken. |
| SLOWNESS | slowness | Decreases movement speed. |
| HASTE | haste | Increases mining and attack speed. |
| MINING_FATIGUE | mining_fatigue | Decreases mining and attack speed. |
| STRENGTH | strength | Increases melee attack damage. |
| INSTANT_HEALTH | instant_health | Instantly restores health (applied each tick cycle). |
| INSTANT_DAMAGE | instant_damage | Instantly deals damage (applied each tick cycle). |
| NAUSEA | nausea | Causes a wobbling/distortion screen effect. |
| REGENERATION | regeneration | Restores health over time. |
| FIRE_RESISTANCE | fire_resistance | Grants immunity to fire and lava damage. |
| WATER_BREATHING | water_breathing | Prevents drowning and allows breathing underwater. |
| INVISIBILITY | invisibility | Makes the player invisible to other entities. |
| BLINDNESS | blindness | Reduces vision range significantly. |
| NIGHT_VISION | night_vision | Allows the player to see clearly in darkness. |
| HUNGER | hunger | Increases food exhaustion rate. |
| WEAKNESS | weakness | Decreases melee attack damage. |
| POISON | poison | Deals damage over time (does not kill below 1 HP). |
| WITHER | wither | Deals damage over time and can kill. |
| HEALTH_BOOST | health_boost | Increases maximum health. |
| ABSORPTION | absorption | Adds extra hearts that absorb damage before health. |
| SATURATION | saturation | Restores food and saturation (applied each tick cycle). |
| GLOWING | glowing | Causes the player to glow with an outline visible to others. |
| LEVITATION | levitation | Causes the player to float upward. |
| LUCK | luck | Increases loot quality from fishing and loot tables. |
| UNLUCK | unluck | Decreases loot quality from fishing and loot tables. |
| SLOW_FALLING | slow_falling | Reduces fall speed and nullifies fall damage. |
| CONDUIT_POWER | conduit_power | Grants water breathing, night vision, and haste underwater. |
| DOLPHINS_GRACE | dolphins_grace | Increases swimming speed. |
| BAD_OMEN | bad_omen | Triggers a raid when the player enters a village. |
| HERO_OF_THE_VILLAGE | hero_of_the_village | Grants discounts from villagers and gifts from villagers. |
| DARKNESS | darkness | Periodically dims the player's vision. |
| TRIAL_OMEN | trial_omen | Causes trial spawners to become ominous. |
| RAID_OMEN | raid_omen | Converts to Bad Omen when entering a village. |
| WIND_CHARGED | wind_charged | Causes a wind burst on death. |
| WEAVING | weaving | Leaves cobweb blocks on death. |
| OOZING | oozing | Spawns slimes on death. |
| INFESTED | infested | Spawns silverfish when taking damage. |

#### Entry-Exit Keys

Entry-exit settings control entry and exit notifications displayed to players when they cross region or scope boundaries. These settings are always global (not player-specific). Region-level notifications are shown as a title displayed in the center of the screen; scope-level notifications are shown as chat messages. Message values support MOTD-style color and formatting codes (e.g. `&6&lWelcome to {0}!`). The placeholder `{0}` is replaced by the region name and `{1}` by the scope name where applicable. If a toggle is enabled and the corresponding message is not set, a default message is applied automatically.

For region-level entry, a debounce rule applies: the title is suppressed if the player re-enters the same region within `entry_exit.region_delay_seconds` seconds (default 5) without having visited any other region in between. GeoScope notifications are always immediate with no debounce.

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| ENTRY_EXIT_MESSAGE_ENABLED | Boolean | true | Toggle entry and exit notifications for the region or scope. |
| ENTER_MESSAGE | String | (auto) | Message shown on entry. For regions, displayed as a title; for scopes, sent to chat. If not set and `ENTRY_EXIT_MESSAGE_ENABLED` is `true`, a default message is applied. |
| EXIT_MESSAGE | String | (auto) | Message shown on exit. For regions, displayed as a title; for scopes, sent to chat. If not set and `ENTRY_EXIT_MESSAGE_ENABLED` is `true`, a default message is applied. |

Default auto-applied messages:
- Region enter: `&6&l踏入 &e{0}&6&l 境界`
- Region exit: `&7离开 &e{0}&7 境界`
- Scope enter: `&a踏入 &e{0}&a 境界 &b{1}&a 地方`
- Scope exit: `&7离开 &e{0}&7 境界 &b{1}&7 地方`

### Teleport Point

A teleport point is attached to a scope in the region,
and the regional teleport point is defined by the teleport point of its main scope,
technically the teleport point of the first scope in line.

A teleport point is subject to physical safety validation both when it is set and when it is used for teleportation.
The physical safety requirements are:
1. The block at the feet position (the teleport point's Y level) must be fully passable with no block collision.
2. The block at the head position (one block above the teleport point) must be fully passable with no block collision.
3. The block directly beneath the teleport point must be a solid, full-square block to support the player.
4. Neither the feet block nor the head block may contain a liquid (water or lava), to prevent drowning or burning.

These requirements allow indoor teleport points as long as the space is clear and the floor is solid.
When a scope is created, the player's position is used only if it is inside the new scope and physically safe.
Otherwise, the same bounded fallback search described below is performed around the player; if no valid
position is found, the scope is created without a teleport point and one can be set later.
When teleportation is requested, the safety of the stored teleport point is rechecked against the current world state.
If the point is no longer safe (e.g., due to subsequent block changes), the system searches a configurable,
bounded cube centered on the original point (radius 0-8, with a 5x5x5 cube used by default). Candidates are
checked in increasing Manhattan distance using Minecraft's native block-position traversal, and must remain
inside the scope and satisfy the same physical safety requirements. If a safe alternative is found,
the teleport point is automatically updated and the player is teleported there with a warning message.
If no safe alternative is found within the search area, teleportation is cancelled and the player is informed.

### Dynmap Integration

When a Dynmap Fabric build compatible with the installed Minecraft version is present, all regions and their scopes are automatically rendered on the dynamic world map. If Dynmap is absent, this feature is silently skipped.

Each scope is displayed as a labeled overlay in the format `RegionName:ScopeName`. The shape type determines the marker type: rectangle and polygon scopes use area overlays; circle scopes use circle overlays. Teleport points are shown as house-icon markers at their exact coordinates.

All scopes belonging to the same region share one color. The color is derived from the first color keyword found in the region name, supporting both Chinese (e.g. 红, 蓝, 绿) and English (e.g. red, blue, green) color words. When no color keyword is present, a distinct color is selected from a fixed palette using the region's numeric ID.

Markers are updated automatically whenever region data is saved.

Region and Scope management operations save data before reporting success, and normal server shutdown
performs a final save. Dynmap markers are synchronized after each successful save.

Each region and each scope has a `showOnDynmap` flag (default: `true`). The region-level flag takes precedence: if a region's `showOnDynmap` is `false`, none of its scopes will appear on the map regardless of their individual flags. If the region flag is `true`, each scope is rendered only if its own `showOnDynmap` is also `true`.

---

## Usage

- This mod provides region and scope management commands for server. All commands are executed on the server side and require the player to have the appropriate permissions.  
- Addon APIs are provided through the `com.imyvm.iwg.inter.api` package.

> For detailed commands and API usage, see the `Command` and `API` sections in this README.

## API Documentation

The supported addon package is `com.imyvm.iwg.inter.api`. Public declarations in implementation
packages are not part of the supported addon surface. Compatibility guarantees, deprecated migrations,
and retained JVM signatures are documented in
[`docs/addon-api-compatibility.md`](docs/addon-api-compatibility.md).

`RegionDataApi` returns live Region and Scope read handles, but those handles are not independent
write channels. Persisted mutations require the exact live Region and GeoScope owned by the active
database and must go through `PlayerInteractionApi`. Detached or stale objects are rejected.

### Selection and creation

Selection state belongs to a `ServerPlayer` and is changed with:

- `startSelection(player, shapeType?)`
- `stopSelection(player)`
- `resetSelection(player, shapeType?)`
- `setSelectionShape(player, shapeType)`
- `startSelectionForModify(player, scope)`

Creation consumes the player's current selection. A fixed selection shape is respected; otherwise
the geometry is inferred from the selected points. API names are required, and Region `idMark` values
must be from `0` to `9`.

```kotlin
PlayerInteractionApi.startSelection(player, GeoShapeType.CIRCLE)
// The normal block interaction flow records the required points.
val region = PlayerInteractionApi.createAndGetRegion(player, "Market", idMark = 0) ?: return
val (_, scope) = PlayerInteractionApi.createAndGetRegionScopePair(player, region, "Square") ?: return
```

`createRegion` and `addScope` return `1` only after persistence succeeds and `0` otherwise.
The object-returning variants return `null` on validation, geometry, capacity, or persistence
failure. Successful creation clears the selection; persistence failure keeps it available for retry.

Immutable addon-created shapes use `GeoShape.rectangle`, `GeoShape.circle`, or
`GeoShape.polygon`. A polygon is limited to 256 vertices.

### Region and Scope mutation

The supported mutation operations are grouped by capability:

| Capability | Recommended operations |
| --- | --- |
| Region lifecycle | `renameRegion`, `deleteRegionWithResult` |
| Scope lifecycle | `renameScope`, `deleteScopeWithResult`, `transferScope`, `mergeRegion` |
| Geometry | `modifyScope`, `replaceScopeShape` |
| Teleport point | `addTeleportPoint`, `resetTeleportPoint`, `toggleTeleportPointAccessibility` |
| Teleport | `teleportPlayerToScope`, `teleportPlayerToScopeAsAdministrator` |

Deletion exposes persistence outcomes instead of reporting false success:

```kotlin
when (PlayerInteractionApi.deleteScopeWithResult(player, region, scope)) {
    ScopeDeleteResult.SUCCESS -> handleDeletedScope()
    ScopeDeleteResult.LAST_SCOPE -> handleLastScope()
    ScopeDeleteResult.PERSISTENCE_FAILED -> handlePersistenceFailure()
}
```

The ordinary teleport operation requires a public teleport point. The administrator operation bypasses
only accessibility; canonical ownership, dimension availability, physical safety, bounded fallback,
persistence, and rollback remain enforced.

`replaceScopeShape` accepts a complete immutable shape of the existing type, validates ownership,
configured size limits, and intersections, and restores the previous shape if persistence fails.

### Typed setting mutation

Addon mutations use a key type that identifies the setting family. Global and player subjects are
represented by separate overloads instead of nullable parameters.

| Family | Region operations | Scope operations | Personal subject |
| --- | --- | --- | --- |
| Permission (`PermissionKeyLike`) | `addRegionPermission`, `removeRegionPermission` | `addScopePermission`, `removeScopePermission` | Supported |
| Effect (`EffectKey`) | `addRegionEffect`, `removeRegionEffect` | `addScopeEffect`, `removeScopeEffect` | Supported |
| Rule (`RuleKeyLike`) | `addRegionRule`, `removeRegionRule` | `addScopeRule`, `removeScopeRule` | Not supported |
| Entry/exit toggle (`EntryExitToggleKey`) | `addRegionEntryExitToggle`, `removeRegionEntryExitToggle` | `addScopeEntryExitToggle`, `removeScopeEntryExitToggle` | Not supported |
| Entry/exit message (`EntryExitMessageKey`) | `addRegionEntryExitMessage`, `removeRegionEntryExitMessage` | `addScopeEntryExitMessage`, `removeScopeEntryExitMessage` | Not supported |

Personal Permission and Effect overloads take a non-null target `UUID`. All Scope operations take
the owning Region and exact live Scope.

```kotlin
val result = PlayerInteractionApi.addScopePermission(
    player,
    region,
    scope,
    PermissionKey.BUILD,
    value = false,
    targetPlayer = visitorId
)
when (result) {
    SettingAddResult.SUCCESS -> handleSaved()
    SettingAddResult.ALREADY_EXISTS -> handleExistingValue()
    SettingAddResult.PERSISTENCE_FAILED -> handlePersistenceFailure()
}
```

Removal returns `SettingRemoveResult.SUCCESS`, `NOT_FOUND`, or `PERSISTENCE_FAILED`.
Extension Permission and Rule keys must be registered before mutation.

Player-notifying convenience queries use explicit default, Region, Region-player, Scope, and
Scope-player operations: `getDefaultPermissionValue`, `getRegionPermissionValue`,
`getRegionPlayerPermissionValue`, `getScopePermissionValue`, and
`getScopePlayerPermissionValue`. These accept built-in or registered extension key strings; addon
logic that already has typed keys can use the `RegionDataApi` queries below.

### Region data and typed queries

Region and Scope lookup operations include:

- `getRegion`, `getRegionByName`, `getRegionList`, and `getRegionListFiltered`
- `getRegionScopes`, `getRegionScopePair`, and `getRegionScopePairByLocation`
- `resolveScopeAtEntity`
- `getScopeShape`, `getScopeArea`, `getRegionArea`, `getRegionScopeCount`, and teleport-point reads
- `getRegionFoundingTime` and `getRegionAge`

Location lookups accept a Minecraft `Level` with either block coordinates or a `BlockPos`.
Returned lists are snapshots; mutating them does not change persisted Region state.

Extension keys are registered before use:

```kotlin
RegionDataApi.registerExtensionPermissionKey("example:anchor_use", defaultValue = false)
RegionDataApi.registerExtensionRuleKey("example:custom_rule", defaultValue = true)
val permissionKey = ExtensionPermissionKey("example:anchor_use")
val ruleKey = ExtensionRuleKey("example:custom_rule")
```

`getRegisteredExtensionPermissionKeys` and `getRegisteredExtensionRuleKeys` return registration
snapshots. Explicit Setting snapshots are available through the Region/Scope global/personal
`get...Settings` and `get...SettingsByType` operations; mutations still go through
`PlayerInteractionApi`.

Built-in Permission queries are explicit about location and subject:

| Target | Operation |
| --- | --- |
| Config default | `getDefaultPermissionValue` |
| Region global/player | `getRegionGlobalPermissionValue`, `getRegionPlayerPermissionValue` |
| Scope global/player | `getScopeGlobalPermissionValue`, `getScopePlayerPermissionValue` |

Extension Permission queries use the corresponding
`getDefaultExtensionPermissionValue`, `getRegionGlobalExtensionPermissionValue`,
`getRegionPlayerExtensionPermissionValue`, `getScopeGlobalExtensionPermissionValue`, and
`getScopePlayerExtensionPermissionValue` operations.

Built-in Rule queries use `getDefaultRuleValue`, `getRegionRuleValue`, and
`getScopeRuleValue`. Extension Rule queries use `getDefaultExtensionRuleValue`,
`getRegionExtensionRuleValue`, and `getScopeExtensionRuleValue`.

Effect queries use `getRegionEffectValue`, `getScopeEffectValue`,
`getRegionActiveEffects`, and `getScopeActiveEffects`. A Scope query with a player UUID resolves
Scope personal → Region personal → timed overlay → Scope global → Region global. Region-only queries
resolve Region personal → Region global.

`getEffectiveEffectsForScope` has no player UUID and therefore resolves timed overlay → Scope
global → Region global. `getEffectiveRulesForScope` returns every built-in Rule using
Scope → Region → configured default.

### Assigned Scope identity and timed overlays

Persisted Scopes expose an `AssignedScopeId`. Kotlin addons use the typed operations:

```kotlin
val scopeId = RegionDataApi.getAssignedScopeIdOrNull(scope) ?: return
val resolved = RegionDataApi.getScopeByAssignedId(scopeId)
val parsed = RegionDataApi.parseAssignedScopeId(scopeId.toIdString())
val history = RegionDataApi.getAssignedScopeOwnershipHistory(scopeId)
```

`getScopeFoundingTimeOrNull` and `getScopeFoundedInRegionNumberId` expose identity metadata for an
assigned Scope.

Java addons use validated primitive-`long` bridges because Kotlin value-class methods are not stable
Java source entry points:

```java
Long scopeIdRaw = RegionDataApi.INSTANCE.parseAssignedScopeIdRaw(scopeIdText);
if (scopeIdRaw == null) return;

Pair<Region, GeoScope> resolved =
    RegionDataApi.INSTANCE.getScopeByAssignedIdRaw(scopeIdRaw.longValue());
String persistedText =
    RegionDataApi.INSTANCE.formatAssignedScopeIdRaw(scopeIdRaw.longValue());
List<ScopeOwnershipEntry> history =
    RegionDataApi.INSTANCE.getAssignedScopeOwnershipHistoryRaw(scopeIdRaw.longValue());
```

Timed overlays are transient and Scope-scoped. Their active interval is
`[startEpochMillis, endEpochMillis)`; effect lists are defensively snapshotted. Overlay calls are
safe from arbitrary addon threads and are serialized with Region/Scope deletion where persistence
ownership matters.

```kotlin
val overlay = RegionDataApi.createTimedEffectOverlay(
    overlayId = "event:festival",
    scopeId = scopeId,
    effects = listOf(TimedEffect(EffectKey.SPEED, amplifier = 1)),
    startMillis = startsAt,
    endMillis = expiresAt,
    priority = 0,
    source = "example"
)
RegionDataApi.applyTimedEffectOverlay(overlay)
val effectiveOverlay = RegionDataApi.queryOverlay(scopeId)
RegionDataApi.clearTimedEffectOverlay(scopeId, overlay.overlayId)
```

Java integrations use `createTimedEffectOverlayRaw`, `clearTimedEffectOverlayRaw`,
`queryOverlayRaw`, and `queryActiveOverlaysRaw`.

### Statistics, messages, and utilities

Natural-stat operations are `getRegionNaturalStats` and `getScopeNaturalStats`. They report a
typed `RegionNaturalStatsResult` and never load an unloaded chunk. `getRegionPlayerStats` returns
persistent aggregate player activity.

Entry/exit reads use `getRegionEntryExitToggle`, `getRegionEntryExitMessage`,
`getScopeEntryExitToggle`, and `getScopeEntryExitMessage`.

Player-facing helpers in `PlayerInteractionApi` include `queryRegionInfo`,
`queryRegionNaturalStats`, `queryRegionPlayerStats`, `toggleActionBar`,
`estimateRegionArea`, and `estimateScopeAreaChange`.

`UtilApi` provides selection/action-bar state, player/profile lookup, Region founding-time parsing,
and `isValidName`. Player overloads use `ServerPlayer`; server-wide overloads use
`MinecraftServer`.

## Commands

Arguments containing spaces or non-ASCII Region/Scope names are wrapped in double quotes.

### Selection

- `/imyvmWorldGeo select start [shapeType]` — start a creation selection with an optional rectangle, circle, or polygon hint.
- `/imyvmWorldGeo select stop` — stop selection mode.
- `/imyvmWorldGeo select reset [shapeType]` — clear points while retaining creation selection mode.
- `/imyvmWorldGeo select shape <shapeType>` — change the creation shape hint.
- `/imyvmWorldGeo select modifyScope <regionIdentifier> <scopeName>` — start a selection locked to one existing Scope.

### Region and Scope management

- `/imyvmWorldGeo create [name]` — create a Region from the current selection; shape comes from selection state or point inference.
- `/imyvmWorldGeo delete <regionIdentifier>`
- `/imyvmWorldGeo rename <regionIdentifier> <newName>`
- `/imyvmWorldGeo addScope <regionIdentifier> [scopeName]` — add a Scope from the current selection.
- `/imyvmWorldGeo deleteScope <regionIdentifier> <scopeName>`
- `/imyvmWorldGeo modifyScope <regionIdentifier> <scopeName> [newName]` — apply the current modification selection or rename the Scope.
- `/imyvmWorldGeo transferScope <regionIdentifier> <scopeName> <targetRegionIdentifier>`
- `/imyvmWorldGeo mergeRegion <regionIdentifier> <targetRegionIdentifier>`

Scope name conflicts during transfer or merge are resolved with a numeric suffix. A Region cannot lose
its last Scope through normal deletion or transfer.

### Teleport

- `/imyvmWorldGeo teleportPoint set [<x> <y> <z>]`
  - With no position tuple, the player's current block position is used.
  - An explicit tuple uses the vanilla block-position grammar: absolute coordinates, `~`
    world-relative coordinates, and `^` local-relative coordinates are supported.
  - Partial or malformed tuples are rejected by Brigadier and do not execute the command.
- `/imyvmWorldGeo teleportPoint reset` — reset the current Scope's teleport point.
- `/imyvmWorldGeo teleportPoint reset <regionIdentifier> <scopeName>` — reset an explicit Scope.
- `/imyvmWorldGeo teleportPoint inquiry` — inspect the current Scope's teleport point.
- `/imyvmWorldGeo teleportPoint inquiry <regionIdentifier> <scopeName>` — inspect an explicit Scope.
- `/imyvmWorldGeo teleport <regionIdentifier> [scopeName]` — teleport through a publicly accessible point. Omitting the Scope selects the first public Scope with a point.
- `/imyvmWorldGeo teleportPoint teleport <regionIdentifier> <scopeName>` — administrator teleport that bypasses only accessibility.
- `/imyvmWorldGeo teleportPoint toggle <regionIdentifier> <scopeName>` — toggle public accessibility.

Invalid explicit Region/Scope arguments do not fall back to the player's current Scope.

### Settings

- `/imyvmWorldGeo setting add <regionIdentifier> <key> <value> [playerName]`
- `/imyvmWorldGeo setting remove <regionIdentifier> <key> [playerName]`
- `/imyvmWorldGeo setting queryValue <regionIdentifier> <key> [playerName]`
- `/imyvmWorldGeo settingScope add <regionIdentifier> <scopeName> <key> <value> [playerName]`
- `/imyvmWorldGeo settingScope remove <regionIdentifier> <scopeName> <key> [playerName]`
- `/imyvmWorldGeo settingScope queryValue <regionIdentifier> <scopeName> <key> [playerName]`

Commands accept built-in keys and addon-registered namespaced extension keys. Permission and Effect
keys may use `playerName`; Rule and Entry/Exit keys are global. Entry/Exit keys are
`ENTRY_EXIT_MESSAGE_ENABLED`, `ENTER_MESSAGE`, and `EXIT_MESSAGE`.

### Information and integration

- `/imyvmWorldGeo dynmapToggle <regionIdentifier>`
- `/imyvmWorldGeo dynmapToggleScope <regionIdentifier> <scopeName>`
- `/imyvmWorldGeo query <regionIdentifier>`
- `/imyvmWorldGeo stats <regionIdentifier> [category]` — category is `all`, `structures`, `difficulty`, `surface`, `biomes`, or `players`.
- `/imyvmWorldGeo list`
- `/imyvmWorldGeo toggle`
- `/imyvmWorldGeo help`

## Acknowledgements

Were it not for the support of IMYVM fellows and players, this project would not have been possible.
