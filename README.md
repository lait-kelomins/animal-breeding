![Lait's Animal Breeding with a family of sheeps and a family of cows in an animal pen](https://media.forgecdn.net/attachments/description/1431060/description_a865b611-7b51-4d79-8ce7-7c705a9e5498.png)

[![alt text](https://img.shields.io/badge/@lait__kelomins-white?color=7948A3&labelColor=gray&logo=x&logoColor=white&style=for-the-badge)](https://x.com/lait_kelomins) [![alt text](https://img.shields.io/badge/@lait__kelomins-white?color=3E4F93&labelColor=gray&logo=discord&logoColor=white&style=for-the-badge)](https://discord.gg/NXExAtes)

# **Lait's Animal Breeding**

**Animal breeding and baby growth for Hytale to expand your farm.**

> **Note:** This mod prioritizes making livestock reproduction possible. I recommend keeping animals in a pen and close together for best results. Always make a backup before trying new mods - though this one only spawns entities and doesn't modify save data for now.

***

## What's New in v1.1.0

*   **49 animals** - Breed wolves, bears, foxes, birds, and more (enable via config)
*   **Picky eaters** - Animals only accept their favorite foods, not just anything
*   **Presets** - Three built-in presets (`default`, `lait_curated`, `zoo`) plus create your own with `/breedconfig preset save <name>`
*   **External config** - Edit preset files directly in `mods/presets/*.json`
*   **Horse fix** - You can now mount horses if you're empty handed or holding something other than food (sometimes it doesn't work, still working on it)

***

## Features

*   **Feed animals to breed** - Press F on animals with their favorite food to put them in love mode
*   **Proximity breeding** - Two animals in love will automatically breed if close enough
*   **Baby animals** - Breeding produces baby animals that grow into adults over time
*   **Wild baby growth** - Wild baby animals found in the world also grow into adults
*   **Breeding cooldown** - Parents need time to rest before breeding again
*   **49 animal types** - All livestock plus wild animals (wolves, bears, deer, birds, etc.)
*   **Multiple breeding foods** - Each animal can accept multiple food items
*   **Preset system** - Quick configuration with built-in presets
*   **Fully configurable** - Customize everything via JSON config or in-game commands

***

## Supported Animals

### Livestock (Enabled by Default)

<div class="spoiler"><p><strong>Cow</strong> - Cauliflower (or Wheat, Lettuce in curated preset)<br><strong>Pig</strong> - Brown Mushroom (or Carrot, Potato, Apple)<br><strong>Chicken</strong> - Corn (or Wheat, Rice)<br><strong>Sheep</strong> - Lettuce (or Wheat, Cauliflower)<br><strong>Goat</strong> - Apple (or Wheat, Carrot)<br><strong>Horse</strong> - Carrot (or Apple, Wheat)<br><strong>Camel</strong> - Wheat (or Cactus Fruit)<br><strong>Ram</strong> - Apple (or Wheat)<br><strong>Turkey</strong> - Corn (or Wheat)<br><strong>Boar</strong> - Red Mushroom (or Brown Mushroom, Apple)<br><strong>Rabbit</strong> - Carrot (or Lettuce, Apple)<br><strong>Bison</strong> - Wheat (or Corn)<br></p><p>Plus variants: Desert Chicken, Mouflon, Wild Pig, Skrill, Warthog</p></div>

### Wild Animals (Disabled by Default)

Can be enabled via config or the `zoo` preset. These spawn as small adults and grow to full size.

<div class="spoiler"><p><strong>Mammals</strong> - Wolf, Fox, Bear (Grizzly &amp; Polar), Deer, Moose, Hyena<br><strong>Birds</strong> - Duck, Pigeon, Parrot, Owl, Crow, Raven, Penguin, Flamingo<br><strong>Critters</strong> - Frog (Blue, Green, Orange), Gecko, Mouse, Squirrel, Meerkat<br><strong>Reptiles</strong> - Tortoise, Crocodile, Sand Lizard<br><strong>Mythic</strong> - Emberwulf, Yeti, Fen Stalker<br></p></div>

***

## Presets

Presets are editable JSON files stored in `%AppData%/Roaming/Hytale/UserData/Saves/{YourSaveName}/mods/presets/`.

**`default`** - Streamlined experience with original game values. Single food per animal, 30 min growth, 5 min cooldown. Only livestock enabled.

**`lait_curated`** - Organic, natural experience. Multiple foods per animal, logical growth times (chickens 10 min, horses 30 min), balanced cooldowns. Rabbits breed fast (1 min cooldown), large animals need more rest.

**`zoo`** - All real-world animals enabled. Includes livestock, mammals, critters, birds, and reptiles. Excludes mythic creatures. Uses lait\_curated values.

### Preset Commands

```
/breedconfig preset list              Show available presets
/breedconfig preset apply <name>      Apply a preset
/breedconfig preset save <name>       Save current config as preset (⚠️ This will override the previous preset values and they can't be recovered)
```

### Creating Custom Presets

**If the game tells you you don't have the permissions to use this command, run `/op self` first**

**To apply a preset to your world:**  
Copy an existing preset in `mods/presets/`, rename it, edit the values, then apply with `/breedconfig preset apply my_preset`

**To create a new preset from the current world config**  
⚠️ _This will override the previous preset values and they can't be recovered_  
Configure animals with `/breedconfig` commands, then run `/breedconfig preset save my_preset`

***

## Commands

### Basic

```
/laitsbreeding                        Show help and breeding foods
/breedstatus                          View tracked animals and stats
```

### Configuration

```
/breedconfig                          Show config summary
/breedconfig list [category]          List animals by category
/breedconfig info <animal>            Show detailed animal info
/breedconfig reload                   Reload config from file
/breedconfig save                     Save current config to file
```

### Animal Management

```
/breedconfig enable <animal|category|ALL>     Enable breeding
/breedconfig disable <animal|category|ALL>    Disable breeding
/breedconfig set <animal> food <item>         Set primary food
/breedconfig set <animal> growth <minutes>    Set growth time
/breedconfig set <animal> cooldown <minutes>  Set cooldown
/breedconfig addfood <animal> <item>          Add breeding food
/breedconfig removefood <animal> <item>       Remove breeding food
```

***

## Configuration

Config file: `mods/laits-breeding-config.json`

```
{
  "activePreset": "lait_curated",
  "defaults": {
    "growthTimeMinutes": 20.0,
    "breedCooldownMinutes": 3.0
  },
  "animals": {
    "COW": {
      "enabled": true,
      "breedingFoods": ["Plant_Crop_Wheat_Item", "Plant_Crop_Cauliflower_Item"],
      "growthTimeMinutes": 25.0,
      "breedCooldownMinutes": 3.0
    }
  }
}
```

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

## Roadmap

**Done:** Configurable timing, multiple breeding foods, external preset files, wild animal breeding

**Planned:**

*   Instead of scanning once at startup and every 30 seconds, detect when entities are spawned
*   Heart particles when animal is in love
*   Feed babies to make them grow faster
*   Taming to make domesticated animals follow you
*   Advanced config (custom scales per growth stage, baby models/NPCs, offsprings count)
*   Animal wellness (pet your animals, build trust, mood affects breeding)
*   Genetics system (babies inherit traits and appearance from parents)
*   Cross-compatibility testing with other mods

***

## Known Issues

*   **Feeding sometimes doesn't work** - Try waiting 30 seconds before retrying
*   **Animations don't interrupt** - Feeding while an animal is fleeing or sleeping won't interrupt them
*   **Cooldown glitch** - Animals on cooldown may still produce babies in some cases
*   **Growth time setting** - May not work reliably in all cases (investigating)
*   **Wild baby animals** - May sometimes not grow into adults
*   **N World not handled** - Only works in single world servers

***

## Contact

This mod is actively being developed. For issues or suggestions, add me on Discord: **lait\_kelomins**

[![alt text](https://img.shields.io/badge/@lait__kelomins-white?color=7948A3&labelColor=gray&logo=x&logoColor=white&style=for-the-badge)](https://x.com/lait_kelomins) [![alt text](https://img.shields.io/badge/@lait__kelomins-white?color=3E4F93&labelColor=gray&logo=discord&logoColor=white&style=for-the-badge)](https://discord.gg/NXExAtes)

***

## Preset Reference Tables

### `default` Preset

| Animal | Food | Growth | Cooldown |
|--------|------|--------|----------|
| Cow | Cauliflower | 30 min | 5 min |
| Pig | Brown Mushroom | 30 min | 5 min |
| Chicken | Corn | 30 min | 5 min |
| Sheep | Lettuce | 30 min | 5 min |
| Goat | Apple | 30 min | 5 min |
| Horse | Carrot | 30 min | 5 min |
| Camel | Wheat | 30 min | 5 min |
| Ram | Apple | 30 min | 5 min |
| Turkey | Corn | 30 min | 5 min |
| Boar | Red Mushroom | 30 min | 5 min |
| Rabbit | Carrot | 30 min | 5 min |
| Bison | Wheat | 30 min | 5 min |
| Desert Chicken | Corn | 30 min | 5 min |
| Mouflon | Lettuce | 30 min | 5 min |
| Wild Pig | Brown Mushroom | 30 min | 5 min |
| Skrill | Corn | 30 min | 5 min |
| Warthog | Red Mushroom | 30 min | 5 min |

#### Wild Animals (Disabled)

| Animal | Category | Food | Growth | Cooldown |
|--------|----------|------|--------|----------|
| Wolf | Mammal | Cooked Meat | 30 min | 5 min |
| White Wolf | Mammal | Cooked Meat | 30 min | 5 min |
| Fox | Mammal | Raw Wildmeat | 30 min | 5 min |
| Grizzly Bear | Mammal | Apple | 30 min | 5 min |
| Polar Bear | Mammal | Cooked Fish | 30 min | 5 min |
| Deer (Doe) | Mammal | Carrot | 30 min | 5 min |
| Deer (Stag) | Mammal | Carrot | 30 min | 5 min |
| Moose (Bull) | Mammal | Wheat | 30 min | 5 min |
| Moose (Cow) | Mammal | Wheat | 30 min | 5 min |
| Hyena | Mammal | Raw Wildmeat | 30 min | 5 min |
| Blue Frog | Critter | Insect | 30 min | 5 min |
| Green Frog | Critter | Insect | 30 min | 5 min |
| Orange Frog | Critter | Insect | 30 min | 5 min |
| Gecko | Critter | Insect | 30 min | 5 min |
| Meerkat | Critter | Insect | 30 min | 5 min |
| Mouse | Critter | Wheat | 30 min | 5 min |
| Squirrel | Critter | Apple | 30 min | 5 min |
| Duck | Bird | Corn | 30 min | 5 min |
| Pigeon | Bird | Wheat | 30 min | 5 min |
| Parrot | Bird | Corn | 30 min | 5 min |
| Crow | Bird | Corn | 30 min | 5 min |
| Raven | Bird | Corn | 30 min | 5 min |
| Brown Owl | Bird | Raw Wildmeat | 30 min | 5 min |
| Snow Owl | Bird | Raw Wildmeat | 30 min | 5 min |
| Flamingo | Bird | Raw Fish | 30 min | 5 min |
| Penguin | Bird | Raw Fish | 30 min | 5 min |
| Tortoise | Reptile | Lettuce | 30 min | 5 min |
| Crocodile | Reptile | Raw Wildmeat | 30 min | 5 min |
| Sand Lizard | Reptile | Insect | 30 min | 5 min |
| Emberwulf | Mythic | Cooked Meat | 30 min | 5 min |
| Yeti | Mythic | Raw Wildmeat | 30 min | 5 min |
| Fen Stalker | Mythic | Raw Wildmeat | 30 min | 5 min |

***

### `lait_curated` Preset

Organic, natural experience with multiple food options and realistic growth times.

**Defaults:** Growth: 20 min | Cooldown: 3 min

### `lait_curated` Preset

| Animal | Foods | Growth | Cooldown |
|--------|-------|--------|----------|
| Cow | Wheat, Cauliflower, Lettuce | 25 min | 3 min |
| Pig | Carrot, Potato, Brown Mushroom, Apple | 15 min | 3 min |
| Chicken | Corn, Wheat, Rice | 10 min | 3 min |
| Sheep | Wheat, Lettuce, Cauliflower | 20 min | 3 min |
| Goat | Apple, Wheat, Carrot | 18 min | 3 min |
| Horse | Carrot, Apple, Wheat | 30 min | 3 min |
| Camel | Wheat, Cactus Fruit | 35 min | 3 min |
| Ram | Wheat, Apple | 20 min | 3 min |
| Turkey | Corn, Wheat | 15 min | 3 min |
| Boar | Red Mushroom, Brown Mushroom, Apple | 18 min | 3 min |
| Rabbit | Carrot, Lettuce, Apple | 8 min | **1 min** |
| Bison | Wheat, Corn | 35 min | 3 min |
| Desert Chicken | Corn, Wheat, Rice | 10 min | 3 min |
| Mouflon | Wheat, Lettuce, Cauliflower | 20 min | 3 min |
| Wild Pig | Red Mushroom, Brown Mushroom, Apple | 18 min | 3 min |
| Skrill | Corn, Raw Wildmeat | 20 min | 3 min |
| Warthog | Red Mushroom, Brown Mushroom, Apple | 18 min | 3 min |

#### Wild Animals (Disabled)

| Animal | Category | Foods | Growth | Cooldown |
|--------|----------|-------|--------|----------|
| Wolf | Mammal | Cooked Meat, Raw Wildmeat | 25 min | 3 min |
| White Wolf | Mammal | Cooked Meat, Raw Wildmeat | 25 min | 3 min |
| Fox | Mammal | Raw Wildmeat, Raw Fish | 15 min | 3 min |
| Grizzly Bear | Mammal | Apple, Raw Fish, Raw Wildmeat | 45 min | 3 min |
| Polar Bear | Mammal | Cooked Fish, Raw Fish, Raw Wildmeat | 45 min | 3 min |
| Deer (Doe) | Mammal | Carrot, Apple, Wheat | 25 min | 3 min |
| Deer (Stag) | Mammal | Carrot, Apple, Wheat | 25 min | 3 min |
| Moose (Bull) | Mammal | Wheat, Lettuce | 40 min | 3 min |
| Moose (Cow) | Mammal | Wheat, Lettuce | 40 min | 3 min |
| Hyena | Mammal | Raw Wildmeat | 20 min | 3 min |
| Blue Frog | Critter | Insect | 5 min | 3 min |
| Green Frog | Critter | Insect | 5 min | 3 min |
| Orange Frog | Critter | Insect | 5 min | 3 min |
| Gecko | Critter | Insect | 5 min | 3 min |
| Meerkat | Critter | Insect | 5 min | 3 min |
| Mouse | Critter | Wheat, Corn | 3 min | **0.5 min** |
| Squirrel | Critter | Apple, Corn | 5 min | 3 min |
| Duck | Bird | Corn, Wheat | 12 min | 3 min |
| Pigeon | Bird | Wheat, Corn | 10 min | 3 min |
| Parrot | Bird | Corn, Apple | 15 min | 3 min |
| Crow | Bird | Corn, Raw Wildmeat | 12 min | 3 min |
| Raven | Bird | Corn, Raw Wildmeat | 12 min | 3 min |
| Brown Owl | Bird | Raw Wildmeat | 18 min | 3 min |
| Snow Owl | Bird | Raw Wildmeat | 18 min | 3 min |
| Flamingo | Bird | Raw Fish | 20 min | 3 min |
| Penguin | Bird | Raw Fish | 20 min | 3 min |
| Tortoise | Reptile | Lettuce, Cauliflower | 60 min | 3 min |
| Crocodile | Reptile | Raw Wildmeat | 50 min | 3 min |
| Sand Lizard | Reptile | Insect | 5 min | 3 min |
| Emberwulf | Mythic | Cooked Meat, Raw Wildmeat | 40 min | 3 min |
| Yeti | Mythic | Raw Wildmeat | 50 min | 3 min |
| Fen Stalker | Mythic | Raw Wildmeat | 50 min | 3 min |