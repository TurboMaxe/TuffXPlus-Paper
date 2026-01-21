# TuffX-United

![preview](./img/showcase.png)

TuffX-United is a single, unified plugin that combines:
- TuffX (Below Y0 support for modern world depth)
- ViaBlocks (custom block palette + chunk updates for modern blocks)
- ViaSounds (modern sound support)
- TuffActions (swimming sync + creative item handling)

This is not a "crack" for Minecraft. It only enables extra client integration features for TuffClient/Eaglercraft.

## Requirements
- Java 17
- Spigot/Paper 1.18+
- ViaVersion and ViaBackwards

PacketEvents, Jackson, and WebSocket libraries are shaded into the jar.

## Install
1. Drop `TuffX-United-0.0.1.jar` into your server's `plugins` folder.
2. Start the server to generate `plugins/TuffX/config.yml`.
3. Configure features in `config.yml` (`y0`, `viablocks`, `swimming`, `creative-items`, `registry`).
4. Restart or run `/tuffx reload`.

## Features
- Below Y0: sends extra chunk data for Y < 0 so TuffX client can see and interact with modern world depth.
- ViaBlocks: synchronizes modern block states to TuffX client with a custom palette.
- ViaSounds: intercepts modern sound packets and sends them to TuffX client.
- TuffActions: swimming state sync and creative item handling.
- Optional server registry over WebSocket (for discovery).

## Commands
- `/tuffx reload` - reload the config
- `/viablocks get` - give a set of custom blocks (creative)
- `/viablocks refresh` - resend ViaBlocks data in view distance

## Permissions
- `tuffx.reload`
- `tuffx.viablocks.command.get`
- `tuffx.viablocks.command.refresh`

## Compiling
```sh
git clone https://github.com/TuffNetwork/Tuff-united.git
cd Tuff-united
./gradlew build
```

Output jar: `build/libs/TuffX-United-0.0.1.jar`.

## Support
Join our Discord: https://discord.gg/G76Q3K4bWJ
