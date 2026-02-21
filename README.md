# IMYVMWorldGeo 1.21 1.2.1

## Changelog

### Version 1.2 Series - Settings System Enhancement

This major version (1.2.x) focuses on enriching and improving the settings system.

#### 1.2.1

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
  Start selecting positions with a command block.

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