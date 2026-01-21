# **As a temporary fix for the horses issue here is what you can do:**

`/breedconfig disable horse` (if you don't have permissions: `/op self`)  
`/breedconfig save`  
(If you still can't mount:) Restart the server or game The next update should provide a reliable fix to the bug

![Lait's Animal Breeding with a family of sheeps and a family of cows in an animal pen](https://media.forgecdn.net/attachments/description/1431060/description_a865b611-7b51-4d79-8ce7-7c705a9e5498.png)

[![alt text](https://img.shields.io/badge/@lait__kelomins-white?color=7948A3&labelColor=gray&logo=x&logoColor=white&style=for-the-badge)](https://x.com/lait_kelomins) [![alt text](https://img.shields.io/badge/@lait__kelomins-white?color=3E4F93&labelColor=gray&logo=discord&logoColor=white&style=for-the-badge)](https://discord.gg/NXExAtes)

# **Lait's Animal Breeding**

**Animal breeding and baby growth for Hytale to expand your farm.**

> **Note:** This mod prioritizes making livestock reproduction possible. I recommend keeping animals in a pen and close together for best results. Always make a backup before trying new mods - though this one only spawns entities and doesn't modify save data for now.

***

## What's New in v1.3.0

# 1.3.0 - Taming & Entity Persistence

*   **Animal Taming** - Use `/nametag <name>` then right-click an animal while holding a Name Tag item to tame and name it
*   **Ownership System** - Tamed animals belong to you; use `/tamingsettings` to control whether others can interact
*   **Persistent Saves** - Tamed animals are saved to disk and survive server restarts
*   **Entity Respawning** - Tamed animals that despawn will respawn at their last position when you approach
*   **New Commands** - `/nametag`, `/taminginfo`, `/tamingsettings`, `/untame`

# 1.2.1

*   **Heart particles** - Pink hearts now appear above animals when they're in love mode
*   **Bug fixes** - Partially fixed horse interaction
*   **All creatures** - All creatures including humanoids are now in the `all` preset. I decline any responsibility with what happens in your worlds, in a future update you will be able to add any NPC you want including from mods or future game updates.

# 1.2.0

*   **119 animals** - Every creature in Hytale is now configurable for breeding (includes bosses and new creatures)
*   **New `all` preset** - Enable all 119 animals at once with `/breedconfig preset apply all`
*   **Auto-update presets** - Existing preset files automatically gain new animals on startup
*   **Restore command** - Reset built-in presets to defaults with `/breedconfig preset restore <name>`
*   **Fixed food items** - Removed wrong item IDs that didn't exist in the game
*   **Disable baby growth** - Use `/breedgrowth` to freeze babies so they never grow up

***

## Features

*   **Feed animals to breed** - Press F on animals with their favorite food to put them in love mode
*   **Proximity breeding** - Two animals in love will automatically breed if close enough
*   **Baby animals** - Breeding produces baby animals that grow into adults over time
*   **Wild baby growth** - Wild baby animals found in the world also grow into adults
*   **Breeding cooldown** - Parents need time to rest before breeding again
*   **49 animal types** - All livestock plus wild animals (wolves, bears, deer, birds, etc.)
*   **Multiple breeding foods** - Each animal can accept multiple food items
*   **Custom animals** - Add creatures from other mods via `/customanimal` commands
*   **Preset system** - Quick configuration with built-in presets
*   **Fully configurable** - Customize everything via JSON config or in-game commands

***

## Installation

*   Place the JAR file in your server's `mods` folder
*   Restart the server
*   (Optional) Configure via `/breedconfig` commands or edit the JSON file
*   Feed animals to start breeding!

***

## How It Works

*   Find two animals of the same type
*   Feed each one their favorite food (use `/breedconfig info <animal>` to check)
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
/breedconfig preset list              Show available presets
/breedconfig preset apply <name>      Apply a preset
/breedconfig preset save <name>       Save current config as preset ⚠️ _This will override the previous preset values if the name was already in use and they can't be recovered_
/breedconfig preset restore <name>    Reset built-in preset to defaults
```

**Note:** Built-in presets (default, default\_extended, lait\_curated, zoo, all) are automatically updated with new animals on startup. Use `restore` to reset a preset to factory defaults if you've modified it.

### Creating Custom Presets

**If the game tells you you don't have the permissions to use this command, run `/op self` first**

**To apply a preset to your world:**  
Copy an existing preset in `mods/presets/`, rename it, edit the values, then apply with `/breedconfig preset apply my_preset`

**To create a new preset from the current world config**  
⚠️ _This will override the previous preset values if the name was already in use and they can't be recovered_  
Configure animals with `/breedconfig` commands, then run `/breedconfig preset save my_preset`

***

## Commands

All commands use the unified `/breed` prefix for easy discovery.

### Main Commands

```
/breed                                Show help
/breed help                           Show help
/breed status                         View tracked animals and stats
/breed growth                         Toggle baby growth on/off
```

### Taming

```
/breed tame <name>                    Prepare to tame and name an animal
/breed untame                         Release a tamed animal
/breed info                           Show taming information
/breed settings                       Taming settings
```

### Configuration

```
/breed config                         Show config summary
/breed config list [category]         List animals by category
/breed config info <animal>           Show detailed animal info
/breed config reload                  Reload config from file
/breed config save                    Save current config to file
/breed config enable <animal|ALL>     Enable breeding
/breed config disable <animal|ALL>    Disable breeding
/breed config set <animal> food <item>      Set primary food
/breed config set <animal> growth <min>     Set growth time
/breed config set <animal> cooldown <min>   Set cooldown
/breed config addfood <animal> <item>       Add breeding food
/breed config removefood <animal> <item>    Remove breeding food
```

### Custom Animals (Other Mods)

Add creatures from other mods to the breeding system. Simply provide the NPC role name and the plugin will auto-discover the model.

```
/breed custom add <npcRole> <food> [food2] [food3]   Add by NPC role (auto-discovers model)
/breed custom setbaby <model> <babyRole>             Set baby NPC role for dedicated baby spawning
/breed custom remove <model>                         Remove a custom animal
/breed custom list                                   List all custom animals
/breed custom info <model>                           Show custom animal details
/breed custom enable <model>                         Enable a custom animal
/breed custom disable <model>                        Disable a custom animal
/breed custom addfood <model> <food>                 Add a breeding food
/breed custom removefood <model> <food>              Remove a breeding food
/breed custom scan                                   Scan world for creatures
```

**Example:** To add a creature with NPC role "VgSlime_Npc_Guumi_Green":
```
/breed custom add VgSlime_Npc_Guumi_Green apple
```

The plugin validates the NPC role exists, spawns a temp entity to discover the model, and registers both for detection and spawning.

**Baby spawning:** If you know the baby NPC role, set it with `/breed custom setbaby`. Otherwise, babies spawn as scaled-down adults (40% size).

Custom animals are automatically detected when they spawn and saved to config.

### Legacy Commands (Deprecated)

The following commands still work but show deprecation warnings:

| Old Command | New Command |
|-------------|-------------|
| `/laitsbreeding` | `/breed` |
| `/breedstatus` | `/breed status` |
| `/breedgrowth` | `/breed growth` |
| `/breedconfig` | `/breed config` |
| `/nametag` | `/breed tame` |
| `/untame` | `/breed untame` |
| `/taminginfo` | `/breed info` |
| `/tamingsettings` | `/breed settings` |
| `/customanimal` | `/breed custom` |

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

**Done:** Configurable timing, multiple breeding foods, external preset files, all creatures breeding, heart particles, custom animals from other mods

**Planned:**

*   Feed babies to make them grow faster
*   Advanced config (custom scales per growth stage, baby models/NPCs, offsprings count)
*   Animal wellness (pet your animals, build trust, mood affects breeding)
*   Genetics system (babies inherit traits and appearance from parents)

***

## Known Issues

*   **Animations don't interrupt** - Feeding while an animal is fleeing or sleeping won't interrupt them
*   **Growth time setting** - May not work reliably in all cases (investigating)
*   **Wild baby animals** - May sometimes not grow into adults
*   **Single world only** - Only works with the default world (multi-world not supported)

***

## Contact

This mod is actively being developed. For issues or suggestions, add me on Discord: **lait\_kelomins**

[![alt text](https://img.shields.io/badge/@lait__kelomins-white?color=7948A3&labelColor=gray&logo=x&logoColor=white&style=for-the-badge)](https://x.com/lait_kelomins) [![alt text](https://img.shields.io/badge/@lait__kelomins-white?color=3E4F93&labelColor=gray&logo=discord&logoColor=white&style=for-the-badge)](https://discord.gg/NXExAtes)