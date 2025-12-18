# IMYVMWorldGeo 1.21 1.1.1

## Changelog 1.1.1

This release is a minor patch fixing the wrong api and implement.
- Fix: Correct the wrong API with a new getPermissionValueRegion api in RegionDataApi.

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

- `resetTeleportPoint(scope: GeoScope)`
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

- `getPermissionValueRegion(player: ServerPlayerEntity, region: Region, scopeName: String? , targetPlayerStr: String?, keyString: String)`
  Retrieves the permission value of a setting.

- `queryRegionInfo(player: ServerPlayerEntity, region: Region)`  
  Queries detailed information about a region.

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

- `getPermissionValueRegion(region: Region, scope: GeoScope?, playerUUID: UUID?, permissionKey: PermissionKey): Boolean`
  Retrieves the permission value of a setting for a region and scope.

### UtilApi

Provides utility functions for region data to improve usability for extension mods.

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

- `/imyvm-world-geo select start`  
  Start selecting positions with a command block.

- `/imyvm-world-geo select stop`  
  Stop selection mode.

- `/imyvm-world-geo select reset`  
  Clear all selected points but keep selection mode active.

- `/imyvm-world-geo create <shapeType> [name]`  
  Create a region of the given shape (rectangle, circle, polygon) from selected positions.  
  Optionally give it a name.

- `/imyvm-world-geo delete <regionIdentifier>`  
  Delete a region by its ID or name.

- `/imyvm-world-geo rename <regionIdentifier> <newName>`  
  Rename an existing region.

- `/imyvm-world-geo add-scope <shapeType> <regionIdentifier> [scopeName]`  
  Add a new scope to the region with the given shape and optional scope name.

- `/imyvm-world-geo delete-scope <regionIdentifier> <scopeName>`  
  Delete a scope from a region.

- `/imyvm-world-geo teleport-point set [x] [y] [z]`  
  Set the teleport point for the current region and scope.
  - If `x`, `y`, and `z` are provided and valid, the teleport point will be set to the specified coordinates.
    - The adjective 'valid' means they are all numbers or '~', which represents the player's current coordinate on that axis, 
      and it satisfies criteria for a safe teleport point physically.
  - If `x`, `y`, and `z` are omitted or invalid, the teleport point will default to the player's current position.

- `/imyvm-world-geo teleport-point reset <regionIdentifier> <scopeName>`  
  Reset the teleport point for the specified region and scope.
  - If `regionIdentifier` and `scopeName` are provided and valid, the teleport point for the specified scope will be reset.
  - If `regionIdentifier` and `scopeName` are omitted or invalid, the teleport point for the scope the player is currently in will be reset.
    - If player is not in any scope, an error message will be shown.

- `/imyvm-world-geo teleport-point inquiry <regionIdentifier> <scopeName>`  
  Inquire about the teleport point for the specified region and scope.
  - If `regionIdentifier` and `scopeName` are provided and valid, the teleport point for the specified scope will be displayed.
  - If `regionIdentifier` and `scopeName` are omitted or invalid, the teleport point for the scope the player is currently in will be displayed.
    - If player is not in any scope, an error message will be shown.

- `/imyvm-world-geo (teleport-point) teleport <regionIdentifier> <scopeName>`  
  Teleport the player to the teleport point of the specified region and scope.

- `/imyvm-world-geo teleport-point toggle <regionIdentifier> <scopeName>`  
  Toggle the accessibility of the teleport point for the specified region and scope.

- `/imyvm-world-geo modify-scope <regionIdentifier> <scopeName> [newName]`  
  Modify a scope's properties or rename it.

- `/imyvm-world-geo setting add <regionIdentifier> <settingType> <key> <value> [playerName]`  
  Add a setting to a region, optionally for a specific player.

- `/imyvm-world-geo setting remove <regionIdentifier> <settingType> <key> [playerName]`  
  Remove a setting from a region, optionally for a specific player.

- `/imyvm-world-geo setting-scope add <regionIdentifier> <scopeName> <settingType> <key> <value> [playerName]`  
  Add a setting to a specific scope of a region, optionally for a specific player.

- `/imyvm-world-geo setting-scope remove <regionIdentifier> <scopeName> <settingType> <key> [playerName]`  
  Remove a setting from a specific scope, optionally for a specific player.

- `/imyvm-world-geo query <regionIdentifier>`  
  Show detailed information about a region.

- `/imyvm-world-geo list`  
  List all regions.

- `/imyvm-world-geo toggle`  
  Toggle the action bar display for regions.

- `/imyvm-world-geo help`  
  Show this help message.

## Acknowledgements

Were it not for the support of IMYVM fellows and players, this project would not have been possible.