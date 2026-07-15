# IMYVMWorldGeo 26.1 1.5.1

## Changelog

### 1.5.x

This major version (1.5.x) extends RPG setting items and infrastructure APIs.

#### 1.5.1

- feat: add `/imyvmWorldGeo stats` and RegionDataApi natural-stat queries for structure counts, average local difficulty, surface block counts, and biome distribution inside loaded chunks of a region.
- feat: add persistent region player statistics for entry count, stay duration, death count, block place count, and block break count, queryable through `/imyvmWorldGeo stats <regionIdentifier> players` and RegionDataApi.
- feat: add stable `ScopeId` (Long-encoded) carrying scope creation time and the founded-in region numberID; persisted alongside each `GeoScope` so scope identity survives renames and cross-region transfers.
- feat: add scope ownership history recording each cross-region scope transfer; queryable through `RegionDataApi.getScopeOwnershipHistory(scopeId)`.
- feat: add `ScopeTransitionEvent` and `RegionTransitionEvent` for downstream listeners to react to player crossings without duplicating the entry/exit tracker.
- feat: add `EffectOverlayService` and `RegionDataApi.applyTimedEffectOverlay / clearTimedEffectOverlay / queryOverlay` for short-lived scope-scoped effect overlays merged into the effect resolution chain at priority `personal > overlay > scope global > region global`.
- feat: add `RegionDataApi.resolveScopeAtEntity`, `getEffectiveEffectsForScope`, and `getEffectiveRulesForScope` for scope-level effect/rule queries that do not require a player UUID.
- feat: reserve `idMark = 1` for ImyvmWorldGeo-Adventure regions and `idMark = 2` for ImyvmCommunity regions, enabling addon-side mutual exclusion through `parseMarkFromRegionId`.


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
- For extensions, apis are provided in `src/main/kotlin/com/imyvm/iwg/inter/api` folder.

> For detailed commands and API usage, see the `Command` and `API` sections in this README.

## API Documentation

Addon compatibility guarantees, deprecated API migrations, and removal rules are tracked in
[`docs/addon-api-compatibility.md`](docs/addon-api-compatibility.md).

This API provides functionality for interacting with geographical regions in a Minecraft server world,
which allows extension mods to enrich and build features based on defined regions.

### Player Interaction API

Handles player-triggered actions related to regions and their scopes.

#### Functions:
- `startSelection(player: ServerPlayerEntity, shapeType: GeoShapeType? = null)`  
  Starts selection mode for the player. Optionally sets a shape hint.

- `stopSelection(player: ServerPlayerEntity)`  
  Stops selection mode for the player.

- `resetSelection(player: ServerPlayerEntity, shapeType: GeoShapeType? = null)`  
  Resets the player's selected positions. Optionally updates the shape hint.

- `setSelectionShape(player: ServerPlayerEntity, shapeType: GeoShapeType)`  
  Changes the shape hint for the current selection. Fails if in scope-modification mode.

- `startSelectionForModify(player: ServerPlayerEntity, scope: GeoScope)`  
  Starts selection mode locked to scope-modification for the given scope.

- `createRegion(player: ServerPlayerEntity, name: String?, shapeTypeName: String? = null, idMark: Int = 0)`  
  Creates a region with an optional name, shape, and ID marker. `idMark` must be from `0` to `9`. If shapeTypeName is null, shape is inferred from selection state.

- `createAndGetRegion(player: ServerPlayerEntity, name: String?, shapeTypeName: String? = null, idMark: Int = 0)`  
  Creates a region and returns the created region.

- `deleteRegion(player: ServerPlayerEntity, region: Region)`  
  Deletes a specified region.

- `renameRegion(player: ServerPlayerEntity, region: Region, newName: String)`  
  Renames a region.

- `addScope(player: ServerPlayerEntity, region: Region, name: String?, shapeTypeName: String?)`  
  Adds a scope to a region with an optional name and shape.

- `createAndGetRegionScopePair(player: ServerPlayerEntity, region: Region, name: String?, shapeTypeName: String?)`  
  Creates a scope for a region and returns the region-scope pair.

- `deleteScope(player: ServerPlayerEntity, region: Region, scopeName: String)`  
  Deletes a scope from a region.

- `renameScope(player: ServerPlayerEntity, region: Region, oldName: String, newName: String)`  
  Renames a scope within a region.

- `transferScope(player: ServerPlayerEntity, sourceRegion: Region, scopeName: String, targetRegion: Region)`  
  Transfers a scope from one region to another. If a scope with the same name already exists in the target region, the scope is automatically renamed by appending a numeric suffix.

