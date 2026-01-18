# Hytale Server API Reference

Detailed API patterns discovered through reverse-engineering HytaleServer.jar.

**Note:** Most classes can be imported and used directly - avoid reflection unless necessary.

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
- [Particles](#particles)
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

### Command Example
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
        ctx.sendMessage(Message.raw("Set!").color("#55FF55"));
        return CompletableFuture.completedFuture(null);
    }
}
```

### CommandContext Methods
```java
ctx.get(argument)           // Get parsed argument value
ctx.provided(argument)      // Check if optional arg was provided
ctx.sendMessage(Message)    // Send message to sender
ctx.isPlayer()              // Check if sender is a player
ctx.senderAsPlayerRef()     // Get player entity ref
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
```

### Common Hex Colors
| Color | Hex Code | Usage |
|-------|----------|-------|
| Orange | `#FF9900` | Headers, titles |
| Green | `#55FF55` | Success, enabled |
| Red | `#FF5555` | Error, disabled |
| Gray | `#AAAAAA` | Labels, descriptions |
| White | `#FFFFFF` | Values, emphasis |

---

## Events

### Imports
```java
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.event.events.entity.EntityRemoveEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.protocol.MouseButtonType;
```

### PlayerMouseButtonEvent
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

### Event Registration
```java
// Simple events
getEventRegistry().register(SomeEvent.class, event -> { ... });

// Global events
getEventRegistry().registerGlobal(PlayerInteractEvent.class, event -> { ... });
```

---

## ECS (Entity Component System)

### Imports
```java
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
```

### Component Types (Direct Usage)
These components support direct `getComponentType()` calls. Cache as static fields:
```java
private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
    TransformComponent.getComponentType();
private static final ComponentType<EntityStore, ModelComponent> MODEL_TYPE =
    ModelComponent.getComponentType();
private static final ComponentType<EntityStore, UUIDComponent> UUID_TYPE =
    UUIDComponent.getComponentType();
```

### Getting Components
```java
// Get the store from world
Store<EntityStore> store = world.getEntityStore().getStore();

// Get component directly using cached type
TransformComponent transform = store.getComponent(entityRef, TRANSFORM_TYPE);
Vector3d position = transform.getPosition();

// Get UUID from entity
UUIDComponent uuidComp = store.getComponent(entityRef, UUID_TYPE);
UUID entityUuid = uuidComp.getUuid();
```

### Getting Store from World
```java
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

World world = Universe.get().getDefaultWorld();
Store<EntityStore> store = world.getEntityStore().getStore();
```

### Deferred Entity Operations
Use `world.execute()` when modifying entities outside of ECS tick:
```java
world.execute(() -> {
    Store<EntityStore> store = world.getEntityStore().getStore();
    // Safe to modify entity components here
});
```

---

## Entity Interactions

### Imports
```java
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
```

### InteractionType Values
```java
InteractionType.Primary   // Left click
InteractionType.Secondary // Right click
InteractionType.Use       // E key (default interact)
InteractionType.Pick      // F key
```

### Setting Entity Interactions
**Note:** Component type access and Interactions methods require reflection:
```java
// Get component types via reflection
Object interactableType = Interactable.class.getMethod("getComponentType").invoke(null);
Object interactionsType = Interactions.class.getMethod("getComponentType").invoke(null);

// Ensure entity has Interactable component (required for hints)
store.ensureAndGetComponent(entityRef, interactableType);

// Get Interactions component
Object interactions = store.ensureAndGetComponent(entityRef, interactionsType);

// Set interaction ID (reflection needed - methods not public)
Method setIntId = interactions.getClass().getMethod(
    "setInteractionId", InteractionType.class, String.class);
setIntId.invoke(interactions, InteractionType.Use, "Root_FeedAnimal");

// Set hint
Method setHint = interactions.getClass().getMethod("setInteractionHint", String.class);
setHint.invoke(interactions, "server.interactionHints.feed");
```

