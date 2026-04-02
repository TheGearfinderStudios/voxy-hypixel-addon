# Voxy Hypixel Addon

A standalone Fabric addon for **[Voxy](https://modrinth.com/mod/voxy)** designed to fix chunk data leakage between same-type Hypixel dimensions across gamemodes.

Created by **TBlazeWarriorT**.

## ⚠️ Alpha Notice
This mod is an addon for Voxy, which is currently in its early **Alpha** development stage. Both Voxy and this addon are experimental. Use at your own risk. (Ban risk is minimal, rendering issues are very possible)

## What does this do?
Voxy is a world-streaming mod that stores chunk data locally. On Hypixel, players frequently switch between different islands (Hub, Private Island, Dwarven Mines, etc.) while technically staying on the same "server world."

Simple example: Hypixel Lobby, Arcade Lobby and Skyblock Hub stop all sharing the overworld Voxy folder in your computer and fighting each other.
Without this addon, Voxy treats all lobbies/islands in a server as one single world/dimension, causing "ghost chunks" from one area to appear in another.

**Voxy Hypixel Addon** fixes this by:
- Detecting your current Hypixel gamemode and area using the **HM-API**.
- Injecting a unique identifier into Voxy's world storage logic.
- Forcing Voxy to isolate chunk data into separate folders based on that.

## Requirements
- **Fabric Loader**
- **Voxy** (Alpha)
- **Fabric API**

*Note: The [HM-API](https://github.com/AzureAaron/HM-API) is bundled within the mod, so you do NOT need to download it separately.*

## License
This project is licensed under the **MIT License**. You are free to use, modify, and distribute it, provided that credit is given to the original author.
