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
- [NPC Behavior Trees](#npc-behavior-trees)
- [NPC Attitude System](#npc-attitude-system)
- [NPC Spawn Tracking](#npc-spawn-tracking)
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
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.entity.EntityRemoveEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
```

### Event Registration
```java
// Simple events
getEventRegistry().register(SomeEvent.class, event -> { ... });

// Global events
getEventRegistry().registerGlobal(SomeEvent.class, event -> { ... });
```

### PlayerConnectEvent
```java
getEventRegistry().register(PlayerConnectEvent.class, event -> {
    // Player just connected - good time to scan nearby entities
});
```

### Notes
- `PlayerMouseButtonEvent` - Does NOT work for entity interactions
- Use custom `Interaction` classes (extending `SimpleInteraction`) for handling entity clicks

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

### Custom ECS Components

Create persistent components that save/load with the world:

```java
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;

public class TameComponent implements Component<EntityStore> {
    // Define codec for persistence (keys are saved to world data)
    public static final BuilderCodec<TameComponent> CODEC = BuilderCodec.builder(TameComponent.class, TameComponent::new)
        .append(new KeyedCodec<>("IsTamed", Codec.BOOLEAN), (data, val) -> data.isTamed = val, data -> data.isTamed)
        .add()
        .append(new KeyedCodec<>("TamerUUID", Codec.UUID_BINARY), (data, val) -> data.tamerUUID = val, data -> data.tamerUUID)
        .add()
        .append(new KeyedCodec<>("TamerName", Codec.STRING), (data, val) -> data.tamerName = val, data -> data.tamerName)
        .add()
        .build();

    private Boolean isTamed = false;  // Use boxed Boolean for null safety
    private UUID tamerUUID = null;
    private String tamerName = null;

    // Getters/setters...

    @Override
    public Component<EntityStore> clone() {
        TameComponent copy = new TameComponent();
        copy.isTamed = this.isTamed;
        copy.tamerUUID = this.tamerUUID;
        copy.tamerName = this.tamerName;
        return copy;
    }
}
```

**Register in plugin setup():**
```java
private ComponentType<EntityStore, TameComponent> tameComponentType;

@Override
protected void setup() {
    tameComponentType = this.getEntityStoreRegistry().registerComponent(
        TameComponent.class, "Tame", TameComponent.CODEC);
}
```

**Usage:**
```java
// Get component (returns null if missing)
TameComponent comp = store.getComponent(entityRef, tameComponentType);

// Get or create component
TameComponent comp = store.ensureAndGetComponent(entityRef, tameComponentType);

// From Holder (in systems)
TameComponent comp = holder.getComponent(tameComponentType);
TameComponent comp = holder.ensureAndGetComponent(tameComponentType);
```

### ECS Systems (HolderSystem)

Create systems that run on entity lifecycle events:

```java
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.server.npc.systems.RoleBuilderSystem;

public class TameActivateSystem extends HolderSystem<EntityStore> {
    private final ComponentType<EntityStore, NPCEntity> npcType;
    private final ComponentType<EntityStore, TameComponent> tameType;
    private final Query<EntityStore> query;
    private final Set<Dependency<EntityStore>> dependencies;

    public TameActivateSystem() {
        this.npcType = NPCEntity.getComponentType();
        this.tameType = TameComponent.getComponentType();

        // Query: match entities with NPCEntity but NOT NPCMountComponent
        this.query = Query.and(npcType, Query.not(NPCMountComponent.getComponentType()));

        // Run AFTER RoleBuilderSystem
        this.dependencies = Set.of(new SystemDependency<>(Order.AFTER, RoleBuilderSystem.class));
    }

    @Override
    public Query<EntityStore> getQuery() { return this.query; }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() { return this.dependencies; }

    @Override
    public void onEntityAdd(Holder<EntityStore> holder, AddReason reason, Store<EntityStore> store) {
        NPCEntity npc = holder.getComponent(npcType);
        TameComponent tame = holder.ensureAndGetComponent(tameType);

        if (tame.isTamed()) {
            // Entity was loaded from world in tamed state
            // Apply runtime effects...
        }
    }

    @Override
    public void onEntityRemoved(Holder<EntityStore> holder, RemoveReason reason, Store<EntityStore> store) {
        // Cleanup if needed
    }
}
```

**Register in plugin start():**
```java
@Override
protected void start() {
    this.getEntityStoreRegistry().registerSystem(new TameActivateSystem());
}
```

---

## NPC Behavior Trees

### Core Components (Actions & Sensors)

Register custom actions/sensors for NPC behavior trees:

```java
@Override
protected void start() {
    NPCPlugin.get().registerCoreComponentType("Tame", BuilderActionTame::new);
    NPCPlugin.get().registerCoreComponentType("Tamed", BuilderSensorTamed::new);
    NPCPlugin.get().registerCoreComponentType("RemovePlayerHeldItems", BuilderActionRemovePlayerHeldItems::new);
}
```

### Custom Action

```java
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;

public class ActionTame extends ActionBase {
    public ActionTame(BuilderActionTame builder, BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean execute(Ref<EntityStore> ref, Role role, InfoProvider sensorInfo,
                          double dt, Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);

        // Get player who triggered interaction
        Ref<EntityStore> playerRef = role.getStateSupport().getInteractionIterationTarget();
        Player player = store.getComponent(playerRef, Player.getComponentType());
        UUIDComponent playerUUID = store.getComponent(playerRef, UUIDComponent.getComponentType());

        // Do action logic...
        return true;  // true = success
    }
}
```

### Custom Action Builder

```java
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.asset.builder.holder.StringArrayHolder;

public class BuilderActionTame extends BuilderActionBase {
    protected StringArrayHolder lovedFoodHolder = new StringArrayHolder();

    @Override
    public String getShortDescription() { return "Tame the entity"; }

    @Override
    public String getLongDescription() { return getShortDescription(); }

    @Override
    public BuilderDescriptorState getBuilderDescriptorState() { return BuilderDescriptorState.Stable; }

    @Override
    public ActionTame build(BuilderSupport support) {
        return new ActionTame(this, support);
    }

    @Override
    public Builder<Action> readConfig(JsonElement data) {
        this.requireStringArray(data, "Food", lovedFoodHolder, 1, Integer.MAX_VALUE, null,
            BuilderDescriptorState.Stable, "Foods for taming", "Description");
        return super.readConfig(data);
    }

    public String[] getLovedFood(BuilderSupport support) {
        return lovedFoodHolder.get(support.getExecutionContext());
    }
}
```

### Custom Sensor

```java
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;

public class SensorTamed extends SensorBase {
    protected final boolean expectedValue;

    public SensorTamed(BuilderSensorTamed builder, BuilderSupport support) {
        super(builder);
        this.expectedValue = builder.getValue(support);
    }

    @Override
    public boolean matches(Ref<EntityStore> ref, Role role, double dt, Store<EntityStore> store) {
        TameComponent tame = store.getComponent(ref, TameComponent.getComponentType());
        if (tame == null) return false;

        return super.matches(ref, role, dt, store) && tame.isTamed() == expectedValue;
    }

    @Override
    public InfoProvider getSensorInfo() { return null; }
}
```

### Custom Sensor Builder

```java
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase;
import com.hypixel.hytale.server.npc.asset.builder.holder.BooleanHolder;

public class BuilderSensorTamed extends BuilderSensorBase {
    protected final BooleanHolder value = new BooleanHolder();

    @Override
    public Sensor build(BuilderSupport support) {
        return new SensorTamed(this, support);
    }

    @Override
    public Builder<Sensor> readConfig(JsonElement data) {
        this.getBoolean(data, "Set", value, true, BuilderDescriptorState.Stable,
            "Whether entity is tamed", null);
        return this;
    }

    public boolean getValue(BuilderSupport support) {
        return value.get(support.getExecutionContext());
    }
}
```

### NPC Role JSON Usage

Reference in NPC role files (`Server/NPC/Roles/Cow.json`):
```json
{
  "Sensors": {
    "Tamed": { "Set": true }
  },
  "Actions": {
    "Tame": { "Food": ["Wheat", "Carrot"] },
    "RemovePlayerHeldItems": { "Count": 1 }
  }
}
```

---

## NPC Attitude System

### Imports
```java
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import com.hypixel.hytale.server.npc.config.AttitudeGroup;
```

### Attitude Values
```java
Attitude.REVERED    // Friendly, won't attack
Attitude.NEUTRAL    // Default for most animals
Attitude.HOSTILE    // Will attack player
```

### Getting Current Attitude
```java
NPCEntity npcEntity = store.getComponent(ref, NPCEntity.getComponentType());
Role role = npcEntity.getRole();
WorldSupport worldSupport = role.getWorldSupport();

// Get default attitude (from NPC role config)
Attitude defaultAttitude = worldSupport.getDefaultPlayerAttitude();

// Get current attitude toward specific player
Attitude currentAttitude = worldSupport.getAttitude(entityRef, playerRef, store);
```

### Setting Attitude (Requires Reflection)

The `defaultPlayerAttitude` field is private. Use reflection:

```java
// Cache field once (in static initializer or setup)
private static final Field ATTITUDE_FIELD;
static {
    try {
        ATTITUDE_FIELD = WorldSupport.class.getDeclaredField("defaultPlayerAttitude");
        ATTITUDE_FIELD.setAccessible(true);
    } catch (NoSuchFieldException e) {
        throw new RuntimeException("Failed to access defaultPlayerAttitude", e);
    }
}

// Set attitude to friendly
try {
    ATTITUDE_FIELD.set(worldSupport, Attitude.REVERED);
} catch (IllegalAccessException e) {
    logger.atSevere().log("Failed to set attitude", e);
}
```

### Attitude Groups

Check if NPC belongs to a tameable group:

```java
WorldSupport worldSupport = role.getWorldSupport();
AttitudeGroup group = AttitudeGroup.getAssetMap().getAsset(worldSupport.getAttitudeGroup());
String groupId = group.getId();  // "PreyBig", "PreySmall", "Livestock", "Critters", etc.
```

---

## NPC Spawn Tracking

### Removing from Overpopulation Tracking

Tamed animals should not count toward spawn limits:

```java
NPCEntity npcEntity = store.getComponent(ref, NPCEntity.getComponentType());

// Remove from spawn tracking (returns previous state)
boolean wasTracked = npcEntity.updateSpawnTrackingState(false);

// Re-enable tracking
npcEntity.updateSpawnTrackingState(true);
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
Vector3f rotation = new Vector3f(0, 0, 0);

// spawnEntity - current code uses reflection for callback, but direct call may work
// TODO: Test if this simpler approach works:
// npcPlugin.spawnEntity(store, roleIndex, position, rotation, null, (a, b, c) -> {});

// Current working approach (reflection):
for (Method m : NPCPlugin.class.getMethods()) {
    if (m.getName().equals("spawnEntity") && m.getParameterCount() == 6) {
        Class<?> callbackClass = m.getParameterTypes()[5];
        Object callback = Proxy.newProxyInstance(
            callbackClass.getClassLoader(),
            new Class<?>[] { callbackClass },
            (proxy, method, args) -> null
        );
        m.invoke(npcPlugin, store, roleIndex, position, rotation, null, callback);
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
int soundId = SoundEvent.getAssetMap().getIndex("SFX_MyCustomSound");
Vector3d pos = transform.getPosition();

// Play 3D sound at position
SoundUtil.playSoundEvent3d(soundId, SoundCategory.SFX,
    pos.getX(), pos.getY(), pos.getZ(), store);
```

### Custom Sound Assets

To add custom sounds to your mod:

**1. Add the sound file (.ogg format):**
```
src/main/resources/
└── Common/
    └── Sounds/
        └── MyCustomSound.ogg
```

**2. Create a SoundEvent JSON:**
```
src/main/resources/
└── Server/
    └── Audio/
        └── SoundEvents/
            └── SFX_MyCustomSound.json
```

**SoundEvent JSON structure:**
```json
{
  "Layers": [
    {
      "Files": ["Sounds/MyCustomSound.ogg"],
      "Volume": 0,
      "RandomSettings": {
        "MinPitch": -1,
        "MaxPitch": 1,
        "MinVolume": -2
      }
    }
  ],
  "Volume": 0,
  "Parent": "SFX_Attn_Moderate",
  "PreventSoundInterruption": false
}
```

**3. Reference in code by filename (without .json):**
```java
int soundId = SoundEvent.getAssetMap().getIndex("SFX_MyCustomSound");
```

### SoundEvent Properties

| Property | Description |
|----------|-------------|
| `Files` | Array of `.ogg` files (picks randomly if multiple) |
| `Volume` | Decibels (0 = normal, negative = quieter, positive = louder) |
| `Parent` | Attenuation preset (see below) |
| `RandomSettings.MinPitch` | Minimum pitch variation |
| `RandomSettings.MaxPitch` | Maximum pitch variation |
| `RandomSettings.MinVolume` | Minimum volume variation |
| `StartDelay` | Delay in seconds before playing |
| `PreventSoundInterruption` | If true, sound won't be cut off by new sounds |

### Attenuation Presets

| Preset | Use Case |
|--------|----------|
| `SFX_Attn_VeryQuiet` | Subtle sounds, short range |
| `SFX_Attn_Quiet` | Quiet sounds |
| `SFX_Attn_Moderate` | Standard sound effects |
| `SFX_Attn_Loud` | Loud sounds, longer range |
| `SFX_Attn_VeryLoud` | Very loud sounds, maximum range |

### Sound Categories

| Category | Use Case |
|----------|----------|
| `SoundCategory.SFX` | Sound effects |
| `SoundCategory.MUSIC` | Background music |
| `SoundCategory.AMBIENT` | Ambient/environmental |

### Multiple Sound Variations

For random variation, add multiple files:
```json
{
  "Layers": [
    {
      "Files": [
        "Sounds/MySound_01.ogg",
        "Sounds/MySound_02.ogg",
        "Sounds/MySound_03.ogg"
      ],
      "Volume": 0
    }
  ],
  "Parent": "SFX_Attn_Moderate"
}
```

### Layered Sounds

Add multiple layers for complex sounds:
```json
{
  "Layers": [
    {
      "Files": ["Sounds/MainSound.ogg"],
      "Volume": 0
    },
    {
      "Files": ["Sounds/Sweetener.ogg"],
      "Volume": -6,
      "Probability": 50
    }
  ],
  "Parent": "SFX_Attn_Moderate"
}
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
  "Spawners": [
    {
      "SpawnerId": "BreedingHearts",
      "PositionOffset": { "X": 0, "Y": 0.4, "Z": -0.1 },
      "FixedRotation": false
    }
  ],
  "LifeSpan": 1
}
```

**ParticleSpawner:** `Server/Particles/Spawners/BreedingHearts.particlespawner`
```json
{
  "ParticleLifeSpan": { "Min": 0.8, "Max": 1 },
  "SpawnRate": { "Min": 5, "Max": 10 },
  "MaxConcurrentParticles": 5,
  "Particle": {
    "Texture": "Particles/Textures/Shapes/Hearts_HiRes.png",
    "Animation": {
      "0": { "Color": "#fbbbfc", "Opacity": 1 }
    }
  }
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

### NPC Core
| Class | Path |
|-------|------|
| `NPCEntity` | `com.hypixel.hytale.server.npc.entities.NPCEntity` |
| `Role` | `com.hypixel.hytale.server.npc.role.Role` |
| `WorldSupport` | `com.hypixel.hytale.server.npc.role.support.WorldSupport` |
| `StateSupport` | `com.hypixel.hytale.server.npc.role.support.StateSupport` |
| `NPCMountComponent` | `com.hypixel.hytale.builtin.mounts.NPCMountComponent` |

### NPC Attitude
| Class | Path |
|-------|------|
| `Attitude` | `com.hypixel.hytale.server.core.asset.type.attitude.Attitude` |
| `AttitudeGroup` | `com.hypixel.hytale.server.npc.config.AttitudeGroup` |

### NPC Behavior Trees
| Class | Path |
|-------|------|
| `Action` | `com.hypixel.hytale.server.npc.corecomponents.Action` |
| `ActionBase` | `com.hypixel.hytale.server.npc.corecomponents.ActionBase` |
| `Sensor` | `com.hypixel.hytale.server.npc.corecomponents.Sensor` |
| `SensorBase` | `com.hypixel.hytale.server.npc.corecomponents.SensorBase` |
| `BuilderActionBase` | `com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase` |
| `BuilderSensorBase` | `com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase` |
| `BuilderSupport` | `com.hypixel.hytale.server.npc.asset.builder.BuilderSupport` |
| `InfoProvider` | `com.hypixel.hytale.server.npc.sensorinfo.InfoProvider` |
| `StringArrayHolder` | `com.hypixel.hytale.server.npc.asset.builder.holder.StringArrayHolder` |
| `BooleanHolder` | `com.hypixel.hytale.server.npc.asset.builder.holder.BooleanHolder` |
| `BuilderDescriptorState` | `com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState` |

### ECS Systems
| Class | Path |
|-------|------|
| `HolderSystem` | `com.hypixel.hytale.component.system.HolderSystem` |
| `Holder` | `com.hypixel.hytale.component.Holder` |
| `Query` | `com.hypixel.hytale.component.query.Query` |
| `Dependency` | `com.hypixel.hytale.component.dependency.Dependency` |
| `SystemDependency` | `com.hypixel.hytale.component.dependency.SystemDependency` |
| `Order` | `com.hypixel.hytale.component.dependency.Order` |
| `AddReason` | `com.hypixel.hytale.component.AddReason` |
| `RemoveReason` | `com.hypixel.hytale.component.RemoveReason` |
| `RoleBuilderSystem` | `com.hypixel.hytale.server.npc.systems.RoleBuilderSystem` |

### Codecs (for persistence)
| Class | Path |
|-------|------|
| `BuilderCodec` | `com.hypixel.hytale.codec.builder.BuilderCodec` |
| `Codec` | `com.hypixel.hytale.codec.Codec` |
| `KeyedCodec` | `com.hypixel.hytale.codec.KeyedCodec` |

### Player
| Class | Path |
|-------|------|
| `Player` | `com.hypixel.hytale.server.core.entity.Player` |
| `PlayerRef` | `com.hypixel.hytale.server.core.universe.PlayerRef` |