### Custom Interactions
Create custom interaction types by extending SimpleInteraction:
```java
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.entity.InteractionContext;

public class FeedAnimalInteraction extends SimpleInteraction {
    public static final BuilderCodec<FeedAnimalInteraction> CODEC =
        BuilderCodec.builder(FeedAnimalInteraction.class, FeedAnimalInteraction::new, SimpleInteraction.CODEC)
            .build();

    @Override
    protected void tick0(boolean firstRun, float time, InteractionType type,
                         InteractionContext context, CooldownHandler cooldownHandler) {
        if (firstRun) {
            Ref<EntityStore> targetRef = context.getTargetEntity();
            ItemStack heldItem = context.getHeldItem();
            // Handle interaction logic
        }
        super.tick0(firstRun, time, type, context, cooldownHandler);
    }
}
```

### Interaction Assets
**RootInteraction:** `Server/Item/RootInteractions/Root_FeedAnimal.json`
```json
{
  "Interactions": ["FeedAnimal"],
  "RequireNewClick": true
}
```

**Interaction:** `Server/Item/Interactions/FeedAnimal.json`
```json
{
  "Type": "FeedAnimal",
  "Effects": {
    "ItemAnimationId": "Eat",
    "WaitForAnimationToFinish": true
  }
}
```

---

## Localization & Hints

### Custom Localization
**File:** `Server/Languages/en-US/server.lang`
```
interactionHints.feed = Press [{key}] to Feed
interactionHints.feedOrMount = Press [{key}] to Feed / Mount
```

### Built-in Keys
| Key | Display |
|-----|---------|
| `server.interactionHints.generic` | Press [F] to interact |
| `server.interactionHints.mount` | Press [F] to Mount |
| `server.interactionHints.trade` | Press [F] to Trade |

---

## NPC Spawning

### Imports
```java
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
```

### Spawning NPCs
```java
NPCPlugin npcPlugin = NPCPlugin.get();
int roleIndex = npcPlugin.getIndex("Cow_Calf");

// spawnEntity requires reflection due to complex callback signature
for (Method m : NPCPlugin.class.getMethods()) {
    if (m.getName().equals("spawnEntity") && m.getParameterCount() == 6) {
        // Create no-op callback proxy
        Class<?> callbackClass = m.getParameterTypes()[5];
        Object callback = Proxy.newProxyInstance(
            callbackClass.getClassLoader(),
            new Class<?>[] { callbackClass },
            (proxy, method, args) -> null
        );
        m.invoke(npcPlugin, store, roleIndex, position, rotation, scaledModel, callback);
        break;
    }
}
```

### Creating Scaled Models
```java
ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset("Wolf");
Model scaledModel = Model.createScaledModel(modelAsset, 0.4f);  // 40% scale
```

### Baby Role IDs
| Animal | Baby Role |
|--------|-----------|
| Cow | `Cow_Calf` |
| Pig | `Pig_Piglet` |
| Chicken | `Chicken_Chick` |
| Sheep | `Sheep_Lamb` |
| Horse | `Horse_Foal` |

---

## Inventory Management

### Imports
```java
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
```

### Working with Inventory
```java
Inventory inventory = ((LivingEntity) player).getInventory();
byte activeSlot = inventory.getActiveHotbarSlot();
ItemStack itemInHand = inventory.getItemInHand();

// Remove 1 item from active slot
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

### Imports
```java
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.protocol.SoundCategory;
```

### Playing Sounds
```java
int soundId = SoundEvent.getAssetMap().getIndex("SFX_Consume_Bread");
Vector3d pos = transform.getPosition();

// Play 3D sound at position
SoundUtil.playSoundEvent3d(soundId, SoundCategory.SFX,
    pos.getX(), pos.getY(), pos.getZ(), store);
```

---

## Particles

### Imports
```java
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
```

### Spawning Particles
**Note:** ParticleUtil methods require reflection to find the correct signature:
```java
Vector3d position = new Vector3d(x, y, z);

// Find and invoke spawnParticleEffect via reflection
for (Method method : ParticleUtil.class.getMethods()) {
    if (method.getName().equals("spawnParticleEffect") && method.getParameterCount() == 3) {
        Class<?>[] params = method.getParameterTypes();
        if (params[0] == String.class && params[1].getSimpleName().equals("Vector3d")) {
            method.invoke(null, "BreedingHearts", position, store);
            break;
        }
    }
}
```

### Custom Particle Assets
**ParticleSystem:** `Server/Particles/BreedingHearts.particlesystem`
```json
{
  "SpawnerId": "BreedingHearts"
}
```

**ParticleSpawner:** `Server/Particles/Spawners/BreedingHearts.particlespawner`
```json
{
  "Particle": {
    "Texture": "Particles/Textures/Shapes/Hearts_HiRes.png",
    "Color": "#fbbbfc"
  },
  "SpawnRate": { "Min": 5, "Max": 10 },
  "ParticleLifeSpan": { "Min": 0.8, "Max": 1 }
}
```

---

## Entity Detection

### Reading Model Asset ID
```java
ModelComponent modelComp = store.getComponent(entityRef, MODEL_TYPE);

