# Currently merging this mod with TheBrandolorian's "Tameable Animals"

# **HyTame**

[![Personal discord](https://img.shields.io/badge/Join%20our%20Discord-white?color=3E4F93&labelColor=gray&logo=discord&logoColor=white&style=for-the-badge)](https://discord.gg/uSKWDCq8e) [![X Twitter profile](https://img.shields.io/badge/Follow%20@lait__kelomins%20On%20X-white?color=7948A3&labelColor=gray&logo=x&logoColor=white&style=for-the-badge)](https://x.com/lait_kelomins)</br></br>

[![Mod%20Community%20Discord](https://img.shields.io/discord/1461295822137327673?color=3E4F93&labelColor=gray&logo=discord&logoColor=white&style=for-the-badge&label=Mod%20Community)](https://discord.gg/UKQCgN5SJ)

This mod is actively being developed. For issues or suggestions, click on one of the links above.

## Quick Overview

**Animal breeding and baby growth for Hytale to expand your farm.**

> **Disclaimer:** This mod prioritizes making livestock reproduction possible. I recommend keeping animals in a pen and close together for best results. Always make a backup before trying new mods.

**What It Does**

*   Feed two animals to breed them (press F with appropriate food)
*   Babies spawn and grow into adults over time
*   All creatures in the base game supported
*   Fully configurable via commands or JSON
*   Add custom animals from other mods
*   Name animals and persist them through game restarts

**What It Can't Do (Yet)**

*   Define which foods attract animals (uses base game foods)
*   Make animals follow you (taming planned)
*   Feed babies to speed up growth (planned)
*   Interrupt animals that are fleeing or sleeping (the mod doesn't change animal behavior)

**Quick Start:** Find two animals → feed each one their favorite food → they breed → baby grows up.

**Useful commands:** `/hytame foods` (see all foods) or `/hytame config info Cow` (detailed info).

![HyTame with a family of sheeps and a family of cows in an animal pen](https://media.forgecdn.net/attachments/description/1431060/description_b17e3b9e-9d37-4560-8f53-24355750c7a0.png)

***

# Releasing 1.4.0 on Beta channel for feedback. Always make backups and install at your own risk.

**Known mod incompatibility:**

*   More mounts

***

## What's New in v1.4.0

*   **Command Rename** - All commands renamed from `/breed` to `/hytame` (old `/breed` still works with deprecation warning but will be removed in future updates)
*   **Quick Food Reference** - New `/hytame foods` command shows all animals and their breeding foods at a glance
*   **Separate Breeding/Taming Control** - You can now enable/disable breeding and taming independently per animal
*   **Permission System** - Admin commands now use permission nodes (`hytame.admin.*`) - Creative mode also grants admin access
*   **Informative Commands for Everyone** - `/hytame foods`, `/hytame config info`, and `/hytame config list` are available to all players
*   **Taming** - Merged with TheBrandolorian's "Tameable Animals" to add taming to the mod. Now animals need to be tamed before you can breed and name them.
*   **Animal Persistence** - All tamed animals are now persisted and should be restored if they are removed for some reason. Only deceased animals won't respawn.
*   **Food-based Taming** - Feed taming food to wild animals to tame them. The configuration now defines base food which can be overridden by taming food, healing food (WIP) and breeding food.
*   **(WIP for Release) Peaceful tamed animals** - Animals will stop being scared or agressive once tamed

## What's New in v1.3.x (doesn't work in solo, enable alpha files for server)

*   Fixed a bug with animals disappearing on game restart after using name tags (data may be recoverable in next update but no)
*   Fixed a bug that made the mod break in solo games
*   Removed excessive logging in the server console

## What's New in v1.3.0 (doesn't work in solo, enable alpha files for server)

#### Deprecated Commands

The following legacy commands still work but show deprecation warnings. Use the unified `/breed` command instead:

*   `/laitsbreeding` → `/breed help`
*   `/breedstatus` → `/breed status`
*   `/breedconfig` → `/breed config`

### Fixed

*   Various performance improvements
*   Various bug fixes including handling multiple worlds, potential memory leaks, potential server crashes
*   Horse feeding and mounting should now work (might still need to hit them once like in the base game)
*   Various bugs caused by changing the model of the player or spawning a model

## New Features Preview (some features might not work)

#### Animal Taming System

*   **Name Tags** - You can now craft name tags at the farming bench and tame an animal
*   **Random cute names** - Animals get random names like Fluffy, Spot, Buddy, Luna, etc.
*   **Ownership system** - Tamed animals belong to the player who tamed them

#### Entity Persistence

*   **Persistent taming data** - Tamed animals are saved to `tamed_animals.json` and survive server restarts
*   **Respawn system** - Tamed animals that despawn will respawn at their last position when a player approaches (64 block radius)
*   **State preservation** - Growth stage, breeding cooldowns, and all taming data are preserved across respawns
*   **Auto-save** - Data auto-saves every 5 minutes
*   **Shutdown save** - Data is always saved on server shutdown

#### Custom Animals (from other mods or future updates)

*   **Role-first registration** - `/breed custom add <npcRole> <food>` register by NPC role
*   **Auto model discovery** - Plugin validates the role exists, spawns a temp entity, and auto-discovers the model asset ID
*   **Baby role mapping** - New `/breed custom setbaby <model> <babyRole>` command for dedicated baby NPC spawning
*   **Scaling fallback** - If no baby role defined, babies spawn as scaled-down adults (40% size)
*   **Custom animal spawn detection** - Custom animals are now detected immediately when they spawn (same as built-in animals)

***

## Features

*   **Feed animals to breed** - Press F on animals with their favorite food to put them in love mode
*   **Proximity breeding** - Two animals in love will automatically breed if close enough
*   **Baby animals** - Breeding produces baby animals that grow into adults over time
*   **Wild baby growth** - Wild baby animals found in the world also grow into adults
*   **Breeding cooldown** - Parents need time to rest before breeding again
*   **All creatures in the base game** - All livestock in the base presets, as well as wild animals and all other creatures in the base game
*   **Any custom creature from mods** - You can add any creature you added from any other mod (and if there is a baby version of the creature you can also register it)
*   **Multiple breeding foods** - Each animal can accept multiple food items
*   **Preset system** - Quick configuration with built-in presets
*   **Fully configurable** - Customize everything via JSON config or in-game commands

***

## Installation

*   Place the JAR file in your server's `mods` folder
*   Restart the server
*   (Optional) Configure via `/hytame config` commands or edit the JSON file
*   Feed animals to start breeding!

***

## How It Works

*   Find two animals of the same type
*   Feed each one their favorite food (use `/hytame config info <animal>` to check)
*   Both animals enter "love mode"
*   If close enough, they breed and a baby spawns
*   The baby grows into an adult based on the configured growth time

***

## Supported Animals

### Livestock (Enabled by Default)

<div class="spoiler"><p><strong>Cow</strong> - Cauliflower (or Wheat, Lettuce in curated preset)<br><strong>Pig</strong> - Brown Mushroom (or Carrot, Potato, Apple)<br><strong>Chicken</strong> - Corn (or Wheat, Rice)<br><strong>Sheep</strong> - Lettuce (or Wheat, Cauliflower)<br><strong>Goat</strong> - Apple (or Wheat, Carrot)<br><strong>Horse</strong> - Carrot (or Apple, Wheat)<br><strong>Camel</strong> - Wheat (or Cactus Fruit)<br><strong>Ram</strong> - Apple (or Wheat)<br><strong>Turkey</strong> - Corn (or Wheat)<br><strong>Boar</strong> - Red Mushroom (or Brown Mushroom, Apple)<br><strong>Rabbit</strong> - Carrot (or Lettuce, Apple)<br><strong>Bison</strong> - Wheat (or Corn)<br></p><p>Plus variants: Desert Chicken, Mouflon, Wild Pig, Skrill, Warthog</p></div>

### Wild Animals (Disabled by Default)

Can be enabled via config or the `zoo` preset. These spawn as small adults and grow to full size.

<div class="spoiler"><p><strong>Mammals</strong> - Wolf, Fox, Bear (Grizzly &amp; Polar), Deer, Moose, Hyena, Antelope, Armadillo, Snow Leopard, Mosshorn, Sabertooth Tiger<br><strong>Birds</strong> - Duck, Pigeon, Parrot, Owl, Crow, Raven, Penguin, Flamingo, Bat, Bluebird, Finch, Sparrow, Woodpecker, Hawk, Vulture, Tetrabird<br><strong>Critters</strong> - Frog (Blue, Green, Orange), Gecko, Mouse, Squirrel, Meerkat<br><strong>Reptiles</strong> - Tortoise, Crocodile, Sand Lizard, Rhino Toad<br><strong>Dinosaurs</strong> - Raptor (Cave), Rex (T-Rex), Archaeopteryx, Pterodactyl<br><strong>Mythic</strong> - Emberwulf, Yeti, Fen Stalker, Cactee, Hatworm, Snapdragon, Trillodon<br><strong>Vermin</strong> - Rat, Molerat, Scorpion, Spider, Cobra, Rattlesnake, Marsh Snake<br><strong>Aquatic</strong> - Salmon, Catfish, Piranha, Clownfish, Crab, Lobster, Jellyfish, Pufferfish, Shark, Whale, and more<br><strong>Bosses</strong> - Fire Dragon, Frost Dragon<br></p></div>

***

## Presets

Presets are editable JSON files stored in `%AppData%/Roaming/Hytale/UserData/Saves/{YourSaveName}/mods/presets/`.

**`default_extended`** (NEW DEFAULT) - Best of both worlds. Default timings (30 min growth, 5 min cooldown) with multiple food options from `lait_curated`. Recommended for most players.

**`default`** - Streamlined experience with original game values. Single food per animal, 30 min growth, 5 min cooldown. Only livestock enabled.

**`lait_curated`** - Organic, natural experience. Multiple foods per animal, logical growth times (chickens 10 min, horses 30 min), balanced cooldowns. Rabbits breed fast (1 min cooldown), large animals need more rest.

**`zoo`** - All real-world animals enabled. Includes livestock, mammals, critters, birds, and reptiles. Excludes mythic creatures. Uses lait\_curated values.

**`all`** - EVERYTHING enabled. All 119 animals across all 10 categories including mythic creatures, vermin, aquatic, and boss dragons. Uses lait\_curated values. For the ultimate breeding experience.

### Preset Commands

```
/hytame config preset list              Show available presets
/hytame config preset apply <name>      Apply a preset
/hytame config preset save <name>       Save current config as preset ⚠️ _This will override the previous preset values if the name was already in use and they can't be recovered_
/hytame config preset restore <name>    Reset built-in preset to defaults
```

**Note:** Built-in presets (default, default\_extended, lait\_curated, zoo, all) are automatically updated with new animals on startup. Use `restore` to reset a preset to factory defaults if you've modified it.

### Creating Custom Presets

**If the game tells you you don't have the permissions to use this command, run `/op self` first**

**To apply a preset to your world:**
Copy an existing preset in `mods/presets/`, rename it, edit the values, then apply with `/hytame config preset apply my_preset`

**To create a new preset from the current world config**
⚠️ _This will override the previous preset values if the name was already in use and they can't be recovered_
Configure animals with `/hytame config` commands, then run `/hytame config preset save my_preset`

***

## Commands

All commands use the unified `/hytame` prefix for easy discovery.

### Player Commands (Available to Everyone)

```
/hytame                               Show help
/hytame help                          Show help
/hytame foods                         Quick reference for all animal foods
/hytame status                        View tracked animals and stats
/hytame config info <animal>          Show detailed animal info
/hytame config list [category]        List animals by category
```

### Taming

```
/hytame tame <name>                   Prepare to tame and name an animal
/hytame untame                        Release a tamed animal
/hytame info                          Show taming information
/hytame settings                      Taming settings
```

### Admin Commands (Require Permission)

These commands require admin permissions (Creative mode or `hytame.admin.*` permission node).

```
/hytame growth                        Toggle baby growth on/off
/hytame config reload                 Reload config from file
/hytame config save                   Save current config to file
/hytame config enable <animal|ALL>    Enable breeding
/hytame config disable <animal|ALL>   Disable breeding
/hytame config enabletaming <animal>  Enable taming (independent of breeding)
/hytame config disabletaming <animal> Disable taming (independent of breeding)
/hytame config set <animal> food <item>      Set primary food
/hytame config set <animal> growth <min>     Set growth time
/hytame config set <animal> cooldown <min>   Set cooldown
/hytame config addfood <animal> <item>       Add breeding food
/hytame config removefood <animal> <item>    Remove breeding food
```

### Custom Animals (Other Mods)

Add creatures from other mods to the breeding system. Simply provide the NPC role name and the plugin will auto-discover the model.

```
/hytame custom add <npcRole> <food> [food2] [food3]   Add by NPC role (auto-discovers model)
/hytame custom setbaby <model> <babyRole>             Set baby NPC role for dedicated baby spawning
/hytame custom remove <model>                         Remove a custom animal
/hytame custom list                                   List all custom animals
/hytame custom info <model>                           Show custom animal details
/hytame custom enable <model>                         Enable a custom animal
/hytame custom disable <model>                        Disable a custom animal
/hytame custom addfood <model> <food>                 Add a breeding food
/hytame custom removefood <model> <food>              Remove a breeding food
/hytame custom scan                                   Scan world for creatures
```

**Example:** To add a creature with NPC role "VgSlime\_Npc\_Guumi\_Green":

```
/hytame custom add VgSlime_Npc_Guumi_Green apple
```

The plugin validates the NPC role exists, spawns a temp entity to discover the model, and registers both for detection and spawning.

**Baby spawning:** If you know the baby NPC role, set it with `/hytame custom setbaby`. Otherwise, babies spawn as scaled-down adults (40% size).

Custom animals are automatically detected when they spawn and saved to config.

### Legacy Commands (Deprecated)

The following commands still work but show deprecation warnings:

*   `/breed` → `/hytame`
*   `/laitsbreeding` → `/hytame`
*   `/breedstatus` → `/hytame status`
*   `/breedgrowth` → `/hytame growth`
*   `/breedconfig` → `/hytame config`

***

## Permissions

HyTame uses a permission system for admin commands. Players can access admin commands through:

1. **Creative Mode** - Automatically grants all admin permissions
2. **Permission Nodes** - Grant specific permissions via `/perm` command

### Permission Nodes

| Permission | Description |
|------------|-------------|
| `hytame.admin` | All admin commands |
| `hytame.admin.config` | Configuration commands (enable, disable, reload, save) |
| `hytame.admin.growth` | Toggle baby growth |
| `hytame.admin.custom` | Manage custom animals |
| `hytame.admin.debug` | Debug commands |

### Granting Permissions

```
/perm player <name> add hytame.admin           Grant all admin permissions
/perm player <name> add hytame.admin.config    Grant only config permissions
```

**Note:** Informative commands like `/hytame foods`, `/hytame config info`, and `/hytame config list` are available to all players without any permissions.

***

## Configuration

Config file: `mods/laits-breeding-config.json`

```
{
  "activePreset": "default_extended",
  "defaults": {
    "growthTimeMinutes": 30.0,
    "breedCooldownMinutes": 5.0,
    "growthEnabled": true
  },
  "animals": {
    "COW": {
      "enabled": true,
      "breedingFoods": ["Plant_Crop_Wheat_Item", "Plant_Crop_Cauliflower_Item"],
      "growthTimeMinutes": 30.0,
      "breedCooldownMinutes": 5.0
    }
  }
}
```

***

## Roadmap

**Done:** Configurable timing, multiple breeding foods, external preset files, all creatures breeding, heart particles

**Planned:**

**Taming & Breeding Extended**

*   Taming to make domesticated animals follow you
*   Feed babies to make them grow faster

**Better customization**

*   Advanced config (custom scales per growth stage, baby models/NPCs, offsprings count)

**Animal Wellness & Bonding**

*   Animal wellness (pet your animals, build trust, mood affects breeding)

**Inheritance**

*   Genetics system (babies inherit traits, stats and appearance from parents)

**Modding**

*   Breeding & Taming library to allow other modders to integrate such mechanics as standalones in their own mods
*   Cross-compatibility testing with other mods

***

## Known Issues

*   **Animations don't interrupt** - Feeding while an animal is fleeing or sleeping won't interrupt them
*   **Growth time setting** - May not work reliably in all cases (investigating)
*   **Wild baby animals** - May sometimes not grow into adults
*   **Single world only** - Only works with the default world (multi-world not supported)

***

## Contact

This mod is actively being developed. For issues or suggestions, click on one of the links below.

[![Personal discord](https://img.shields.io/badge/Join%20our%20Discord-white?color=3E4F93&labelColor=gray&logo=discord&logoColor=white&style=for-the-badge)](https://discord.gg/uSKWDCq8e) [![X Twitter profile](https://img.shields.io/badge/Follow%20@lait__kelomins%20On%20X-white?color=7948A3&labelColor=gray&logo=x&logoColor=white&style=for-the-badge)](https://x.com/lait_kelomins)</br></br>

[![Mod%20Community%20Discord](https://img.shields.io/discord/1461295822137327673?color=3E4F93&labelColor=gray&logo=discord&logoColor=white&style=for-the-badge&label=Mod%20Community)](https://discord.gg/UKQCgN5SJ)

[![BisectHosting partnership program, code lait for 25% discount on a gaming server](https://www.bisecthosting.com/partners/custom-banners/2aa6078b-c5d0-416b-89a6-347d410d20cb.webp)](https://bisecthosting.com/lait)