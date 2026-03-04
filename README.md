# IMYVMWorldGeo 1.21 1.3.0

## Changelog

### 1.3.x

This major version (1.3.x) focuses on perfection of current system.

#### 1.3.0

This version includes the following changes:

- feat: Added `ENDERMAN_BLOCK_PICKUP` rule to control whether endermen can pick up blocks within a region or scope (default: true).
- feat: Added `SCULK_SPREAD` rule to control whether sculk can spread within a region or scope (default: true).
- feat: Added `SNOW_GOLEM_TRAIL` rule to control whether snow golems leave snow trails within a region or scope (default: true).
- feat: Added `DISPENSER` rule to block dispenser output from affecting a region's interior, including dispensers placed outside the region (default: true).
- feat: Added `PRESSURE_PLATE` rule to prevent pressure plates inside a region from being activated (default: true).
- feat: Added `PISTON` rule to block pistons from pushing or breaking blocks inside a region, including pistons placed outside the region (default: true).

## Introduction

This is a mod to provide a geography system framework for Imyvm server players and groups, 
and is also a basement for extensions, exemplified by jungle system, community system and so on.

## Environment Requirements

This mod is **server-side only** and requires the following environment:

- **Minecraft Version:** 1.21  
- **Fabric Loader Version:** 0.16.9 (or compatible with Minecraft 1.21)
- **Java Version:** 21  
- **IMYVM Hoki Mod Version:** 1.1.4

> Note: Client-side players do not need to install this mod, but the server must meet these requirements.

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
A shape can be of `RECTANGLE`, `CIRCLE` or `POLYGON` type, and `shapeParameters` parsed by corresponding type.

### Setting

Settings are key-value pairs associated with regions or scopes,
which can be either global (applicable to all players) or personal (specific to individual players).
When there is a conflict between regional and scope settings, scope settings take precedence,
and personal settings are of higher priority when the corresponding global settings are added.
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

#### Rule Keys

Rules control server-side gameplay mechanics within a region or scope. Unlike permissions, rules are not player-specific and have no parent-child hierarchy. The effective value follows the priority: scope setting → region setting → config default.

| Key | Default | Description |
|-----|---------|-------------|
| SPAWN_MONSTERS | true | Controls whether hostile monsters (SpawnGroup MONSTER) spawn in the region. |
| SPAWN_PHANTOMS | true | Controls whether phantoms spawn in the region (overrides SPAWN_MONSTERS for phantoms). |
| TNT_BLOCK_PROTECTION | false | When set to true, explosions from TNT, TNT minecarts, end crystals, beds used in the End, respawn anchors used outside the Nether, wither spawning, and wither skulls do not destroy blocks inside the region. Blocks outside protected regions are still destroyed normally. Entity damage and knockback from the explosion are unaffected. |
| ENDERMAN_BLOCK_PICKUP | true | Controls whether endermen can pick up blocks inside the region. When set to false, the enderman PickUpBlockGoal is suppressed for the region. |
| SCULK_SPREAD | true | Controls whether sculk can spread inside the region. When set to false, the sculk catalyst tick is suppressed, preventing all sculk spread within the region. |
| SNOW_GOLEM_TRAIL | true | Controls whether snow golems leave snow trails inside the region. When set to false, the setBlockState call for snow placement is suppressed. |
| DISPENSER | true | Controls whether dispensers can fire into the region. When set to false, any dispenser whose output face points into the region is blocked, including dispensers placed outside the region. |
| PRESSURE_PLATE | true | Controls whether pressure plates inside the region can be activated. When set to false, entity collision with pressure plates in the region is suppressed. |
| PISTON | true | Controls whether pistons can push or break blocks inside the region. When set to false, any piston move that would affect a block inside the region is cancelled, including pistons placed outside the region. |

#### Effect Keys

Effect settings apply Minecraft status effects to players inside a region or scope. The value is the effect amplifier (0-indexed: 0 = Level 1, 1 = Level 2, etc.). Effects are refreshed every lazy ticker interval and last for `effect.duration_seconds` (default 5 s) after each application. Effect settings can be global or personal. The effective value follows the priority: scope personal → scope global → region personal → region global.

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
When teleportation is requested, the safety of the stored teleport point is rechecked against the current world state.
If the point is no longer safe (e.g., due to subsequent block changes), the system searches a 5x5x5 cube
centered on the original point (with height priority, meaning positions at the same vertical distance are
checked before moving further away) for the nearest safe alternative. If a safe alternative is found,
the teleport point is automatically updated and the player is teleported there with a warning message.
If no safe alternative is found within the search area, teleportation is cancelled and the player is informed.

