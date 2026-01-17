# Hytale Server API Reference

Detailed API patterns discovered through reverse-engineering HytaleServer.jar.

---

## Table of Contents
- [Plugin Lifecycle](#plugin-lifecycle)
- [Commands](#commands)
- [Messages & Colors](#messages--colors)
- [Events](#events)
- [ECS (Entity Component System)](#ecs-entity-component-system)
- [Entity Interactions](#entity-interactions)
- [ContextualUseNPC System](#contextualusenpc-system)
- [Localization & Hints](#localization--hints)
- [NPC Spawning](#npc-spawning)
- [Inventory Management](#inventory-management)
- [Sound System](#sound-system)
- [Entity Detection](#entity-detection)
- [Stale Entity Refs](#stale-entity-refs)
- [Asset Pack Structure](#asset-pack-structure)
- [Class Paths](#class-paths)

---

## Plugin Lifecycle

```java
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public class MyPlugin extends JavaPlugin {
    public MyPlugin(JavaPluginInit init) {
        super(init);  // REQUIRED
    }

    @Override
    protected void setup() { }     // Early initialization

    @Override
    protected void start() { }     // Register commands, systems

    @Override
    protected void shutdown() { }  // Cleanup
}
```

**IMPORTANT:** Does NOT use `onLoad()`, `onEnable()`, `onDisable()` like Bukkit!

### Manifest Format (manifest.json)
```json
{
  "Group": "PluginGroup",
  "Name": "Plugin Display Name",
  "Version": "1.0.0",
  "Description": "Plugin description",
  "Main": "com.example.MyPlugin",
  "ServerVersion": "*",
  "IncludesAssetPack": false,
  "Authors": [{ "Name": "Author", "Email": "", "Url": "" }],
  "Website": ""
}
```

### Logging
```java
getLogger().atInfo().log("Message");
getLogger().atInfo().log("Formatted: %s", value);
getLogger().atWarning().log("Warning message");
getLogger().atSevere().log("Error message");
```

### Verbose Logging Pattern
```java
private static boolean verboseLogging = false;
public static boolean isVerboseLogging() { return verboseLogging; }
public static void setVerboseLogging(boolean enabled) { verboseLogging = enabled; }

private void logVerbose(String message) {
    if (verboseLogging) {
        getLogger().atInfo().log("[MyPlugin] " + message);
    }
}
```

---

## Commands

### Imports
```java
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.Message;
```

### Argument Types
| Type | Description |
|------|-------------|
| `ArgTypes.STRING` | Single string argument |
| `ArgTypes.INTEGER` | Integer argument |
| `ArgTypes.DOUBLE` | Double argument |
| `ArgTypes.FLOAT` | Float argument |
| `ArgTypes.BOOLEAN` | Boolean (true/false) |
| `ArgTypes.PLAYER_REF` | Player reference |
| `ArgTypes.ITEM_ASSET` | Item asset |
| `ArgTypes.forEnum("name", MyEnum.class)` | Enum with tab completion |

### Required Arguments
```java
public class SetGrowthCommand extends AbstractCommand {
    private final RequiredArg<String> animalArg;
    private final RequiredArg<Double> minutesArg;

    public SetGrowthCommand() {
        super("setgrowth", "Set animal growth time");
        animalArg = withRequiredArg("animal", "Animal type name", ArgTypes.STRING);
        minutesArg = withRequiredArg("minutes", "Growth time in minutes", ArgTypes.DOUBLE);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        String animal = ctx.get(animalArg);
        Double minutes = ctx.get(minutesArg);
        return CompletableFuture.completedFuture(null);
    }
}
```

### Optional Arguments
```java
private final OptionalArg<String> categoryArg;

public ListCommand() {
    super("list", "List animals");
    categoryArg = withOptionalArg("category", "Filter by category", ArgTypes.STRING);
}

@Override
protected CompletableFuture<Void> execute(CommandContext ctx) {
    String category = ctx.get(categoryArg);  // null if not provided
}
```

### Enum Arguments (with Tab Completion)
```java
private final RequiredArg<AnimalType> animalArg;

public MyCommand() {
    super("mycommand", "Description");
    animalArg = withRequiredArg("animal", "Animal type",
        ArgTypes.forEnum("animal", AnimalType.class));
}
```

### Sub-Commands Pattern
```java
public class ConfigCommand extends AbstractCommand {
    public ConfigCommand() {
        super("config", "Manage configuration");
        addSubCommand(new ReloadSubCommand());
        addSubCommand(new SaveSubCommand());
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext ctx) {
        showHelp(ctx);
        return CompletableFuture.completedFuture(null);
    }
}
```

### CommandContext Methods
```java
ctx.get(argument)           // Get parsed argument value
ctx.provided(argument)      // Check if optional arg was provided
ctx.getInputString()        // Raw input (avoid - use typed args instead)
ctx.sendMessage(Message)    // Send message to sender
ctx.isPlayer()              // Check if sender is a player
ctx.senderAsPlayerRef()     // Get player entity ref
ctx.sender()                // Get CommandSender
```

### Aliases
```java
public MyCommand() {
    super("mycommand", "Description");
    addAliases("mc", "mycmd");
}
```

---

## Messages & Colors

**IMPORTANT:** `Message.raw("§atext")` does NOT work - the § codes show literally.

```java
// Single color
ctx.sendMessage(Message.raw("Success!").color("#55FF55"));

// Multiple colors using insert()
ctx.sendMessage(Message.raw("Label: ").color("#AAAAAA")
    .insert(Message.raw("value").color("#FFFFFF")));

// Complex formatting
Message msg = Message.raw("Prefix: ").color("#AAAAAA")
    .insert(Message.raw("Middle").color("#FFFFFF"))
    .insert(Message.raw(" - Suffix").color("#AAAAAA"));
```

### Common Hex Colors
| Color | Hex Code | Usage |
|-------|----------|-------|
| Orange | `#FF9900` | Headers, titles |
| Green | `#55FF55` | Success, enabled |
| Red | `#FF5555` | Error, disabled |
| Gray | `#AAAAAA` | Labels, descriptions |
| White | `#FFFFFF` | Values, emphasis |
| Yellow | `#FFFF55` | Warnings, highlights |
| Aqua | `#55FFFF` | Info, special |

---

## Events

### PlayerMouseButtonEvent (RECOMMENDED for click handling)
```java
getEventRegistry().register(PlayerMouseButtonEvent.class, event -> {
    Player player = event.getPlayer();
    MouseButtonType button = event.getMouseButton().mouseButtonType;
    Entity targetEntity = event.getTargetEntity();   // nullable
    Item heldItem = event.getItemInHand();           // Item config (not ItemStack!)

    if (button == MouseButtonType.Right) {
        // Handle right-click
    }
});
```

### MouseButtonType Values
- `MouseButtonType.Left` - Left click
- `MouseButtonType.Right` - Right click
- `MouseButtonType.Middle` - Middle click
- `MouseButtonType.X1`, `MouseButtonType.X2` - Extra buttons

### Event Registration
```java
// Simple events
getEventRegistry().register(SomeEvent.class, event -> { ... });

// Global events (with String key)
getEventRegistry().registerGlobal(PlayerInteractEvent.class, event -> { ... });

// Priority-based
getEventRegistry().register(EventPriority.EARLY, SomeEvent.class, this::handler);
```

### Event Priority Values
- `EventPriority.FIRST` (-21844): Security checks
- `EventPriority.EARLY` (-10922): Data transformation
- `EventPriority.NORMAL` (0): Default
- `EventPriority.LATE` (10922): Logging
- `EventPriority.LAST` (21844): Cleanup

### Available Events

**Player Events** (`com.hypixel.hytale.server.core.event.events.player`):
- `PlayerMouseButtonEvent` - Mouse clicks (RECOMMENDED)
- `PlayerInteractEvent` - DEPRECATED for entity clicks
- `PlayerConnectEvent` / `PlayerDisconnectEvent`
- `PlayerChatEvent`

**ECS Events** (`com.hypixel.hytale.server.core.event.events.ecs`):
- `BreakBlockEvent` / `PlaceBlockEvent`
- `UseBlockEvent` / `UseBlockEvent.Pre` / `UseBlockEvent.Post`
- `DropItemEvent` / `InteractivelyPickupItemEvent`

**Entity Events** (`com.hypixel.hytale.server.core.event.events.entity`):
- `EntityEvent` / `EntityRemoveEvent`
- `LivingEntityInventoryChangeEvent`

### Events That DON'T Work for Animal Spawns
- `PrefabPlaceEntityEvent` - Only fires for structure prefabs
- `LoadedNPCEvent` - Does not fire for animal spawns

**Workaround:** Use periodic scanning (every 30 seconds).

---

## ECS (Entity Component System)

### EntityEventSystem Pattern
```java
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class MyEventHandler extends EntityEventSystem<EntityStore, SomeEvent> {
    public MyEventHandler() {
        super(SomeEvent.class);
    }

    @Override
    public void handle(
        int entityIndex,
        ArchetypeChunk<EntityStore> chunk,
        Store<EntityStore> store,
        CommandBuffer<EntityStore> buffer,
        SomeEvent event
    ) {
        PlayerRef player = chunk.getComponent(entityIndex, PlayerRef.getComponentType());
    }

    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }
}
```

### NewSpawnComponent (UNTESTED)
The ECS has a `NewSpawnComponent` that marks newly spawned entities:
```
com.hypixel.hytale.server.core.modules.entity.component.NewSpawnComponent
```

Related systems:
- `EntitySystems$NewSpawnTick`
- `EntitySystems$NewSpawnEntityTrackerUpdate`
- `NewSpawnStartTickingSystem`

---

## Entity Interactions

### Architecture
```
Item (interactions Map<InteractionType, String>)
  └── RootInteraction (referenced by String ID)
       ├── interactionIds: String[] (Interaction asset IDs)
       └── operations: Operation[] (execution steps)
```

### InteractionType Enum
- `Primary` - Left click
- `Secondary` - Right click
- `Use` - E key
- `Pick` - F key

### Setting Up Entity Interactions
```java
Object interactions = store.ensureAndGetComponent(entityRef, interactionsCompType);

Class<?> interactionTypeClass = Class.forName("com.hypixel.hytale.protocol.InteractionType");
Object useType = null;
for (Object enumConst : interactionTypeClass.getEnumConstants()) {
    if (enumConst.toString().equals("Use")) useType = enumConst;
}

Method setIntId = interactions.getClass().getMethod("setInteractionId", interactionTypeClass, String.class);
setIntId.invoke(interactions, useType, "Root_FeedAnimal");

Method setHint = interactions.getClass().getMethod("setInteractionHint", String.class);
setHint.invoke(interactions, "server.interactionHints.feed");
```

### Modifying Item Interactions at Runtime
```java
Class<?> itemClass = Class.forName("com.hypixel.hytale.server.core.asset.type.item.config.Item");
Object assetStore = itemClass.getMethod("getAssetStore").invoke(null);
Object assetMap = assetStore.getClass().getMethod("getAssetMap").invoke(assetStore);
Object innerMap = assetMap.getClass().getMethod("getAssetMap").invoke(assetMap);
Object item = ((Map<?,?>) innerMap).get("Plant_Crop_Wheat_Item");

Field interactionsField = item.getClass().getDeclaredField("interactions");
interactionsField.setAccessible(true);
Map<Object, Object> oldMap = (Map<Object, Object>) interactionsField.get(item);

Map<Object, Object> newMap = new HashMap<>(oldMap);
newMap.put(useKey, "MyRootInteractionId");
interactionsField.set(item, newMap);
```

### Interaction Asset Format
```json
{
  "Type": "YourInteractionType",
  "Effects": {
    "ItemAnimationId": "Eat",
    "WaitForAnimationToFinish": true,
    "WorldSoundEventId": "SFX_Consume_Bread"
  },
  "Next": {
    "Type": "ModifyInventory",
    "AdjustHeldItemQuantity": -1
  }
}
```

### Operation Types
- `LabelOperation` - Wraps interactions with labels
- `JumpOperation` - Jumps to labeled operations
- `SimpleInteraction` - Basic interaction
- `ReplaceInteraction` - Variable substitution
- `ConditionInteraction` - Conditional checks
- `ChargingInteraction` - Hold-to-charge
- `ModifyInventoryInteraction` - Inventory changes

---

## ContextualUseNPC System

For conditional interactions like shearing sheep or milking cows.

### How It Works
1. **Item** defines `Type: "ContextualUseNPC"` with a `Context` string
2. **NPC Role** defines `HarvestInteractionContext` field
3. System checks if contexts match
4. Match → execute effects; No match → execute `Failed` chain

### Example: Shears on Sheep

**Shears Item:**
```json
{
  "Interactions": {
    "Primary": {
      "Interactions": [{
        "Type": "ContextualUseNPC",
        "Context": "Shear",
        "Effects": {
          "ItemAnimationId": "Shear",
          "WaitForAnimationToFinish": true,
          "WorldSoundEventId": "SFX_Shears_Activate"
        },
        "Failed": "Shears_Attack"
      }]
    }
  }
}
```

**Sheep NPC Role:**
```json
{
  "Type": "Variant",
  "Reference": "Template_Animal_Neutral",
  "Modify": {
    "IsHarvestable": true,
    "HarvestInteractionContext": "Shear",
    "HarvestDropList": "Drop_Sheep_Harvest",
    "HarvestTimeout": ["PT11H", "PT14H"],
    "HarvestSound": "SFX_Sheep_Sheared"
  }
}
```

### NPC Harvest Fields
| Field | Description |
|-------|-------------|
| `IsHarvestable` | Enable harvest mechanics |
| `HarvestInteractionContext` | Context string to match |
| `HarvestDropList` | Drop table reference |
| `HarvestTimeout` | Cooldown (ISO-8601 duration) |
| `HarvestParticles` | Particle effect ID |
| `HarvestSound` | Sound effect ID |
| `HarvestAddItemBucket` | Special item for bucket tools |

### Limitations
- `HarvestInteractionContext` is in NPC Role assets, NOT runtime settable
- One context per NPC type
- Does NOT work for per-item validation (e.g., different foods for animals)

---

## Localization & Hints

### Setting Up Hints
```java
// 1. Entity must have Interactable component
Class<?> interactableClass = Class.forName(
    "com.hypixel.hytale.server.core.modules.entity.component.Interactable");
Object interactableType = interactableClass.getMethod("getComponentType").invoke(null);
store.ensureAndGetComponent(entityRef, interactableType);

// 2. Set hint using localization key
Object interactions = store.ensureAndGetComponent(entityRef, interactionsType);
Method setHint = interactions.getClass().getMethod("setInteractionHint", String.class);
setHint.invoke(interactions, "server.interactionHints.feed");
```

### Built-in Localization Keys
| Key | Display |
|-----|---------|
| `server.interactionHints.generic` | Press [F] to interact |
| `server.interactionHints.mount` | Press [F] to Mount |
| `server.interactionHints.trade` | Press [F] to Trade |
| `server.interactionHints.harvest` | Press [F] to harvest {name} |
| `server.interactionHints.open` | Press [F] to open {name} |

### Custom Localization
**File:** `Server/Languages/en-US/server.lang`
```
interactionHints.feed = Press [{key}] to Feed
interactionHints.breed = Press [{key}] to Breed
```

---

## NPC Spawning

```java
import com.hypixel.hytale.server.npc.NPCPlugin;

int roleIndex = NPCPlugin.get().getIndex("Cow_Calf");

Pair<Ref<EntityStore>, NPCEntity> result = NPCPlugin.get().spawnEntity(
    store,
    roleIndex,
    position,      // Vector3d
    rotation,      // Vector3f
    model,         // Model (optional)
    callback       // Consumer<NPCEntity> (optional)
);
```

### Baby Role IDs
| Animal | Parent Role | Baby Role |
|--------|-------------|-----------|
| Cow | `Cow` | `Cow_Calf` |
| Pig | `Pig` | `Pig_Piglet` |
| Chicken | `Chicken` | `Chicken_Chick` |
| Sheep | `Sheep` | `Sheep_Lamb` |
| Goat | `Goat` | `Goat_Kid` |
| Horse | `Horse` | `Horse_Foal` |
| Camel | `Camel` | `Camel_Calf` |
| Ram | `Ram` | `Ram_Lamb` |
| Turkey | `Turkey` | `Turkey_Chick` |
| Boar | `Boar` | `Boar_Piglet` |
| Rabbit | `Rabbit` | `Bunny` |

### Breeding Foods (from LovedItems)
| Animal | Food Item ID |
|--------|--------------|
| Cow | `Plant_Crop_Cauliflower_Item` |
| Pig | `Plant_Crop_Mushroom_Cap_Brown` |
| Chicken | `Plant_Crop_Corn_Item` |
| Sheep | `Plant_Crop_Lettuce_Item` |
| Goat | `Plant_Fruit_Apple` |
| Horse | `Plant_Crop_Carrot_Item` |
| Camel | `Plant_Crop_Wheat_Item` |

---

## Inventory Management

```java
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

Inventory inventory = ((LivingEntity) player).getInventory();
```

### Sections
```java
ItemContainer hotbar = inventory.getHotbar();
ItemContainer storage = inventory.getStorage();
ItemContainer armor = inventory.getArmor();
ItemContainer utility = inventory.getUtility();
ItemContainer tools = inventory.getTools();
```

### Active Slot & Removing Items
```java
byte activeSlot = inventory.getActiveHotbarSlot();
ItemStack itemInHand = inventory.getItemInHand();

// Remove 1 item
inventory.getHotbar().removeItemStackFromSlot((short) activeSlot, 1);
inventory.markChanged();
player.sendInventory();
```

### In InteractionContext
```java
context.getHeldItemContainer()  // ItemContainer
context.getHeldItemSlot()       // short slot index
context.getHeldItem()           // ItemStack
```

---

## Sound System

```java
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.protocol.SoundCategory;

int soundId = SoundEvent.getAssetMap().getIndex("SFX_Consume_Bread");
Vector3d pos = entity.getTransformComponent().getPosition();
Store<EntityStore> store = world.getEntityStore().getStore();

SoundUtil.playSoundEvent3d(soundId, SoundCategory.SFX, pos.getX(), pos.getY(), pos.getZ(), store);
```

### Common Sound IDs
| Sound ID | Description |
|----------|-------------|
| `SFX_Consume_Bread` | Eating sound |
| `SFX_Cow_Idle` | Cow moo |
| `SFX_Pig_Idle` | Pig oink |
| `SFX_Chicken_Idle` | Chicken cluck |

### Sound Categories
- `SoundCategory.SFX`
- `SoundCategory.MUSIC`
- `SoundCategory.AMBIENT`

---

## Entity Detection

### Reading Entity Model
```java
Class<?> modelCompClass = Class.forName(
    "com.hypixel.hytale.server.core.modules.entity.component.ModelComponent");
Object modelCompType = modelCompClass.getMethod("getComponentType").invoke(null);
Object modelComp = store.getComponent(entityRef, modelCompType);

Field modelField = modelCompClass.getDeclaredField("model");
modelField.setAccessible(true);
Object model = modelField.get(modelComp);

Field assetIdField = model.getClass().getDeclaredField("modelAssetId");
assetIdField.setAccessible(true);
String modelAssetId = (String) assetIdField.get(model);  // "Cow", "Sheep", etc.
```

### Creating Scaled Models
```java
Class<?> modelAssetClass = Class.forName(
    "com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset");
Object assetMap = modelAssetClass.getMethod("getAssetMap").invoke(null);
Object modelAsset = assetMap.getClass().getMethod("getAsset", Object.class).invoke(assetMap, "Wolf");

Class<?> modelClass = Class.forName(
    "com.hypixel.hytale.server.core.asset.type.model.config.Model");
Object model = modelClass.getMethod("createScaledModel", modelAssetClass, float.class)
    .invoke(null, modelAsset, 0.4f);  // 40% scale for baby
```

---

## Stale Entity Refs

Entity refs become invalid when entities despawn.

### Solution
```java
Object component = null;
try {
    component = store.getComponent(entityRef, componentType);
} catch (Exception e) {
    Throwable cause = e;
    if (e instanceof java.lang.reflect.InvocationTargetException) {
        cause = ((java.lang.reflect.InvocationTargetException) e).getTargetException();
    }

    if (cause instanceof IllegalStateException &&
        cause.getMessage() != null &&
        cause.getMessage().contains("Invalid entity")) {
        // Entity despawned - clean up
        removeTrackingData(entityId);
        return;
    }
    throw e;
}
```

### Best Practices
1. Always wrap ref usage in try-catch
2. Clean up tracking data when ref is stale
3. Don't store refs long-term - store entity IDs instead
4. Use `world.execute()` for entity operations

---

## Asset Pack Structure

When `IncludesAssetPack: true`:
```
my-mod.jar
├── manifest.json
└── assets/
    └── Server/
        ├── Item/
        │   ├── RootInteractions/Root_FeedAnimal.json
        │   ├── Interactions/FeedAnimal.json
        │   └── Items/MyItem.json
        ├── NPC/
        │   └── Roles/MyNPC.json
        └── Languages/
            └── en-US/server.lang
```

### Key Asset Paths
| Type | Path |
|------|------|
| RootInteraction | `Server/Item/RootInteractions/{id}.json` |
| Interaction | `Server/Item/Interactions/{id}.json` |
| Item | `Server/Item/Items/{category}/{id}.json` |
| NPC Role | `Server/NPC/Roles/{id}.json` |

---

## Class Paths

| Component | Path |
|-----------|------|
| `JavaPlugin` | `com.hypixel.hytale.server.core.plugin.JavaPlugin` |
| `JavaPluginInit` | `com.hypixel.hytale.server.core.plugin.JavaPluginInit` |
| `AbstractCommand` | `com.hypixel.hytale.server.core.command.system.AbstractCommand` |
| `CommandContext` | `com.hypixel.hytale.server.core.command.system.CommandContext` |
| `Message` | `com.hypixel.hytale.server.core.Message` |
| `Model` | `com.hypixel.hytale.server.core.asset.type.model.config.Model` |
| `ModelAsset` | `com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset` |
| `ModelComponent` | `com.hypixel.hytale.server.core.modules.entity.component.ModelComponent` |
| `TransformComponent` | `com.hypixel.hytale.server.core.modules.entity.component.TransformComponent` |
| `Interactions` | `com.hypixel.hytale.server.core.modules.interaction.Interactions` |
| `InteractionType` | `com.hypixel.hytale.protocol.InteractionType` |
| `NPCPlugin` | `com.hypixel.hytale.server.npc.NPCPlugin` |
| `EntityStore` | `com.hypixel.hytale.server.core.universe.world.storage.EntityStore` |
| `PlayerRef` | `com.hypixel.hytale.server.core.universe.PlayerRef` |
| `ItemStack` | `com.hypixel.hytale.server.core.inventory.ItemStack` |
| `SoundEvent` | `com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent` |
| `SoundUtil` | `com.hypixel.hytale.server.core.universe.world.SoundUtil` |
