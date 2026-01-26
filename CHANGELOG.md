# Changelog

## v1.4.0 - Taming Integration

### Added
- **Taming** - Merged with TheBrandolorian's "Tameable Animals" to add taming to the mod. Now animals need to be tamed before you can breed and name them.
- **Animal Persistence** - All tamed animals are now persisted and should be restored if they are removed for some reason. Only dead animals won't respawn.
- **Food-based Taming & Healing** - Feed taming food to wild animals to tame them. The configuration now defines base food which can be overridden by taming food, healing food and breeding food.
- **Attitude Field** - Tamed animals now have REVERED attitude (won't attack owner)

---

## v1.3.2 - Solo Mode Hint Fix & Stability

### Fixed
- Interaction hints ("Press [F] to Feed") now display correctly in solo/local server mode
  - Added missing Interactable component to entities (required for hint display)
- Fixed "[StateUpdate] Error updating Rabbit: null" spam in logs
- Fixed "[AnimalScan] UUID check failed" errors for fast-despawning entities
- Stale entity references are now properly cleaned up when animals despawn
- Added isValid() checks throughout to prevent NPEs from despawned entities

---

## v1.3.1 - Bug Fixes & Logging Cleanup

### Fixed
- Debug logs now only print when verbose mode is enabled (`/breedlogs` command)
- Reduced console spam during normal gameplay
- Startup/shutdown messages remain visible

---

## v1.3.0 - Fixes + Preview for Taming & Entity Persistence

#### Deprecated Commands
The following legacy commands still work but show deprecation warnings. Use the unified `/breed` command instead:

- `/laitsbreeding` → `/breed help`
- `/breedstatus` → `/breed status`
- `/breedconfig` → `/breed config`

### Fixed
- Various performance improvements
- Various bug fixes
- Horse feeding and mounting should now work (might still need to hit them once like in the base game)
- Various bugs caused by changing the model of the player or spawning a model 

### New Features Preview (some features might not work)

#### Animal Taming System
- **Name Tags** - You can now craft name tags at the farming bench and tame an animal
- **Random cute names** - Animals get random names like Fluffy, Spot, Buddy, Luna, etc.
- **Ownership system** - Tamed animals belong to the player who tamed them

#### Entity Persistence
- **Persistent taming data** - Tamed animals are saved to `tamed_animals.json` and survive server restarts
- **Respawn system** - Tamed animals that despawn will respawn at their last position when a player approaches (64 block radius)
- **State preservation** - Growth stage, breeding cooldowns, and all taming data are preserved across respawns
- **Auto-save** - Data auto-saves every 5 minutes
- **Shutdown save** - Data is always saved on server shutdown

#### Custom Animals (from other mods or future updates)
- **Role-first registration** - `/breed custom add <npcRole> <food>` register by NPC role
- **Auto model discovery** - Plugin validates the role exists, spawns a temp entity, and auto-discovers the model asset ID
- **Baby role mapping** - New `/breed custom setbaby <model> <babyRole>` command for dedicated baby NPC spawning
- **Scaling fallback** - If no baby role defined, babies spawn as scaled-down adults (40% size)
- **Custom animal spawn detection** - Custom animals are now detected immediately when they spawn (same as built-in animals)

---

## v1.3.0-pre - Taming & Entity Persistence

### New Commands
| Command | Description |
|---------|-------------|
| `/nametag <name>` | Set pending name tag, then right-click animal to tame |
| `/taminginfo` | Show taming stats and list your tamed animals |
| `/tamingsettings` | Toggle whether others can interact with your animals |
| `/untame` | Right-click to release a tamed animal (owner only) |
| `/breed custom setbaby <model> <babyRole>` | Set baby NPC role for custom animal breeding |

### New Files
- `TamedAnimalData.java` - Data model for tamed animals
- `TamingManager.java` - Manages taming state and ownership
- `PersistenceManager.java` - JSON save/load system
- `Server/Item/Items/Misc/NameTag.json` - Name Tag item asset

---

## v1.2.1 - Heart Particles & Spawn Detection

### New Features
- **Heart particles** - Pink hearts now appear above animals when they're in love mode
- **Instant spawn detection** - Animals are detected immediately when they spawn using EntityTickingSystem (no more waiting for periodic scans)
- **Debug mode command** - Use `/breeddev` to toggle in-game chat logging for easier debugging

### Fixed
- **Breeding cooldown bypass** - Fixed bug where feeding during cooldown could still trigger breeding
- **Horse interaction** - Partially fixed horse interactions (remaining bug is already in the base game)
- **Spawn detector timing** - Moved ECS system registration to proper lifecycle phase

### Changed
- Periodic animal scan reduced to safety net only (30 seconds) - primary detection is now instant
- Debug logs now broadcast to all players when dev mode is enabled

---

## v1.2.0 - Complete Creature Expansion

### New Features

#### Massive Animal Expansion
- **119 animals** now configurable (up from 53)
- **10 categories** (up from 7): LIVESTOCK, MAMMAL, CRITTER, AVIAN, REPTILE, VERMIN, AQUATIC, MYTHIC, DINOSAUR, BOSS

#### New Categories
- **VERMIN** - Creepy crawlies: Rat, Molerat, Scorpion, Spider, Snakes, Slugs, Snails, Larva
- **AQUATIC** - Fish and sea creatures: Salmon, Catfish, Piranha, Jellyfish, Crab, Lobster, Pufferfish, Shark, Whale
- **BOSS** - Epic creatures: Fire Dragon, Frost Dragon

#### New Animals by Category

**Mammals (6 new)**
- Antelope, Armadillo, Snow Leopard, Mosshorn, Mosshorn Plain, Sabertooth Tiger

**Birds (9 new)**
- Bat, Ice Bat, Bluebird, Green Finch, Sparrow, Woodpecker, Hawk, Vulture, Tetrabird

**Reptiles (2 new)**
- Rhino Toad, Magma Rhino Toad

**Mythic (5 new)**
- Cactee, Hatworm, Snapdragon, Living Spark, Trillodon

**Vermin (12 new)**
- Rat, Molerat, Silk Larva, Scorpion, Magma Slug, Frost Snail, Magma Snail
- Cobra, Marsh Snake, Rattlesnake, Spider, Cave Spider

**Aquatic (30 new)**
- Fish: Bluegill, Catfish, Frostgill, Minnow, Pike, Piranha, Black Piranha, Salmon, Snapjaw, Rainbow Trout
- Marine: Clownfish, Crab, Pufferfish, Lobster, Blue Tang, Chevron Tang, Lemon Peel Tang, Sailfin Tang
- Jellyfish: Blue, Cyan, Green, Red, Yellow, Man of War
- Deep: Moray Eel, Hammerhead Shark, Lava Shellfish, Trilobite, Black Trilobite, Humpback Whale

**Bosses (2 new)**
- Fire Dragon, Frost Dragon

### Fixed
- Replaced all fake item IDs with correct game assets:
  - `Food_Insect` → `Plant_Fruit_Berries_Red` (for critters)
  - `Food_Meat_Cooked` → `Food_Wildmeat_Cooked`
  - `Food_Fish_Cooked` → `Food_Fish_Grilled`
  - `Plant_Crop_Cactus_Fruit` → `Plant_Cactus_Flower`

### Changed
- All new animals disabled by default (enable via `/breedconfig enable <animal>`)
- Growth times scaled by creature size (small critters: 3-5 min, large bosses: 180 min)
- Updated GAME_ASSETS_REFERENCE.md with complete NPC and food item lists
- New `all` preset enables all 119 animals at once
- Zoo preset now includes aquatic creatures, excludes vermin and bosses
- **Auto-update presets on startup** - Existing preset files automatically gain new animals with correct values
- **New command: `/breedconfig preset restore <name>`** - Reset any built-in preset to its default values

### Upgrade Notes
- Preset files are automatically updated with new animals on startup (no manual action needed)
- Use `/breedconfig preset restore <name>` to reset a preset to factory defaults
- Your existing `laits-breeding-config.json` will automatically gain the new animals on next save

---

## v1.1.1

### New Features
- **Dinosaurs!** - 4 new prehistoric creatures added:
  - **Raptor_Cave** - Velociraptor-like predator (35 min growth)
  - **Rex_Cave** - T-Rex, the king of dinosaurs (60 min growth, 10 min cooldown)
  - **Archaeopteryx** - Small flying dinosaur (20 min growth)
  - **Pterodactyl** - Large flying dinosaur (40 min growth)
- **New category: DINOSAUR** - Prehistoric creatures are now their own category
- **New preset: `default_extended`** - Default timings (30 min growth, 5 min cooldown) with multiple food options from `lait_curated`. This is now the default preset for new installations.
- **Disable baby growth** - New `/breedgrowth` command to toggle baby growth on/off. When disabled, babies will stay babies forever.
- **`growthEnabled` config option** - Persisted in config file, can be set via command or JSON.

### Changed
- Default preset is now `default_extended` instead of `default`
- Debug logs in FeedAnimalInteraction now respect verbose logging setting (use `/breedlogs` to enable)
- Zoo preset now includes dinosaurs (they're prehistoric, not mythical!)
- Total animal types increased to 53 (from 49)

### Fixed
- Fixed feeding interaction not working after code refactoring
- Improved error logging for interaction setup

### Misc
- Switched to MIT License
- Added `/breedscan` command for manual animal scan (debugging)

### Dinosaur Breeding Foods
- **Raptor_Cave**: Cooked wildmeat, raw wildmeat
- **Rex_Cave**: Cooked wildmeat, raw wildmeat
- **Archaeopteryx**: Raw wildmeat, red berries
- **Pterodactyl**: Raw fish, grilled fish, raw wildmeat

## v1.1.0 - Configuration System & Extended Animals

### New Features

#### Configuration System
- **JSON config file** (`mods/laits-breeding-config.json`) for persistent settings
- **Runtime configuration** via `/breedconfig` command
- **Multiple breeding foods** per animal - each animal can now accept multiple food items
- **External preset files** - Presets stored as editable JSON files in `mods/presets/`
  - Built-in presets (`default.json`, `lait_curated.json`, `zoo.json`) auto-generated on first load
  - Create custom presets by copying and editing JSON files
- **Preset system** with three built-in presets:
  - `default` - Streamlined experience with original game values
  - `lait_curated` - Organic, natural experience with multiple foods and logical growth times
  - `zoo` - All real-world animals enabled (excludes mythic creatures)

#### Extended Animal Support
- **49 animal types** now configurable (up from 11)
- **Animal categories**: LIVESTOCK, MAMMAL, CRITTER, AVIAN, REPTILE, MYTHIC
- **Wild animals** can be enabled via config (disabled by default)
- **Wild animal breeding** - Animals without baby variants spawn as scaled babies
- New livestock: Bison, Desert Chicken, Mouflon, Wild Pig, Skrill, Warthog
- Wild mammals: Wolf, Fox, Bear, Deer, Moose, Hyena, etc.
- Birds: Duck, Pigeon, Parrot, Owl, Penguin, Flamingo, etc.
- Critters: Frog, Gecko, Mouse, Squirrel, Meerkat
- Reptiles: Tortoise, Crocodile, Lizard
- Mythic: Emberwulf, Yeti, Fen Stalker

#### New Commands
- `/breedconfig` - Show config summary with category counts
- `/breedconfig list [category]` - List animals, optionally filtered by category
- `/breedconfig info <animal>` - Show detailed animal config
- `/breedconfig preset list` - Show available presets
- `/breedconfig preset apply <name>` - Apply a preset
- `/breedconfig preset save <name>` - Save current config as new preset
- `/breedconfig reload` - Reload config from file
- `/breedconfig save` - Save current config to file
- `/breedconfig enable <animal|category|ALL>` - Enable breeding
- `/breedconfig disable <animal|category|ALL>` - Disable breeding
- `/breedconfig set <animal> food <item>` - Set primary breeding food
- `/breedconfig set <animal> growth <minutes>` - Set growth time
- `/breedconfig set <animal> cooldown <minutes>` - Set cooldown
- `/breedconfig addfood <animal> <item>` - Add breeding food
- `/breedconfig removefood <animal> <item>` - Remove breeding food

### Changed
- **Picky eaters** - Animals now only accept their configured foods, rejecting everything else
- Breeding food validation now uses ConfigManager (supports runtime changes)
- Growth times now configurable per animal type
- Cooldowns now configurable per animal type
- Breeding radius increased from 3 to 5 blocks

### Fixed
- **Horse mounting** - Feeding horses with wrong food now falls back to mounting instead of blocking interaction
- Disabled animals no longer show "Press [F] to Feed" hint
- Replaced invalid `Food_Meat_Raw` with `Food_Wildmeat_Raw`

### Lait's Curated Preset Highlights
- **Cows**: Accept wheat, cauliflower, or lettuce (25 min growth)
- **Pigs**: Accept carrots, potatoes, mushrooms, or apples (15 min growth)
- **Chickens**: Accept corn, wheat, or rice (10 min growth)
- **Rabbits**: Accept carrots, lettuce, or apples (8 min growth, 1 min cooldown)
- **Mice**: 3 min growth, 30 sec cooldown (breed fast!)
- **Horses**: 30 min growth (large animal)
- **Tortoises**: 60 min growth (slow and steady)

---

## v1.0.1 - Bug Fixes

### Fixed
- Growth time increased from 20 minutes to 30 minutes (15 min baby + 15 min juvenile)

---

## v1.0.0 - Initial Release

### Features
- Feed animals to put them in love mode
- Proximity-based breeding - two animals in love will automatically breed when near each other
- Baby animals spawn instantly after breeding
- Babies grow into adults after 20 minutes
- Wild baby animals also grow into adults
- 5 minute breeding cooldown between cycles

### Supported Animals
- Cow (Cauliflower)
- Pig (Brown Mushroom)
- Chicken (Corn)
- Sheep (Lettuce)
- Goat (Apple)
- Horse (Carrot)
- Camel (Wheat)
- Ram (Apple)
- Turkey (Corn)
- Boar (Red Mushroom)
- Rabbit (Carrot)

### Commands
- `/laitsbreeding` - Show help and breeding foods
- `/breedstatus` - View tracked animals and breeding stats
