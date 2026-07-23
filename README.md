# IMYVMWorldGeo 26.2 1.5.5

## Changelog

### 1.5.x

The 1.5.x line expands WorldGeo from region settings into a neutral infrastructure layer for addons. It adds RPG permission and rule keys, namespaced extension settings, stable Scope IDs, ownership history, SubSpace, neutral time and behavior events, periodic statistics, and menu-facing space snapshots.

#### 1.5.5

- feat: land the V1-V3 WorldGeo infrastructure path for SubSpace, neutral time, behavior events, periodic stats, geography snapshots, setting summaries, space messages, and OP debug views.
- feat: add typed statistics queries for block deltas, residence time, entity combat, and online or AFK time on top of the persisted behavior statistics store.
- feat: extend space snapshots with dominant-biome hints, entry-message state, map color suggestions, shape type, copied shape parameters, and inline public setting summaries for addon menu lore and detail pages.
- fix: enumerate every missed natural period on restart so hour, day, week, month, and year subscribers can backfill skipped settlement cycles without receiving duplicate current-period callbacks.
- feat: add `listPlayersInRegion`/`Scope`/`SubSpace` to `RegionDataApi` with cache and instant-resolution fallback.
- feat: add `toggleLocationActionBar`, `setLocationActionBarVisible`, `isLocationActionBarVisible` to `PlayerInteractionApi`.
- feat: add messageKey+args overloads for `sendRegionSpaceMessage`, `sendScopeSpaceMessage`, `sendSubSpaceMessage`.
- feat: add `openSpaceDebugView` to `PlayerInteractionApi` with OP permission check for Region, Scope, and SubSpace.
- feat: add direct `GeoShape` overloads for `createRegion`, `createAndGetRegion`, `addScope`, `createAndGetRegionScopePair`.
- feat: add `eventId`, `quantity`, and `source` fields to `WorldGeoBehaviorEvent` with safe defaults.
- feat: add `YEAR` to `NaturalPeriodKind` with full enumeration, backfill, and test-mode support.
- feat: add `getRealTimeSnapshot(zoneId)` to `RegionDataApi` for configurable timezone queries.

## Introduction

This is a mod to provide a geography system framework for Imyvm server players and groups, 
and is also a basement for extensions, exemplified by jungle system, community system and so on.

## Environment Requirements

This mod is **server-side only** and requires the following environment:

- **Minecraft Version:** 26.2
- **Fabric Loader Version:** 0.19.3 (or compatible with Minecraft 26.2)
- **Java Version:** 25
- **IMYVM Hoki Mod Version:** 1.1.6

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
A shape can be of `RECTANGLE`, `CIRCLE` or `POLYGON` type. Addons should construct immutable shapes
through `GeoShape.rectangle`, `GeoShape.circle`, or `GeoShape.polygon`; the legacy flat
`shapeParameter` list is retained only for binary and persistence compatibility. Polygon shapes
support at most 256 vertices.

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
| ANIMAL_KILLING | - | true | Passive animal damage permission. Covers passive AnimalEntity (excluding goats), fish (cod, salmon, tropical fish, pufferfish, tadpole), squid, glow squid, dolphin, allay, and snow golem. Does not apply to monsters, neutral mobs, or goats. |
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
| RPG_FIRE_SPREAD | true | (RPG) Controls whether fire may burn or place fire at target blocks inside the region. Source fire still ages and extinguishes normally. |
| RPG_HUNGER | true | (RPG) Controls whether hunger and exhaustion drain for players inside the region. When set to false, all food exhaustion calls are suppressed. |

#### Namespaced Extension Boolean Keys

In addition to the built-in enums above, the core also supports addon-registered namespaced boolean setting keys such as `adventure:anchor_use` or `community:treasury_use`.

The first version only supports two extension categories:

1. extension permissions: boolean, can be global or personal
2. extension rules: boolean, always global

These keys must be registered by an addon through `RegionDataApi` before they can be used in commands or API queries. Once registered, they can be managed through the same `/imyvmWorldGeo setting`, `/imyvmWorldGeo scope setting`, and `/imyvmWorldGeo subspace setting` commands as built-in settings.

Resolution order:

1. extension permissions: scope personal → scope global → region personal → region global → registered default
2. extension rules: scope global → region global → registered default