- `mergeRegion(player: ServerPlayerEntity, sourceRegion: Region, targetRegion: Region)`  
  Merges one region into another: all scopes are moved to the target (with automatic renaming on conflict). The source region's overall settings are discarded. The source region is deleted afterward.

- `addTeleportPoint(player: ServerPlayer, targetRegion: Region, scope: GeoScope, x: Int, y: Int, z: Int)`
  Adds a teleport point with a given location.

- `addTeleportPoint(player: ServerPlayer, targetRegion: Region, scope: GeoScope)`
  Adds a teleport point at the player's current location.

- `resetTeleportPoint(player: ServerPlayer, region: Region, scope: GeoScope)`
  Resets the teleport point of a scope.

- `getTeleportPoint(scope: GeoScope)`
  Retrieves the teleport point of a scope.

- `toggleTeleportPointAccessibility(player: ServerPlayer, region: Region, scope: GeoScope)`
  Toggles the access permission of a scope's teleport point and persists the change. The scope must belong to the supplied region.

- `teleportPlayerToScope(player: ServerPlayer, targetRegion: Region, scope: GeoScope)`
  Teleports a player only when the scope's teleport point is publicly accessible.

- `teleportPlayerToScopeAsAdministrator(player: ServerPlayer, targetRegion: Region, scope: GeoScope)`
  Bypasses teleport-point accessibility for administrative use. Ownership, dimension, and physical safety checks still apply.

- `modifyScope(player: ServerPlayerEntity, region: Region, scopeName: String)`  
  Modifies the properties of a scope.

- `replaceScopeShape(player: ServerPlayerEntity, region: Region, scope: GeoScope, newShape: GeoShape)`
  Replaces a live scope's geometry with an immutable shape of the same type. The operation validates
  ownership, configured size limits, and intersections, persists the change, and restores the old
  shape if saving fails.

- `addSettingRegion(player: ServerPlayerEntity, region: Region, keyString: String, valueString: String?, targetPlayerStr: String?)`  
  Adds a setting to a region.

- `addSettingScope(player: ServerPlayerEntity, region: Region, scopeName: String, keyString: String, valueString: String?, targetPlayerStr: String?)`  
  Adds a setting to a scope within a region.

- `removeSettingRegion(player: ServerPlayerEntity, region: Region, keyString: String, targetPlayerStr: String?)`  
  Removes a setting from a region.

- `removeSettingScope(player: ServerPlayerEntity, region: Region, scopeName: String, keyString: String, targetPlayerStr: String?)`  
  Removes a setting from a scope within a region.

- `getPermissionValueRegion(player: ServerPlayerEntity, region: Region?, scopeName: String? , targetPlayerNameStr: String?, keyString: String)`
  Retrieves the permission value of a setting.

- `getRuleValueRegion(region: Region?, keyString: String): Boolean?`
  Retrieves the explicitly set rule value for a region. Returns `null` if the rule is not explicitly set.

- `getRuleValueScope(region: Region?, scopeName: String, keyString: String): Boolean?`
  Retrieves the explicitly set rule value for a specific scope within a region. Returns `null` if the rule is not explicitly set.

- `addEntryExitSettingRegion(player: ServerPlayerEntity, region: Region, keyString: String, valueString: String?)`  
  Adds an entry-exit setting to a region (`ENTER_ENABLED`, `EXIT_ENABLED`, `ENTER_MESSAGE`, or `EXIT_MESSAGE`).

- `addEntryExitSettingScope(player: ServerPlayerEntity, region: Region, scopeName: String, keyString: String, valueString: String?)`  
  Adds an entry-exit setting to a scope within a region.

- `removeEntryExitSettingRegion(player: ServerPlayerEntity, region: Region, keyString: String)`  
  Removes an entry-exit setting from a region.

- `removeEntryExitSettingScope(player: ServerPlayerEntity, region: Region, scopeName: String, keyString: String)`  
  Removes an entry-exit setting from a scope within a region.

- `queryRegionInfo(player: ServerPlayerEntity, region: Region)`  
  Queries detailed information about a region.

- `queryRegionNaturalStats(player: ServerPlayerEntity, region: Region, categoryName: String? = null)`  
  Queries region statistics. `categoryName` accepts `all`, `structures`, `difficulty`, `surface`, `biomes`, or `players`. The `players` category returns the region's persistent aggregated player statistics.

