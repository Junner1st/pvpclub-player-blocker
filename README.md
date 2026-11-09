# PvPClub Blocker

PvPClub Blocker is a Fabric client mod for maintaining a local player block
list on `mcpvp.club` and its subdomains. When a blocked player appears in your
match, the mod can automatically leave before the fight begins.

## Requirements

- Minecraft 1.21.11
- Java 21
- Fabric Loader 0.19.2 or newer
- Fabric API
- malilib 0.27.16 or newer
- Mod Menu is optional

## Commands

| Command | Description |
| --- | --- |
| `/pblock <player> [reason]` | Add a player to the block list. The default reason is `other`. |
| `/pb <player> [reason]` | Short alias for `/pblock`. |
| `/punblock <player>` | Remove a player from the block list. |
| `/pblocked` | Open the blocked-player screen. |

`/pblock` and `/pb` suggest players from the current match. After a match or
disconnect, suggestions fall back to players from the previous match. A player
who is not currently online is looked up through the official Minecraft
profile service.

## Block reasons

- `weird_behavior`
- `long_time_no_active`
- `bad_language`
- `refusing_to_fight`
- `ely_macing_at_crystal`
- `swap_macing_at_crystal`
- `crit_spam`
- `other`

## Managing blocked players

The blocked-player screen supports adding players, removing entries, searching,
and filtering by reason. It shows each player's name, UUID, reason, and skin
when available.

You can open it with `/pblocked`, from the pause screen, or from Minecraft's
Social Interactions screen. You can also assign hotkeys for the configuration
and block-list screens.

## Automatic disconnect

Automatic detection is enabled by default. After joining a match, the mod waits
for the configured delay and checks the players in that match. If it finds a
blocked player, it disconnects and shows that player's name and block reason on
the disconnect screen.

The default delay is 20 ticks. Detection stops when the match ends and does not
run in the lobby.

Blocking someone with `/pblock` during a match can also trigger a delayed
disconnect. This behavior is disabled by default and has its own delay setting.

## Settings

Open the configuration through Mod Menu or the blocked-player screen.

The configuration screen provides:

- automatic blocked-player detection;
- delay after joining a match;
- disconnect after using `/pblock` during a match;
- delay after using `/pblock`;
- configuration-screen hotkey;
- block-list hotkey.

The configuration and block list are stored locally in
`config/pvpclub-blocker.json` inside the Minecraft instance.

Runtime messages use the `[pvpclub blocker]` prefix in the Minecraft log.

## Developer notes

Build with Java 21:

```bash
./gradlew build
```

The remapped mod jar is written to `build/libs/`.

### Packet debugging

Full packet logging is disabled by default and is intentionally not exposed in
the GUI. It is enabled when either of these values is `true`:

- `"packetDebug": true` in `config/pvpclub-blocker.json` followed by a game restart;
- `PacketTraceLog.FORCE_ENABLED = true` in the source followed by a rebuild.

Packet logs are written to `logs/pvpclub-blocker/`. They can grow quickly and
may contain chat and other private session data.