---

## Usage

- This mod provides region and scope management commands for server. All commands are executed on the server side and require the player to have the appropriate permissions.  
- For extensions, apis are provided in `src/main/kotlin/com/imyvm/iwg/inter/api` folder.

> For detailed commands and API usage, see the `Command` and `API` sections in this README.

## API Documentation

This API provides functionality for interacting with geographical regions in a Minecraft server world,
which allows extension mods to enrich and build features based on defined regions.

### Player Interaction API

Handles player-triggered actions related to regions and their scopes.

#### Functions:
- `startSelection(player: ServerPlayerEntity)`  
  Starts selection mode for the player.

- `stopSelection(player: ServerPlayerEntity)`  
  Stops selection mode for the player.

- `resetSelection(player: ServerPlayerEntity)`  
  Resets the player's selected positions.

- `createRegion(player: ServerPlayerEntity, name: String?, shapeTypeName: String?, idMark: Int = 0)`  
  Creates a region with an optional name, shape, and ID marker.

- `createAndGetRegion(player: ServerPlayerEntity, name: String?, shapeTypeName: String?, idMark: Int = 0)`  
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

- `addTeleportPoint(player: ServerPlayerEntity, targetRegion: Region, scope: GeoScope, x: Int, y: Int, z: Int)`
  Adds a teleport point with a given location.

- `addTeleportPoint(player: ServerPlayerEntity, targetRegion: Region, scope: GeoScope)`
  Adds a teleport point at the player's current location.

- `resetTeleportPoint(player: ServerPlayerEntity, region: Region, scope: GeoScope)`
  Resets the teleport point of a scope.

- `getTeleportPoint(scope: GeoScope)`
  Retrieves the teleport point of a scope.

- `toggleTeleportPointAccessibility(scope: GeoScope)`
 Toggles the access permission of a scope's teleport point.

- `teleportPlayerToScope(player: ServerPlayerEntity, targetRegion: Region, scope: GeoScope)`
  Teleports a player to the teleport point of a scope.

- `modifyScope(player: ServerPlayerEntity, region: Region, scopeName: String)`  
  Modifies the properties of a scope.

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

- `toggleActionBar(player: ServerPlayerEntity)`
  Toggles the action bar display for regions for the player.

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

- `getRegion(id: Int): Region?`  
  Retrieves a region by its numeric ID.

- `getRegionList(): List<Region>`  
  Retrieves the list of all regions.

- `getRegionListFiltered(idMark: Int): List<Region>`  
  Retrieves a filtered list of regions based on ID mark.

- `getRegionFoundingTime(region: Region): Long`  
  Gets the founding time of a region.

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

- `getRuleValueForRegion(region: Region?, scope: GeoScope?, ruleKey: RuleKey): Boolean`
  Retrieves the effective rule value for a region and optional scope. Returns the config default if the rule is not explicitly set.

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

- `/imyvmWorldGeo select start`  
  Start selecting positions with a command block. Right-click a block to add a point; left-click (on any block or in air) to undo the last point.

- `/imyvmWorldGeo select stop`  
  Stop selection mode.

- `/imyvmWorldGeo select reset`  
  Clear all selected points but keep selection mode active.

- `/imyvmWorldGeo create <shapeType> [name]`  
  Create a region of the given shape (rectangle, circle, polygon) from selected positions.  
  Optionally give it a name.

- `/imyvmWorldGeo delete <regionIdentifier>`  
  Delete a region by its ID or name.

- `/imyvmWorldGeo rename <regionIdentifier> <newName>`  
  Rename an existing region.

- `/imyvmWorldGeo addScope <shapeType> <regionIdentifier> [scopeName]`  
  Add a new scope to the region with the given shape and optional scope name.

- `/imyvmWorldGeo deleteScope <regionIdentifier> <scopeName>`  
  Delete a scope from a region.

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

- `/imyvmWorldGeo teleport <regionIdentifier> <scopeName>`  
  Teleport the player to the teleport point of the specified region and scope.

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

- `/imyvmWorldGeo query <regionIdentifier>`  
  Show detailed information about a region.

- `/imyvmWorldGeo list`  
  List all regions.

- `/imyvmWorldGeo toggle`  
  Toggle the action bar display for regions.

- `/imyvmWorldGeo help`  
  Show the help message.

## Acknowledgements

Were it not for the support of IMYVM fellows and players, this project would not have been possible.