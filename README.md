# Voxy Hypixel Addon

A standalone Fabric addon for **[Voxy](https://modrinth.com/mod/voxy)** designed to fix chunk data leakage on Hypixel Skyblock.

Created by **TBlazeWarriorT**.

## ⚠️ Alpha Notice
This mod is an addon for Voxy, which is currently in its **Alpha** development stage. Both Voxy and this addon are highly experimental. Use at your own risk.

## What does this do?
Voxy is a world-streaming mod that stores chunk data locally. On Hypixel Skyblock, players frequently switch between different islands (Hub, Private Island, Dwarven Mines, etc.) while technically staying on the same "server world." 

Without this addon, Voxy treats all these islands as one single world, causing "ghost chunks" from one area to appear in another.

**Voxy Hypixel Addon** fixes this by:
- Detecting your current Skyblock area using the **HM-API**.
- Injecting a unique identifier into Voxy's world storage logic.
- Forcing Voxy to isolate chunk data into separate folders per-island.

## Requirements
- **Minecraft**: 1.21.1
- **Fabric Loader**: 0.16.1 or newer
- **Voxy**: 0.2.13-alpha or newer
- **Fabric API**

*Note: The [HM-API](https://github.com/AzureAaron/HM-API) is bundled within the mod, so you don't need to download it separately.*

## Installation
1. Install **Fabric** for 1.21.1.
2. Download and drop **Voxy** into your `mods` folder.
3. Download and drop **Voxy Hypixel Addon** into your `mods` folder.
4. Enjoy isolated chunk storage!

## License
This project is licensed under the **MIT License**. You are free to use, modify, and distribute it, provided that credit is given to the original author.