- `queryRegionPlayerStats(player: ServerPlayerEntity, region: Region)`  
  Queries the same persistent aggregated player statistics as `queryRegionNaturalStats(..., "players")`.

- `toggleActionBar(player: ServerPlayerEntity)`
  Toggles the action bar display for regions for the player. When enabling, scopes with a shape in the player's current world have their boundaries immediately rendered using a bounded best-effort visual effect. Very large or numerous boundaries are sampled within one fixed per-player render budget. Scope boundaries continue to be rendered while the player is in selection mode; if the player is modifying a specific scope, that scope's boundary is rendered in orange and other scope boundaries use any remaining budget.

- `estimateRegionArea(player: ServerPlayerEntity, shapeTypeName: String, customPositions: List<BlockPos>? = null)`  
  Estimates the area of a region to be created based on selected points and shape type.  
  Returns `AreaEstimationResult` with either the estimated area or an error if the configuration is invalid.  
  If `customPositions` is null, uses the player's current selected positions.

- `estimateScopeAreaChange(player: ServerPlayerEntity, region: Region, scopeName: String, customPositions: List<BlockPos>? = null)`  
  Estimates the area change (delta) when modifying an existing scope.  
  Returns `AreaEstimationResult` with the area change value (positive for increase, negative for decrease) or an error.  
  If `customPositions` is null, uses the player's current selected positions.

---

### Region Data API

Provides access to region data and database operations for extension functions.

- `registerExtensionPermissionKey(key: String, defaultValue: Boolean = true)`  
  Registers a namespaced boolean extension permission key such as `adventure:anchor_use`.

- `registerExtensionRuleKey(key: String, defaultValue: Boolean = true)`  
  Registers a namespaced boolean extension rule key.

- `getRegisteredExtensionPermissionKeys(): List<String>`  
  Returns all currently registered extension permission keys.

- `getRegisteredExtensionRuleKeys(): List<String>`  
  Returns all currently registered extension rule keys.

- `getRegion(id: Int): Region?`  
  Retrieves a region by its numeric ID.

- `getRegionByName(name: String): Region?`  
  Retrieves a region by its name. Returns `null` if no region with the given name exists.

- `getRegionList(): List<Region>`  
  Retrieves the list of all regions.

- `getRegionListFiltered(idMark: Int): List<Region>`  
  Retrieves a filtered list of regions based on an ID mark from `0` to `9`. Other values fail fast.

- `getRegionFoundingTime(region: Region): Long`  
  Gets the founding time of a region.

- `getRegionAge(region: Region): Long`  
  Returns the elapsed time in milliseconds since the region was created. Note that the precision is 1 hour due to the encoding of the founding time in the region ID.

- `getRegionScopes(region: Region): List<GeoScope>`  
  Retrieves the list of scopes within a region.

- `getRegionScopePair(region: Region, scopeName: String): Pair<Region, GeoScope?>`  
  Retrieves the region-scope pair based on the region and scope name.

- `getRegionScopePair(regionId: Int, scopeName: String): Pair<Region?, GeoScope?>`  
  Retrieves the region-scope pair by region ID and scope name.

- `getRegionScopePairByLocation(world: World, x: Int, z: Int): Pair<Region, GeoScope>?`  
  Retrieves the region-scope pair in a world by coordinates.

- `getRegionScopePairByLocation(world: World, blockPos: BlockPos): Pair<Region, GeoScope>?`  
  Retrieves the region-scope pair in a world by block position.

- `inquireTeleportPointAccessibility(scope: GeoScope)`
  Inquires the access permission of a scope's teleport point.

- `getScopeTeleportPoint(scope: GeoScope): BlockPos?`  
  Retrieves the teleport point coordinates of a scope. Returns `null` if no teleport point is set.

- `getScopeShape(scope: GeoScope): Region.Companion.GeoShape?`  
  Retrieves the shape of a scope.

- `getScopeArea(scope: GeoScope): Double?`  
  Retrieves the area of a scope.

- `getRegionArea(region: Region): Double`  
  Calculates the total area of a region.

- `getRegionGlobalSettings(region: Region): List<Setting>`  
  Retrieves the global settings for a region.

- `getRegionGlobalSettingsByType(region: Region, settingTypes: SettingTypes): List<Setting>`  
  Retrieves the global settings by type for a region.

- `getRegionPersonalSettings(region: Region, playerUUID: UUID): List<Setting>`  
  Retrieves the personal settings for a region for a specific playerUUID.

