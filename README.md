<div align="center">

<img src="logo.png" alt="Distortion logo" width="128" height="128">

# Distortion

[![Issues](https://img.shields.io/github/issues/RATR2/distortion?style=for-the-badge&logo=github)](https://github.com/RATR2/distortion/issues)
[![Loaders](https://img.shields.io/badge/loaders-paper-blue?style=for-the-badge)](#plugin)
[![Minecraft](https://img.shields.io/badge/minecraft-1.21.2%20to%2026.3-blue?style=for-the-badge)](#resource-pack-branches)
[![License](https://img.shields.io/github/license/RATR2/distortion?style=for-the-badge&logo=github)](LICENSE)
[![Stars](https://img.shields.io/github/stars/RATR2/distortion?style=for-the-badge&logo=github)](https://github.com/RATR2/distortion/stargazers)
[![Status](https://img.shields.io/badge/status-in--development-yellow?style=for-the-badge)](#resource-pack-branches)

> Colored ripples that bend the screen around a point in the world.
> A resource pack and a server plugin, no client mods.

If you find any issues, please report them [here](https://github.com/RATR2/distortion/issues)

</div>

---

## What is this

Distortion draws a circular ripple that is anchored to a position in the world, not to the screen. It grows outward from that point, bends the picture around its ring, tints it with a color of your choosing, and fades out. Up to 256 can be live at once.

Everything the player needs arrives as a normal server resource pack, so nobody installs a mod. The server sends each player the pack that matches their client version and spawns the ripples.

## How it works

1. **Marker.** A text display with a special glyph sits in the world. Its brightness picks a slot (0 to 255), its text color is the ripple color, and its scale carries the radius and strength.
2. **Text shader.** The pack overrides the text shader. It spots the marker, projects its position to the screen, and writes the numbers into a small block of pixels in the corner of the frame.
3. **Post effect.** A post effect reads those pixels back, finds each live ripple, and warps the picture around it. The data pixels are hidden before the frame is shown.

The shader formats change from one Minecraft release to the next, so the pack is ported per version range. That is why this repository has a branch for each one.

## Resource Pack Branches

Use the **branch selector** to pick your version. The pack sits at the root of its branch (`pack.mcmeta`, `assets/`).

| Branch | Minecraft versions | Notes |
|---|---|---|
| `26.3` | 26.3 | Applied by the server with the post effect command and API. Has a debug view |
| `26.2` | 26.2 | Rides on the Fabulous! graphics transparency effect |
| `26.1` | 26.1 to 26.1.2 | Same as above |
| `1.21.11` | 1.21.11 | Same as above |
| `1.21.10` | 1.21.9 to 1.21.10 | Same as above |
| `1.21.8` | 1.21.6 to 1.21.8 | Same as above, older shader version |
| `1.21.5` | 1.21.5 | Plain uniforms, no `ScreenSize` |
| `1.21.4` | 1.21.2 to 1.21.4 | Shader program JSON files |

Before 26.3 a server cannot turn a post effect on for a player, so those packs only show the effect for players who have **Fabulous!** graphics enabled. Versions older than 1.21.2 are not supported.

> **Note:** only the 26.3 pack has been run in game so far. The others compile as GLSL but are untested. Things will change.

## Plugin

The Paper plugin lives on the `plugin` branch. It is a single jar that embeds one pack per branch above and sends each player the pack for the client version they connect with. It asks ViaVersion for that version when it is installed, and logs a warning on servers with ViaBackwards that clients older than 1.21.2 will not see the effects. The pack is served from a built-in web server, so the configured port has to be reachable by players.

Requires Paper 26.3. ViaVersion and ViaBackwards are optional.

| Command | Permission | What it does |
|---|---|---|
| `/distortion spawn <hex> <radius> <ticks> [x y z]` | `distortion.spawn` | Spawns a ripple of that color, growing to the radius in blocks over the given ticks |
| `/distortion reload <config\|host\|both>` | `distortion.reload` | Reloads the config and/or restarts the pack host |
| `/distortion debug [on\|off]` | `distortion.debug` | Shows outlines and the raw data strip (26.3 clients only) |

Other plugins can call `DistortionAPI.spawn(Location, int rgb, double radius, int ticks)`, which returns false when all 256 slots are busy.

## License

See [LICENSE](LICENSE). MIT.

---

<div align="center">

Thanks, R4T Out.

</div>
