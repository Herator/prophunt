# Block Hide and Seek

A Minecraft Paper plugin for **1.21.1** where hiders disguise as blocks and seekers hunt them down. No client mods required — everything is server-side!

## How It Works

- **Hiders** choose a block from a configurable list and become that block. When they stand still, they turn into an actual solid block that blends into the world.
- **Seekers** are held at a spawn point during a countdown, then released with an iron sword to hunt. They also get a **Firework Tracker** that launches fireworks near each hider's position.
- When a hider is found (hit by a seeker), they become a seeker too.
- **Hiders win** if time runs out. **Seekers win** if everyone is found.
- At the end, a stats summary shows who was found and in what order.

## Building

Requires **Java 21** and **Maven**.

**Option 1 — Use the build script (Windows):**
Double-click `BUILD.bat`. It downloads Maven automatically and builds the plugin.

**Option 2 — Manual:**
```
mvn clean package
```

The plugin JAR will be at `target/BlockHideSeek-1.0.0.jar`. Copy it into your Paper server's `plugins` folder.

## Commands

All commands use `/hs` (aliases: `/hideseek`, `/bhs`) and require the `blockhideseek.admin` permission (op by default).

| Command | Description |
|---|---|
| `/hs start <seeker> [seeker2...]` | Start a game — named players are seekers, everyone else hides |
| `/hs stop` | Force stop the current game |
| `/hs setseekerspawn` | Set the seeker waiting area to your location |
| `/hs addblock <MATERIAL>` | Add a block to the disguise list |
| `/hs removeblock <MATERIAL>` | Remove a block from the disguise list |
| `/hs blocks` | Show all allowed disguise blocks |
| `/hs settime <seconds>` | Set game duration |
| `/hs setcountdown <seconds>` | Set the hiding phase duration |
| `/hs setcooldown <seconds>` | Set the firework tracker cooldown |
| `/hs reload` | Reload config from file |

## Configuration

Edit `plugins/BlockHideSeek/config.yml`:

```yaml
game:
  game-time: 300          # Total game time in seconds
  seeker-countdown: 60    # Hiding phase before seekers are released
  still-time-ticks: 30    # Ticks standing still before becoming a solid block

tracker:
  cooldown: 45            # Seconds between tracker uses
  inaccuracy-radius: 5    # Fireworks spawn within this many blocks of hiders

allowed-blocks:           # Blocks hiders can disguise as
  - STONE
  - OAK_PLANKS
  - GRASS_BLOCK
  # ... add or remove as you like
```

## Quick Start

1. Install the plugin on a Paper 1.21.1 server
2. Stand where seekers should wait and run `/hs setseekerspawn`
3. Make sure at least 2 players are online
4. Run `/hs start <playerName>` to make someone the seeker
5. Hiders: right-click a block in your hotbar to pick your disguise, then go hide!
6. Seekers: hit suspicious blocks with your sword, or use the Firework Tracker (right-click the Firework Star)

## Requirements

- Paper or Folia server for Minecraft **1.21.1**
- Java **21**
- No client mods needed for players