Extension settings are persisted by their string key, so addon-defined keys do not depend on core enum ordinal layout.

#### Effect Keys

Effect settings apply Minecraft status effects to players inside a region or scope. The value is an effect amplifier from `0` to `255` (0-indexed: 0 = Level 1, 1 = Level 2, etc.). Effects are refreshed every lazy ticker interval and last for `effect.duration_seconds` (default 5 s) after each application. Effect settings can be global or personal. The effective value follows the priority: scope personal → scope global → region personal → region global.

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
- For extensions, APIs are exposed from package `com.imyvm.iwg.inter.api`; source files are under `src/main/kotlin/com/imyvm/iwg/entrypoint/api`.

> For detailed commands and API usage, see the `Command` and `API` sections in this README.

## API Documentation

Addon compatibility guarantees, deprecated API migrations, and removal rules are tracked in
[`docs/addon-api-compatibility.md`](docs/addon-api-compatibility.md).

This API provides functionality for interacting with geographical regions in a Minecraft server world,
which allows extension mods to enrich and build features based on defined regions.

### Player Interaction API

`PlayerInteractionApi` handles player-driven mutations and player-facing utility actions. These methods must be called with canonical `Region`, `GeoScope`, and `SubSpace` objects from `RegionDatabase` or `RegionDataApi`; detached copies are rejected by the application boundary.

Selection and shape editing:

- `startSelection(player: ServerPlayer, shapeType: GeoShapeType? = null)` starts a normal creation selection.
- `stopSelection(player: ServerPlayer)` stops selection mode.
- `resetSelection(player: ServerPlayer, shapeType: GeoShapeType? = null)` clears selected points and optionally updates the shape hint.
- `setSelectionShape(player: ServerPlayer, shapeType: GeoShapeType)` changes the shape hint for the current creation selection.
- `startSelectionForModify(player: ServerPlayer, scope: GeoScope)` starts selection mode locked to one live Scope.
- `modifyScope(player: ServerPlayer, region: Region, scopeName: String)` applies the current modification selection to a Scope.
- `replaceScopeShape(player: ServerPlayer, region: Region, scope: GeoScope, newShape: GeoShape)` replaces a live Scope shape after ownership, size, intersection, persistence, and SubSpace-containment checks.

Region and Scope mutations:

- `createRegion(player: ServerPlayer, name: String?, idMark: Int = 0)` creates a Region from the player's selection.
- `createAndGetRegion(player: ServerPlayer, name: String?, idMark: Int = 0): Region?` creates a Region from selection and returns it.
- `createRegion(player: ServerPlayer, name: String, idMark: Int = 0, shape: GeoShape): Int` creates a Region directly from an immutable shape.
- `createAndGetRegion(player: ServerPlayer, name: String, idMark: Int = 0, shape: GeoShape): Region?` creates and returns a Region directly from an immutable shape.
- `deleteRegion(player: ServerPlayer, region: Region)` deletes a Region after persistence succeeds.
- `renameRegion(player: ServerPlayer, region: Region, newName: String)` renames a Region.
- `addScope(player: ServerPlayer, region: Region, name: String?)` creates a Scope from the player's selection.
- `createAndGetRegionScopePair(player: ServerPlayer, region: Region, name: String?): Pair<Region, GeoScope>?` creates a Scope from selection and returns it with its Region.
- `addScope(player: ServerPlayer, region: Region, name: String, shape: GeoShape): Int` creates a Scope directly from an immutable shape.
- `createAndGetRegionScopePair(player: ServerPlayer, region: Region, name: String, shape: GeoShape): Pair<Region, GeoScope>?` creates and returns a Scope directly from an immutable shape.
- `deleteScope(player: ServerPlayer, region: Region, scopeName: String)` deletes a Scope by name.
- `renameScope(player: ServerPlayer, region: Region, oldName: String, newName: String)` renames a Scope.
- `transferScope(player: ServerPlayer, sourceRegion: Region, scopeName: String, targetRegion: Region)` transfers a Scope between Regions, with automatic name suffixing on target conflicts.
- `mergeRegion(player: ServerPlayer, sourceRegion: Region, targetRegion: Region)` moves all Scopes into the target Region and deletes the source Region.

SubSpace mutations:

- `createSubSpace(player: ServerPlayer, region: Region, parentScope: GeoScope, name: String, shape: GeoShape, entryMessage: String? = null, stringTags: Set<String> = emptySet(), keyedTags: Map<String, String> = emptyMap()): Int` creates a SubSpace under a Scope.
- `createAndGetSubSpace(player: ServerPlayer, region: Region, parentScope: GeoScope, name: String, shape: GeoShape, entryMessage: String? = null, stringTags: Set<String> = emptySet(), keyedTags: Map<String, String> = emptyMap()): SubSpace?` creates and returns a SubSpace.
- `deleteSubSpace(player: ServerPlayer, region: Region, parentScope: GeoScope, subSpace: SubSpace): Int` deletes a SubSpace.
- `renameSubSpace(player: ServerPlayer, region: Region, parentScope: GeoScope, subSpace: SubSpace, newName: String): Int` renames a SubSpace.
- `replaceSubSpaceShape(player: ServerPlayer, region: Region, parentScope: GeoScope, subSpace: SubSpace, newShape: GeoShape): Int` replaces a SubSpace shape after parent containment and sibling conflict checks.
- `addSubSpaceStringTag`, `removeSubSpaceStringTag`, `putSubSpaceKeyedTag`, and `removeSubSpaceKeyedTag` mutate SubSpace tags and persist or roll back the change.

Settings, teleport, and display:

- `addSettingRegion`, `addSettingScope`, `addSettingSubSpace`, `removeSettingRegion`, `removeSettingScope`, and `removeSettingSubSpace` mutate settings through the controlled persistence path.
- `getDefaultPermissionValue`, `getRegionPermissionValue`, `getRegionPlayerPermissionValue`, `getScopePermissionValue`, `getScopePlayerPermissionValue`, and the deprecated `getPermissionValueRegion` read effective permission values for command-style callers.
- `getRuleValueRegion(region: Region?, keyString: String): Boolean?` and `getRuleValueScope(region: Region?, scopeName: String, keyString: String): Boolean?` read explicitly set rule values for Region or Scope targets.
- `addTeleportPoint`, `resetTeleportPoint`, `getTeleportPoint`, `teleportPlayerToScope`, `teleportPlayerToScopeAsAdministrator`, and `toggleTeleportPointAccessibility` manage Scope teleport points.
- `toggleActionBar(player: ServerPlayer)` toggles the legacy location action bar.
- `toggleLocationActionBar(player: ServerPlayer)`, `setLocationActionBarVisible(player: ServerPlayer, visible: Boolean)`, and `isLocationActionBarVisible(player: ServerPlayer): Boolean` expose the V3 location action-bar API.
- `openSpaceDebugView(player: ServerPlayer, region: Region)`, `openSpaceDebugView(player: ServerPlayer, region: Region, scope: GeoScope)`, and `openSpaceDebugView(player: ServerPlayer, region: Region, parentScope: GeoScope, subSpace: SubSpace)` send OP-only neutral space debug output.
- `queryRegionInfo`, `queryRegionNaturalStats`, and `queryRegionPlayerStats` send player-facing Region information and stats messages.
- `estimateRegionArea` and `estimateScopeAreaChange` return area-estimation results for the current or supplied point list.
- `addEntryExitSettingRegion`, `addEntryExitSettingScope`, `removeEntryExitSettingRegion`, and `removeEntryExitSettingScope` are convenience wrappers for Region and Scope entry-exit settings.

### Region Data API

`RegionDataApi` is the supported read/query API for addons. Legacy object-returning methods still return live domain objects for compatibility, but mutable domain writes are guarded and new integrations should prefer snapshot and controlled mutation APIs.

Extension settings:

- `registerExtensionPermissionKey(key: String, defaultValue: Boolean = true)` registers a namespaced boolean extension permission key.
- `registerExtensionRuleKey(key: String, defaultValue: Boolean = true)` registers a namespaced boolean extension rule key.
- `getRegisteredExtensionPermissionKeys(): List<String>` and `getRegisteredExtensionRuleKeys(): List<String>` return registered extension keys.

Region, Scope, and SubSpace lookup:

- `getRegion(id: Int): Region?`, `getRegionByName(name: String): Region?`, `getRegionList(): List<Region>`, and `getRegionListFiltered(idMark: Int): List<Region>` return Region objects for legacy-compatible callers.
- `getRegionFoundingTime(region: Region): Long` and `getRegionAge(region: Region): Long` read Region ID-derived time facts.
- `getRegionScopes(region: Region): List<GeoScope>` and `getRegionSubSpaces(region: Region): List<SubSpace>` return detached lists of live child objects.
- `getRegionScopePair`, `getRegionScopePairByLocation`, `getRegionScopeSubSpaceByLocation`, `resolveScopeAtEntity`, and `resolveSubSpaceAtEntity` resolve spaces by name, ID, position, or entity coordinates.
- `getSubSpaceById(subSpaceId: Long): Triple<Region, GeoScope, SubSpace>?` and `getSubSpaceByName(region: Region, name: String): Pair<GeoScope, SubSpace>?` resolve canonical SubSpace objects.
- `parseAssignedScopeId`, `getScopeByAssignedId`, `getAssignedScopeIdOrNull`, `getScopeFoundingTimeOrNull`, `getScopeFoundedInRegionNumberId`, and `getAssignedScopeOwnershipHistory` expose stable Scope ID and ownership-history facts. Deprecated `ScopeId` wrappers remain for binary compatibility.

Snapshot and menu-support reads:

- `listScopeSnapshots(regionId: Int): List<WorldGeoSpaceSnapshot>` returns Scope snapshots under one Region.
- `listSubSpaceSnapshots(scopeId: Long): List<WorldGeoSpaceSnapshot>` returns SubSpace snapshots under one Scope.
- `getSpaceSnapshot(type: WorldGeoSpaceType, id: Long): WorldGeoSpaceSnapshot?` resolves Region, Scope, or SubSpace snapshots by stable ID.
- `getRegionSpaceSnapshot`, `getScopeSpaceSnapshot`, and `getSubSpaceSnapshot` return immutable neutral snapshots. Server-taking overloads include dominant-biome data from the natural geography scanner.
- `WorldGeoSpaceSnapshot` contains type, stable ID, display name, dimension, area, parent IDs and names, child counts, SubSpace tags, stats version, dominant biome hint, entry-message state, map color suggestion, PUBLIC setting summaries, shape type, and copied shape parameters.
- `listRegionSettingSummaries`, `listScopeSettingSummaries`, and `listSubSpaceSettingSummaries` return `WorldGeoSettingSummary` entries filtered by `WorldGeoSettingVisibility.PUBLIC` or `OP_DEBUG`.
- `sendRegionSpaceMessage`, `sendScopeSpaceMessage`, and `sendSubSpaceMessage` broadcast to online players currently resolved inside a Region, Scope, or SubSpace. Each target has a `Component` overload and a `messageKey, vararg args` overload that resolves text through `Translator.tr`; missing translation keys log an error and send nothing.
- `listPlayersInRegion(server, region)`, `listPlayersInScope(server, region, scope)`, and `listPlayersInSubSpace(server, region, scope, subSpace)` return online player UUIDs using the location cache with instant coordinate-resolution fallback.

Settings and effects:

- Explicit permission methods cover default, Region global, Region player, Scope global, Scope player, SubSpace global, and SubSpace player queries for built-in `PermissionKey` values.
- Extension permission methods mirror the same target levels and require prior key registration.
- Rule methods cover default, Region, Scope, and SubSpace queries for built-in `RuleKey` values.
- `getExtensionRuleValueForRegion(region: Region?, scope: GeoScope?, key: String)` resolves registered extension rules for Region or Scope targets.
- Effect methods cover Region, Scope, and SubSpace effect values and active effect maps.
- Timed effect overlay methods include `createTimedEffectOverlay`, `applyTimedEffectOverlay`, `clearTimedEffectOverlay`, `queryOverlay`, and `queryActiveOverlays`; deprecated `ScopeId` overloads remain for compatibility where present.
- Deprecated nullable dispatchers remain as delegating compatibility entry points and are documented in `docs/addon-api-compatibility.md`.
- `getRegionGlobalSettings`, `getRegionPersonalSettings`, `getScopeGlobalSettings`, and `getScopePersonalSettings` remain as legacy setting-list reads.

Time, events, and statistics:

- `getTimeSnapshot(level: ServerLevel): WorldGeoTimeSnapshot` returns game time and the default real-time snapshot.
- `getRealTimeSnapshot(zoneId: String): RealTimeSnapshot` returns real-time facts for any valid Java `ZoneId` string.
- `getCurrentNaturalPeriodIds(): Map<NaturalPeriodKind, String>` returns current `HOUR`, `DAY`, `WEEK`, `MONTH`, and `YEAR` IDs.
- `registerNaturalPeriodTransitionCallback`, `registerBehaviorEventCallback`, and `registerSubSpaceTransitionCallback` register bounded asynchronous addon callbacks.
- `getRecentBehaviorEvents()` and `getRecentBehaviorEvents(limit: Int)` expose the in-memory behavior debug window.
- `WorldGeoBehaviorEvent` contains event ID, behavior type, player UUID and name, dimension, coordinates, UNIX time, Region, Scope, SubSpace, space level, object ID, target ID, quantity, and source.
- `queryBehaviorStats(WorldGeoBehaviorStatsQuery)` and explicit overloads return persisted neutral behavior counts filtered by period, space, player, behavior type, object ID, and target ID.
- `queryBlockDelta`, `queryResidence`, `queryEntityCombat`, and `queryOnlineTime` return typed statistics for block net delta, chunk residence millis, combat facts, and online or AFK millis.

Natural geography and player stats:

- `getRegionNaturalStats`, `getScopeNaturalStats`, and `getSubSpaceNaturalStats` return live natural scan results.
- `getRegionGeographicProfile`, `getRegionGeographicProfileSnapshot`, `getScopeGeographicProfile`, `getScopeGeographicProfileSnapshot`, `getSubSpaceGeographicProfile`, and `getSubSpaceGeographicProfileSnapshot` expose geography profiles for Region, Scope, and SubSpace.
- `getGeographicProfileCacheStatus()` and `refreshGeographicProfiles(server)` expose cache status and a manual refresh entry point.
- `getRegionPlayerStats(region: Region): RegionPlayerStats` returns persistent cumulative Region activity totals.

`RegionNaturalStatsResult` has three variants:

1. `Success(stats: RegionNaturalStats)` for a completed scan.
2. `ChunkLimitExceeded(dimensionId, candidateChunkCount, limit)` when candidate chunks exceed the configured scan limit.
3. `DimensionUnavailable(dimensionId)` when the target dimension cannot be resolved from the running server.

`RegionPlayerStats` reports persistent cumulative player activity per Region. Entry counting is driven by confirmed Region transitions, stay duration is accumulated over time and flushed on disconnect or server stop, deaths are counted at the player's death position, and block place or break counts only record successful actions inside the Region.

Entry-exit reads:

- `getRegionEntryExitToggle(region: Region): Boolean` and `getScopeEntryExitToggle(scope: GeoScope): Boolean` return whether entry-exit notifications are enabled, defaulting to `true`.
- `getRegionEntryExitMessage(region: Region, key: EntryExitMessageKey): String?` and `getScopeEntryExitMessage(scope: GeoScope, key: EntryExitMessageKey): String?` return configured messages or `null`.

### UtilApi

Provides utility functions for region data to improve usability for extension mods.

- `isSelectingPoints(playerExecutor: ServerPlayer): Boolean`
  Checks if the player is in selection mode.

- `isActionBarEnabled(playerExecutor: ServerPlayer): Boolean`
  Checks if the action bar display is enabled for the player.

- `getPlayerUUID(server: MinecraftServer, playerName: String): UUID?`
  Retrieves the UUID of a player by their name.

- `getPlayerUUID(player: ServerPlayer, playerName: String): UUID?`
  Retrieves the UUID of a player by their name using a player entity.

- `getPlayerName(server: MinecraftServer, uuid: UUID?): String`
  Retrieves the player name by their UUID.

- `getPlayerName(player: ServerPlayer, uuid: UUID?): String`
  Retrieves the player name by their UUID using a player entity.

- `getPlayer(playerExecutor: ServerPlayer, playerName: String): ServerPlayer?`
  Retrieves the player entity by their name.

- `getPlayer(server: MinecraftServer, playerName: String): ServerPlayer?`
  Retrieves the player entity by their name using the server instance.

- `getPlayer(playerExecutor: ServerPlayer, playerUuid: UUID): ServerPlayer?`
  Retrieves the player entity by their UUID.