// Get modelAssetId via reflection (field is private)
Field modelField = ModelComponent.class.getDeclaredField("model");
modelField.setAccessible(true);
Object model = modelField.get(modelComp);

// Parse from toString() or use reflection on modelAssetId field
String modelStr = model.toString();
// Extract modelAssetId from: "Model{modelAssetId='Cow', ...}"
```

---

## Stale Entity Refs

Entity refs become invalid when entities despawn.

### Handling Invalid Refs
```java
try {
    TransformComponent transform = store.getComponent(entityRef, TRANSFORM_TYPE);
} catch (IllegalStateException e) {
    if (e.getMessage() != null && e.getMessage().contains("Invalid entity")) {
        // Entity despawned - clean up tracking data
        return;
    }
    throw e;
}
```

### Best Practices
1. Always wrap ref usage in try-catch
2. Clean up tracking data when ref is stale
3. Store entity UUIDs instead of refs for long-term tracking
4. Use `world.execute()` for deferred entity operations

---

## Asset Pack Structure

When `IncludesAssetPack: true`:
```
my-mod.jar
├── manifest.json
└── Server/
    ├── Item/
    │   ├── RootInteractions/Root_FeedAnimal.json
    │   └── Interactions/FeedAnimal.json
    ├── Particles/
    │   ├── MyParticle.particlesystem
    │   └── Spawners/MyParticle.particlespawner
    └── Languages/
        └── en-US/server.lang
```

---

## Class Paths

### Core
| Class | Path |
|-------|------|
| `JavaPlugin` | `com.hypixel.hytale.server.core.plugin.JavaPlugin` |
| `Message` | `com.hypixel.hytale.server.core.Message` |
| `Universe` | `com.hypixel.hytale.server.core.universe.Universe` |
| `World` | `com.hypixel.hytale.server.core.universe.world.World` |

### ECS
| Class | Path |
|-------|------|
| `Store` | `com.hypixel.hytale.component.Store` |
| `Ref` | `com.hypixel.hytale.component.Ref` |
| `ComponentType` | `com.hypixel.hytale.component.ComponentType` |
| `EntityStore` | `com.hypixel.hytale.server.core.universe.world.storage.EntityStore` |

### Components (Direct getComponentType())
| Class | Path |
|-------|------|
| `TransformComponent` | `com.hypixel.hytale.server.core.modules.entity.component.TransformComponent` |
| `ModelComponent` | `com.hypixel.hytale.server.core.modules.entity.component.ModelComponent` |
| `UUIDComponent` | `com.hypixel.hytale.server.core.entity.UUIDComponent` |

### Components (Require Reflection for getComponentType())
| Class | Path |
|-------|------|
| `Interactable` | `com.hypixel.hytale.server.core.modules.entity.component.Interactable` |
| `Interactions` | `com.hypixel.hytale.server.core.modules.interaction.Interactions` |

### Interactions
| Class | Path |
|-------|------|
| `InteractionType` | `com.hypixel.hytale.protocol.InteractionType` |
| `SimpleInteraction` | `com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction` |
| `RootInteraction` | `com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction` |
| `InteractionContext` | `com.hypixel.hytale.server.core.entity.InteractionContext` |

### Assets
| Class | Path |
|-------|------|
| `Model` | `com.hypixel.hytale.server.core.asset.type.model.config.Model` |
| `ModelAsset` | `com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset` |
| `SoundEvent` | `com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent` |
| `SoundUtil` | `com.hypixel.hytale.server.core.universe.world.SoundUtil` |
| `ParticleUtil` | `com.hypixel.hytale.server.core.universe.world.ParticleUtil` |
| `NPCPlugin` | `com.hypixel.hytale.server.npc.NPCPlugin` |
