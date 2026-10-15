# distortion

World-anchored, colored circular ripple screen distortions for Minecraft, done with a resource pack and a server
plugin. No client mods.

A marker text display in the world carries the ripple's position, size and color. A modified text shader moves that
data into a corner of the frame, and a post effect reads it back and draws the ripple. The resource pack has to be
ported for each Minecraft shader format, so every supported version range has its own branch.

Please use the **branch selector** to pick your version.

## Resource pack branches

The pack sits at the root of its branch (`pack.mcmeta`, `assets/`).

| Branch | Minecraft versions | Notes |
|---|---|---|
| `26.3` | 26.3 | Applied by the server with the post effect command and API. Has a debug view. |
| `26.2` | 26.2 | Rides on the Fabulous! graphics transparency effect. |
| `26.1` | 26.1 to 26.1.2 | Same as above. |
| `1.21.11` | 1.21.11 | Same as above. |
| `1.21.10` | 1.21.9 to 1.21.10 | Same as above. |
| `1.21.8` | 1.21.6 to 1.21.8 | Same as above, older shader version. |
| `1.21.5` | 1.21.5 | Plain uniforms, no `ScreenSize`. |
| `1.21.4` | 1.21.2 to 1.21.4 | Shader program JSON files. |

Before 26.3 a server cannot turn a post effect on for a player, so those packs only show the effect for players who
have **Fabulous!** graphics enabled. Versions older than 1.21.2 are not supported.

Only the 26.3 pack has been run in game so far. The others compile as GLSL but are untested.

## Plugin

The Paper plugin (branch `plugin`) is a single jar that embeds one pack per branch above and sends each player the pack
that matches the client version they connect with. It asks ViaVersion for that version when it is installed, and logs
a warning on servers with ViaBackwards that clients older than 1.21.2 will not see the effects.

Spawn a ripple with `/distortion spawn <hex> <radius> <ticks> [x y z]`, or call `DistortionAPI.spawn` from another
plugin.