- `getRegionPersonalSettingsByType(region: Region, playerUUID: UUID, settingTypes: SettingTypes): List<Setting>`  
  Retrieves the personal settings by type for a region for a specific playerUUID.

- `getScopeGlobalSettings(scope: GeoScope): List<Setting>`  
  Retrieves the global settings for a scope.

- `getScopeGlobalSettingsByType(scope: GeoScope, settingTypes: SettingTypes): List<Setting>`  
  Retrieves the global settings by type for a scope.

- `getScopePersonalSettings(scope: GeoScope, playerUUID: UUID): List<Setting>`  
  Retrieves the personal settings for a scope for a specific playerUUID.

- `getScopePersonalSettingsByType(scope: GeoScope, playerUUID: UUID, settingTypes: SettingTypes): List<Setting>`  
  Retrieves the personal settings by type for a scope for a specific playerUUID.

- `getPermissionValueRegion(region: Region?, scope: GeoScope?, playerUUID: UUID?, permissionKey: PermissionKey): Boolean`
  Retrieves the permission value of a setting for a region and scope.

- `getExtensionPermissionValueRegion(region: Region?, scope: GeoScope?, playerUUID: UUID?, key: String): Boolean`
  Retrieves the effective value of a registered namespaced extension permission key.

- `getRuleValueForRegion(region: Region?, scope: GeoScope?, ruleKey: RuleKey): Boolean`
  Retrieves the effective rule value for a region and optional scope. Returns the config default if the rule is not explicitly set.

- `getExtensionRuleValueForRegion(region: Region?, scope: GeoScope?, key: String): Boolean`
  Retrieves the effective value of a registered namespaced extension rule key.

- `getEffectValueForRegion(region: Region?, scope: GeoScope?, playerUUID: UUID, effectKey: EffectKey): Int?`  
  Retrieves the resolved effect amplifier for a specific player and effect key, considering scope and region settings in priority order. Returns `null` if the effect is not set.

- `getActiveEffectsForRegion(region: Region, scope: GeoScope?, playerUUID: UUID): Map<EffectKey, Int>`  
  Retrieves a map of all active effects and their amplifiers for a specific player in a region and optional scope.

- `getRegionScopeCount(region: Region): Int`  
  Returns the number of scopes in a region.

- `getRegionNaturalStats(server: MinecraftServer, region: Region): RegionNaturalStatsResult`  
  Retrieves natural statistics for a region, including structure counts, area-weighted average local difficulty, surface block counts, biome counts, and per-dimension breakdowns.

- `getScopeNaturalStats(server: MinecraftServer, scope: GeoScope): RegionNaturalStatsResult`  
  Retrieves the same natural statistics for a single scope.

- `getRegionPlayerStats(region: Region): RegionPlayerStats`  
  Retrieves the region's persistent aggregated player statistics, including tracked player count, entry count, stay duration, death count, block place count, and block break count.

`RegionNaturalStatsResult` has three variants:

1. `Success(stats: RegionNaturalStats)` for a completed scan.
2. `ChunkLimitExceeded(dimensionId, candidateChunkCount, limit)` when the region's candidate chunk window exceeds the current hard limit.
3. `DimensionUnavailable(dimensionId)` when the target dimension cannot be resolved from the running server.

`RegionNaturalStats` reports loaded chunk count, candidate chunk count, sampled surface-column count, aggregate structure counts, aggregate surface block counts, aggregate biome counts, area-weighted average local difficulty, and per-dimension `DimensionNaturalStats` entries. Surface block counting samples the top non-air block of each in-region column. Average local difficulty is weighted by sampled column count, and each chunk now samples an actual in-region column so edge-only coverage does not pull difficulty from outside the region.

`RegionPlayerStats` reports persistent cumulative player activity per region. Entry counting is driven by confirmed region transitions, stay duration is accumulated over time and flushed on disconnect/server stop, deaths are counted at the player's death position, and block place/break counts only record successful actions inside the region.

- `getRegionEntryExitToggle(region: Region): Boolean`  
  Returns whether entry-exit notifications are enabled for a region. Defaults to `true` if not set.

- `getRegionEntryExitMessage(region: Region, key: EntryExitMessageKey): String?`  
  Retrieves the entry-exit message for a region. Returns `null` if not set.

- `getScopeEntryExitToggle(scope: GeoScope): Boolean`  
  Returns whether entry-exit notifications are enabled for a scope. Defaults to `true` if not set.

