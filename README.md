# IMYVMWorldGeo 1.21 1.0.2

## Changelog 1.0.2

This is a version with new UtilApi features.
- API: Add UtilApi to improve usability of region data.
- Region Behavior: handle a situation of empty newName when rename a Region.
- Refactor: Improve the readability of code.

## Introduction

This is a mod to provide a geography system framework for Imyvm server players and groups, which is a basement for jungle system, community system and so on.

## Environment Requirements

This mod is **server-side only** and requires the following environment:

- **Minecraft Version:** 1.21  
- **Fabric Loader Version:** 0.16.9 (or compatible with Minecraft 1.21)  
- **Fabric API:** Required  
- **Fabric Kotlin:** Required  
- **Java Version:** 21  
- **IMYVM Hoki Mod Version:** 1.1.4

> Note: Client-side players do not need to install this mod, but the server must meet these requirements.

---

## Installation

1. **Download the mod jar**  
   Download the latest `imyvm-world-geo` jar file from the release page or build it from source.

2. **Install Fabric Loader on your server**  
   Ensure the server runs Fabric Loader compatible with Minecraft 1.21.

3. **Install Fabric API and Fabric Kotlin**  
   Download the corresponding Fabric API and Fabric Kotlin jars and place them into the server's `mods` folder:
   - [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)  
   - [Fabric Kotlin](https://www.curseforge.com/minecraft/mc-mods/fabric-language-kotlin)

4. **Install Imyvm Hoki mod**  
   This mod depends on Imyvm fork of Hoki, another ImyvmCircle project. Download the latest Hoki jar from [GitHub](https://github.com/ImyvmCircle/Hoki) and place it in the server's `mods` folder.

5. **Add Imyvm World Geo mod jar**  
   Place the `imyvm-world-geo` jar into the server's `mods` folder. 

6. **Start the server**  
   Run the server normally. The mod will automatically load along with its dependencies.

---

## Usage

- This mod provides region and scope management commands for server. All commands are executed on the server side and require the player to have the appropriate permissions.  
- For extensions, apis are provided in `src/main/kotlin/com/imyvm/iwg/inter/api` folder.

> For detailed commands and API usage, see the `Command` and `API` sections in this README.

## API Documentation

This API provides functionality for interacting with geographical regions in your Minecraft world. It allows extension mods to enrich and build features based on defined regions.

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

- `queryRegionInfo(player: ServerPlayerEntity, region: Region)`  
  Queries detailed information about a region.

---

### Region Data API

Provides access to region data and database operations for extension functions.

#### Functions:
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

- `getRegionScopePairByLocation(x: Int, z: Int): Pair<Region, GeoScope>?`  
  Retrieves the region-scope pair by coordinates.

- `getRegionScopePairByLocation(blockPos: BlockPos): Pair<Region, GeoScope>?`  
  Retrieves the region-scope pair by block position.

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

- `/imyvm-world-geo addscope <shapeType> <regionIdentifier> [scopeName]`  
  Add a new scope to the region with the given shape and optional scope name.

- `/imyvm-world-geo deletescope <regionIdentifier> <scopeName>`  
  Delete a scope from a region.

- `/imyvm-world-geo modifyscope <regionIdentifier> <scopeName> [newName]`  
  Modify a scope's properties or rename it.

- `/imyvm-world-geo setting add <regionIdentifier> <settingType> <key> <value> [playerName]`  
  Add a setting to a region, optionally for a specific player.

- `/imyvm-world-geo setting remove <regionIdentifier> <settingType> <key> [playerName]`  
  Remove a setting from a region, optionally for a specific player.

- `/imyvm-world-geo settingscope add <regionIdentifier> <scopeName> <settingType> <key> <value> [playerName]`  
  Add a setting to a specific scope of a region, optionally for a specific player.

- `/imyvm-world-geo settingscope remove <regionIdentifier> <scopeName> <settingType> <key> [playerName]`  
  Remove a setting from a specific scope, optionally for a specific player.

- `/imyvm-world-geo query <regionIdentifier>`  
  Show detailed information about a region.

- `/imyvm-world-geo list`  
  List all regions.

- `/imyvm-world-geo toggle`  
  Toggle the action bar display for regions.

- `/imyvm-world-geo help`  
  Show this help message.