- `getPlayer(server: MinecraftServer, playerUuid: UUID): ServerPlayer?`
  Retrieves the player entity by their UUID using the server instance.

- `getPlayerProfile(server: MinecraftServer, playerName: String): GameProfile?`
  Retrieves the GameProfile of a player by their name.

- `getPlayerProfile(playerExecutor: ServerPlayer, playerName: String): GameProfile?`
  Retrieves the GameProfile of a player by their name using a player entity to get server.

- `getPlayerProfile(server: MinecraftServer, playerUuid: UUID): GameProfile?`
  Retrieves the GameProfile of a player by their UUID.

- `getPlayerProfile(playerExecutor: ServerPlayer, playerUuid: UUID): GameProfile?`
  Retrieves the GameProfile of a player by their UUID using a player entity to get server.

- `parseRegionFoundingTime(regionNumberId: Int): Long`
  Parses and retrieves the founding time of a region by its numeric ID.

- `isValidName(name: String): Boolean`
  Checks whether a Region, Scope, or SubSpace display name is accepted by the shared name validator.

## Commands

- `/imyvmWorldGeo select start [shapeType]`
  Start selecting positions with a command block. Right-click a block to add a point; left-click (on any block or in air) to undo the last point. Optionally provide a shape hint (rectangle, circle, polygon).

- `/imyvmWorldGeo select stop`
  Stop selection mode.

- `/imyvmWorldGeo select reset [shapeType]`
  Clear all selected points but keep selection mode active. Optionally update the shape hint.

- `/imyvmWorldGeo select shape <shapeType>`
  Change the shape hint for the current selection. Cannot be used in scope-modification mode.

- `/imyvmWorldGeo create [shapeType] [name]`
  Create a region of the given shape (rectangle, circle, polygon) from selected positions. If shapeType is omitted, the shape is inferred from the selection state.
  Optionally give it a name.

- `/imyvmWorldGeo delete <regionIdentifier>`
  Delete a region by its ID or name.

- `/imyvmWorldGeo rename <regionIdentifier> <newName>`
  Rename an existing region.

- `/imyvmWorldGeo scope create <regionIdentifier> [scopeName]`
  Create a new scope in the region from the current selection. The shape is inferred from the selection state. Optionally provide a scope name.

- `/imyvmWorldGeo scope delete <regionIdentifier> <scopeName>`
  Delete a scope from a region.

- `/imyvmWorldGeo scope transfer <regionIdentifier> <scopeName> <targetRegionIdentifier>`
  Transfer a scope from one region to another. If a scope with the same name already exists in the target region, the scope is automatically renamed by appending a numeric suffix (e.g., `scopeName1`, `scopeName2`).

- `/imyvmWorldGeo mergeRegion <regionIdentifier> <targetRegionIdentifier>`
  Merge one region into another: all scopes are moved (with automatic renaming on conflict), the source region's overall settings are discarded, and the source region is deleted.

- `/imyvmWorldGeo teleportPoint set [x] [y] [z]`
  Set the teleport point for the current region and scope.
  - If `x`, `y`, and `z` are provided and valid, the teleport point will be set to the specified coordinates.
    - The adjective 'valid' means they are all numbers or '~', which represents the player's current coordinate on that axis,
      and it satisfies criteria for a safe teleport point physically.
  - If `x`, `y`, and `z` are omitted or invalid, the teleport point will default to the player's current position.

- `/imyvmWorldGeo teleportPoint reset <regionIdentifier> <scopeName>`
  Reset the teleport point for the specified region and scope.
  - If `regionIdentifier` and `scopeName` are provided and valid, the teleport point for the specified scope will be reset.
  - If `regionIdentifier` and `scopeName` are omitted or invalid, the teleport point for the scope the player is currently in will be reset.
    - If player is not in any scope, an error message will be shown.

- `/imyvmWorldGeo teleportPoint inquiry <regionIdentifier> <scopeName>`
  Inquire about the teleport point for the specified region and scope.
  - If `regionIdentifier` and `scopeName` are provided and valid, the teleport point for the specified scope will be displayed.
  - If `regionIdentifier` and `scopeName` are omitted or invalid, the teleport point for the scope the player is currently in will be displayed.
    - If player is not in any scope, an error message will be shown.

