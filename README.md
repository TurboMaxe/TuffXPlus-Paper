# TuffX+

> [!WARNING]
> This plugin does not allow Swimming for TuffClient users if the server is on Paper. 


![preview](./img/showcase.png)

TuffX+ is a single, unified plugin that combines:
- TuffX (Below Y0 support for modern world depth)
- ViaBlocks (custom block palette + chunk updates for modern blocks)
- ViaEntities (modern entity sync without nametags)
- TuffActions (swimming sync + creative item handling)

## Requirements
- Java 17
- Spigot/Paper 1.18+
- ViaVersion and ViaBackwards

PacketEvents, Jackson, and WebSocket libraries are shaded into the jar.

## Install
1. Download the latest version from the Releases page.
2. Drop `TuffXPlus-x.x.x.jar` into your server's `plugins` folder.
3. Start the server to generate `plugins/TuffX/config.yml`.
4. Configure features in `config.yml` (`y0`, `registry`, `viablocks`,  `swimming`, `creative-items`, `restrictions`).
5. Restart or run `/tuffx reload`.

## Features
- Below Y0: sends extra chunk data for Y < 0 so TuffX client can see and interact with modern world depth.
- ViaBlocks: synchronizes modern block states to TuffX client with a custom palette.
- ViaEntities: syncs modern entities to TuffX client without nametags.
- TuffActions: swimming state sync and creative item handling.
- Restrictions: disallow TuffClient modules: [module list](/docs/restrictions.md)
- Optional server registry over WebSocket (for discovery).

## Commands
- `/tuffx reload` - reload the config
- `/viablocks get` - give a set of custom blocks (creative)
- `/viablocks refresh` - resend ViaBlocks data in view distance
- `/restrictions disallow` - add a module to the disallow list and send an update to all TuffClient clients
- `/restrictions allow` - remove a module from the disallow list and send an update

## Permissions
- `tuffx.reload`
- `tuffx.viablocks.command.get`
- `tuffx.viablocks.command.refresh`
- `tuffx.restrictions.command.disallow`
- `tuffx.restrictions.command.allow`

## Compiling
```sh
git clone https://github.com/TuffNetwork/TuffPlus.git
cd TuffPlus
./gradlew build
```

Output jar: `build/libs/TuffXPlus-x.x.x.jar`.

## Support
Join our Discord: https://discord.gg/G76Q3K4bWJ
