# IMYVMWorldGeo 1.21 1.0.0

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
   This mod depends on Imyvm fork of  Hoki, another ImyvmCircle project. Download the latest Hoki jar from [GitHub](https://github.com/ImyvmCircle/Hoki) and place it in the server's `mods` folder.

5. **Add Imyvm World Geo mod jar**  
   Place the `imyvm-world-geo` jar into the server's `mods` folder. 

6. **Start the server**  
   Run the server normally. The mod will automatically load along with its dependencies.

---

## Usage

- This mod provides region and scope management commands for server. All commands are executed on the server side and require the player to have the appropriate permissions.  
- For extensions, apis are provided in `src/main/kotlin/com/imyvm/iwg/inter/api` folder.

> For detailed commands and API usage, see the `Command` and `API` sections in this README.

## API

APIs allow extension mods to build and enrich functions base on geography regions defined here.

### Player Interaction API
_Handles player-triggered actions related to regions and scopes._

- `startSelection(player)` – Start selection mode for the player.  
- `stopSelection(player)` – Stop selection mode.  
- `resetSelection(player)` – Reset selected positions.  
- `createRegion(player, name, shapeTypeName)` – Create a region with optional name and shape.  
- `deleteRegion(player, region)` – Delete a specified region.  
- `renameRegion(player, region, newName)` – Rename a region.  
- `addScope(player, region, name, shapeTypeName)` – Add a scope to a region with optional name and shape.  
- `deleteScope(player, region, scopeName)` – Delete a scope from a region.  
- `renameScope(player, region, oldName, newName)` – Rename a scope within a region.  
- `modifyScope(player, region, scopeName)` – Modify a scope’s properties.  
- `addSettingRegion(player, region, keyString, valueString, targetPlayerStr)` – Add a setting to a region.  
- `addSettingScope(player, region, scopeName, keyString, valueString, targetPlayerStr)` – Add a setting to a specific scope.  
- `removeSettingRegion(player, region, keyString, targetPlayerStr)` – Remove a setting from a region.  
- `removeSettingScope(player, region, scopeName, keyString, targetPlayerStr)` – Remove a setting from a specific scope.  
- `queryRegionInfo(player, region)` – Query detailed information about a region.  

---

### Region Data API
_Provides access to region data and database operations, which make it convenient for extension functions._

- `addRegion(region)` – Add a new region to the database.  
- `removeRegion(region)` – Remove a region from the database.  
- `renameRegion(region, newName)` – Rename a region in the database.  
- `getRegionList()` – Retrieve the list of all regions.  
- `getRegionById(id)` – Get a region by its numeric ID.  
- `getRegionScopePairByLocation(x, z)` – Get region and scope at specified coordinates.  
- `getRegionScopePairByLocation(blockPos)` – Get region and scope at specified block position.  
- `getRegionArea(region)` – Calculate the total area of a region.  
- `getRegionAreaById(id)` – Calculate the total area of a region by its ID.  

## Commands

- `/imyvm-world-geo select start`  
  Start selecting positions with a golden hoe.

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