- `/imyvmWorldGeo teleport <regionIdentifier> [scopeName]`
  Teleport the player to a publicly accessible teleport point. Omitting `scopeName` selects the first public scope with a teleport point.

- `/imyvmWorldGeo teleportPoint teleport <regionIdentifier> <scopeName>`
  Administrator-only teleport that bypasses teleport-point accessibility while retaining target safety checks.

- `/imyvmWorldGeo teleportPoint toggle <regionIdentifier> <scopeName>`
  Toggle the accessibility of the teleport point for the specified region and scope.

- `/imyvmWorldGeo scope modify <regionIdentifier> <scopeName>`
  Modify a scope geometry from the current modification selection.

- `/imyvmWorldGeo scope rename <regionIdentifier> <scopeName> <newName>`
  Rename a scope.

- `/imyvmWorldGeo setting add <regionIdentifier> <key> <value> [playerName]`
  Add a setting to a region, optionally for a specific player.
  Entry-exit keys (`ENTER_ENABLED`, `EXIT_ENABLED`, `ENTER_MESSAGE`, `EXIT_MESSAGE`) do not support personal player assignment.

- `/imyvmWorldGeo setting remove <regionIdentifier> <key> [playerName]`
  Remove a setting from a region, optionally for a specific player.

- `/imyvmWorldGeo setting queryValue <regionIdentifier> <key> [playerName]`
  Query the value of a setting in a region, optionally for a specific player.

- `/imyvmWorldGeo scope setting add <regionIdentifier> <scopeName> <key> <value> [playerName]`
  Add a setting to a specific scope of a region, optionally for a specific player.

- `/imyvmWorldGeo scope setting remove <regionIdentifier> <scopeName> <key> [playerName]`
  Remove a setting from a specific scope, optionally for a specific player.

- `/imyvmWorldGeo scope setting queryValue <regionIdentifier> <scopeName> <key> [playerName]`
  Query the value of a setting in a specific scope, optionally for a specific player.

- `/imyvmWorldGeo subspace modify <regionIdentifier> <subSpaceName> [shapeType]`
  Modify a SubSpace shape from the current SubSpace selection.

- `/imyvmWorldGeo subspace rename <regionIdentifier> <subSpaceName> <newName>`
  Rename a SubSpace.

- `/imyvmWorldGeo subspace setting add <regionIdentifier> <subSpaceName> <key> <value> [playerName]`
  Add a setting to a SubSpace, optionally for a specific player.

- `/imyvmWorldGeo subspace setting remove <regionIdentifier> <subSpaceName> <key> [playerName]`
  Remove a setting from a SubSpace, optionally for a specific player.

- `/imyvmWorldGeo subspace setting queryValue <regionIdentifier> <subSpaceName> <key> [playerName]`
  Query a setting value with SubSpace precedence, optionally for a specific player.

- `/imyvmWorldGeo dynmapToggle <regionIdentifier>`
  Toggle the region's visibility on the dynamic map. When a region is hidden, all its scopes are hidden regardless of their individual settings.

- `/imyvmWorldGeo scope dynmapToggle <regionIdentifier> <scopeName>`
  Toggle a scope's visibility on the dynamic map. Has no effect if the region itself is hidden.

- `/imyvmWorldGeo query <regionIdentifier>`
  Show detailed information about a region.

- `/imyvmWorldGeo stats <regionIdentifier> [category]`
  Query region statistics. `category` accepts `all`, `structures`, `difficulty`, `surface`, `biomes`, or `players`. Natural-stat scans only read loaded chunks, report loaded/candidate chunk coverage, count structures by anchored start chunk, sample the top non-air block for each in-region column, and calculate average local difficulty from in-region sampled columns. The `players` category returns persistent aggregated entry count, stay duration, death count, block place count, and block break count.

- `/imyvmWorldGeo list`
  List all regions.

- `/imyvmWorldGeo toggle`
  Toggle the action bar display for regions. When enabling, current-world scope boundaries are rendered on a bounded best-effort basis. Very large or numerous boundaries are sampled within one fixed per-player render budget. Scope boundaries remain visible while in selection mode; the scope being modified is highlighted in orange and other scopes use any remaining budget.

- `/imyvmWorldGeo help`
  Show the help message.

## Acknowledgements

Were it not for the support of IMYVM fellows and players, this project would not have been possible.