- `getScopeEntryExitMessage(scope: GeoScope, key: EntryExitMessageKey): String?`  
  Retrieves the entry-exit message for a scope. Returns `null` if not set.

### UtilApi

Provides utility functions for region data to improve usability for extension mods.

- `isSelectingPoints(playerExecutor: ServerPlayerEntity): Boolean`  
  Checks if the player is in selection mode.

- `isActionBarEnabled(playerExecutor: ServerPlayerEntity): Boolean`
  Checks if the action bar display is enabled for the player.

- `getPlayerUUID(server: MinecraftServer, playerName: String): UUID?`  
  Retrieves the UUID of a player by their name.

- `getPlayerUUID(player: ServerPlayerEntity, playerName: String): UUID?`
  Retrieves the UUID of a player by their name using a player entity.

- `getPlayerName(server: MinecraftServer, uuid: UUID?): String`
  Retrieves the player name by their UUID.

- `getPlayerName(player: ServerPlayerEntity, uuid: UUID?): String`
  Retrieves the player name by their UUID using a player entity.

- `getPlayer(playerExecutor: ServerPlayerEntity, playerName: String): ServerPlayerEntity?`
  Retrieves the player entity by their name.

- `getPlayer(server: MinecraftServer, playerName: String): ServerPlayerEntity?`
  Retrieves the player entity by their name using the server instance.

- `getPlayer(playerExecutor: ServerPlayerEntity, playerUuid: UUID): ServerPlayerEntity?`
  Retrieves the player entity by their UUID.

- `getPlayer(server: MinecraftServer, playerUuid: UUID): ServerPlayerEntity?`
  Retrieves the player entity by their UUID using the server instance.

- `getPlayerProfile(server: MinecraftServer, playerName: String): GameProfile?`
  Retrieves the GameProfile of a player by their name.

- `getPlayerProfile(playerExecutor: ServerPlayerEntity, playerName: String): GameProfile?`
  Retrieves the GameProfile of a player by their name using a player entity to get server.

- `getPlayerProfile(server: MinecraftServer, playerUuid: UUID): GameProfile?`
  Retrieves the GameProfile of a player by their UUID.

- `getPlayerProfile(playerExecutor: ServerPlayerEntity, playerUuid: UUID): GameProfile?`
  Retrieves the GameProfile of a player by their UUID using a player entity to get server.

- `parseRegionFoundingTime(regionNumberId: Int): Long`
  Parses and retrieves the founding time of a region by its numeric ID.

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

- `/imyvmWorldGeo addScope [shapeType] <regionIdentifier> [scopeName]`  
  Add a new scope to the region. If shapeType is omitted, the shape is inferred from the selection state. Optionally provide a scope name.

- `/imyvmWorldGeo deleteScope <regionIdentifier> <scopeName>`  
  Delete a scope from a region.

- `/imyvmWorldGeo transferScope <regionIdentifier> <scopeName> <targetRegionIdentifier>`  
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

- `/imyvmWorldGeo modifyScope <regionIdentifier> <scopeName> [newName]`  
  Modify a scope's properties or rename it.

- `/imyvmWorldGeo setting add <regionIdentifier> <key> <value> [playerName]`  
  Add a setting to a region, optionally for a specific player.  
  Entry-exit keys (`ENTER_ENABLED`, `EXIT_ENABLED`, `ENTER_MESSAGE`, `EXIT_MESSAGE`) do not support personal player assignment.

- `/imyvmWorldGeo setting remove <regionIdentifier> <key> [playerName]`  
  Remove a setting from a region, optionally for a specific player.

- `/imyvmWorldGeo setting queryValue <regionIdentifier> <key> [playerName]`  
  Query the value of a setting in a region, optionally for a specific player.

- `/imyvmWorldGeo settingScope add <regionIdentifier> <scopeName> <key> <value> [playerName]`  
  Add a setting to a specific scope of a region, optionally for a specific player.

- `/imyvmWorldGeo settingScope remove <regionIdentifier> <scopeName> <key> [playerName]`  
  Remove a setting from a specific scope, optionally for a specific player.

- `/imyvmWorldGeo settingScope queryValue <regionIdentifier> <scopeName> <key> [playerName]`  
  Query the value of a setting in a specific scope, optionally for a specific player.

- `/imyvmWorldGeo dynmapToggle <regionIdentifier>`  
  Toggle the region's visibility on the dynamic map. When a region is hidden, all its scopes are hidden regardless of their individual settings.

- `/imyvmWorldGeo dynmapToggleScope <regionIdentifier> <scopeName>`  
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
