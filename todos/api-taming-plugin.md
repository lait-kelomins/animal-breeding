# API Reference: Taming Plugin (com.tameableanimals)

Standalone taming plugin to be integrated into HyTame.

---

## TameableAnimalsPlugin.java (TO DELETE)

**Path**: `com.tameableanimals.TameableAnimalsPlugin`

### Static Fields
```java
private static TameableAnimalsPlugin INSTANCE;
private static final HytaleLogger LOGGER;
private static final Field ATTITUDE_FIELD;  // WorldSupport.defaultPlayerAttitude
```

### Static Initializer
```java
static {
    // Reflection to access WorldSupport.defaultPlayerAttitude
    ATTITUDE_FIELD = WorldSupport.class.getDeclaredField("defaultPlayerAttitude");
    ATTITUDE_FIELD.setAccessible(true);
}
```

### Static Access
```java
public static TameableAnimalsPlugin get()
public static Field getAttitudeField()  // For setting Attitude.REVERED on tamed animals
```

### Instance Fields
```java
private ComponentType<EntityStore, TameComponent> tameComponentType;
```

### Lifecycle

**setup()** - Called early:
```java
// 1. Load config from Configs/config.json
ConfigManager.load(this.getDataDirectory().resolve("Configs/config.json").toFile());

// 2. Register commands (with alias)
this.getCommandRegistry().registerCommand(new TameableAnimalsCommand());       // /TameableAnimals
this.getCommandRegistry().registerCommand(new TameableAnimalsCommand("TA"));   // /TA (shortcut)

// 3. Register TameComponent
tameComponentType = this.getEntityStoreRegistry().registerComponent(
    TameComponent.class, "Tame", TameComponent.CODEC);
```

**start()** - Called after setup:
```java
// 1. Register TameActivateSystem
this.getEntityStoreRegistry().registerSystem(new TameSystems.TameActivateSystem());

// 2. Register NPC core components for behavior trees
NPCPlugin.get().registerCoreComponentType("Tame", BuilderActionTame::new);
NPCPlugin.get().registerCoreComponentType("Tamed", BuilderSensorTamed::new);
NPCPlugin.get().registerCoreComponentType("RemovePlayerHeldItems", BuilderActionRemovePlayerHeldItems::new);
```

**Integration Notes:**
- Move config loading to HyTamePlugin.setup()
- Merge commands into `/breed` subcommands
- Register TameComponent and systems in HyTamePlugin

---

## TameComponent.java (KEEP)

**Path**: `com.tameableanimals.tame.TameComponent`

### Codec (for persistence)
```java
public static final BuilderCodec<TameComponent> CODEC = BuilderCodec.builder(...)
    .append(new KeyedCodec<>("IsTamed", Codec.BOOLEAN), ...)
    .append(new KeyedCodec<>("TamerUUID", Codec.UUID_BINARY), ...)
    .append(new KeyedCodec<>("TamerName", Codec.STRING), ...)
    .build();
```

Persisted keys: `IsTamed`, `TamerUUID`, `TamerName`

### Static Access (NEEDS FIX)
```java
public static ComponentType<EntityStore, TameComponent> getComponentType() {
    return TameableAnimalsPlugin.get().getTameComponentType();  // ← Must change to HyTamePlugin
}
```

### Fields
```java
private Boolean isTamed = false;   // Note: Boxed Boolean, not primitive
private UUID tamerUUID = null;
private String tamerName = null;
```

### Methods
```java
public boolean isTamed()           // Returns Boolean.TRUE.equals(isTamed)
public UUID getTamerUUID()
public String getTamerName()
public void setTamed(@Nonnull UUID player, @Nonnull String playerName)  // Sets all 3 fields
public Component<EntityStore> clone()  // Deep copy
```

---

## TameSystems.java (KEEP)

**Path**: `com.tameableanimals.tame.TameSystems`

### TameActivateSystem (Inner Class)
```java
public static class TameActivateSystem extends HolderSystem<EntityStore>
```

### Constructor
```java
public TameActivateSystem() {
    this.npcComponentType = NPCEntity.getComponentType();
    this.tameComponentType = TameComponent.getComponentType();
    this.query = Query.and(npcComponentType, Query.not(NPCMountComponent.getComponentType()));
    this.dependencies = Set.of(new SystemDependency<>(Order.AFTER, RoleBuilderSystem.class));
    this.validGroups = ConfigManager.getConfig().getTameableAnimalGroups();
}
```

### Query
- Matches: `NPCEntity AND NOT NPCMountComponent`
- Excludes mounts from taming system

### Dependencies
- Runs AFTER `RoleBuilderSystem`

### onEntityAdd()
```java
public void onEntityAdd(Holder<EntityStore> holder, AddReason reason, Store<EntityStore> store) {
    // 1. Get NPCEntity and Role
    NPCEntity npcEntity = holder.getComponent(npcComponentType);
    Role role = npcEntity.getRole();
    WorldSupport worldSupport = role.getWorldSupport();

    // 2. Check if animal is in valid attitude group
    AttitudeGroup attitudeGroup = AttitudeGroup.getAssetMap().getAsset(worldSupport.getAttitudeGroup());
    if (!validGroups.contains(attitudeGroup.getId())) return;

    // 3. Ensure TameComponent exists (creates if missing)
    TameComponent tameComponent = holder.ensureAndGetComponent(tameComponentType);

    // 4. If tamed, apply effects
    if (tameComponent.isTamed()) {
        // Set attitude to REVERED via reflection
        TameableAnimalsPlugin.getAttitudeField().set(worldSupport, Attitude.REVERED);

        // Remove from overpopulation tracking
        npcEntity.updateSpawnTrackingState(false);
    }
}
```

### onEntityRemoved()
Empty - no cleanup needed.

**Integration Notes:**
- Replace `TameComponent.getComponentType()` with `HyTamePlugin.getInstance().getTameComponentType()`
- Replace `TameableAnimalsPlugin.getAttitudeField()` with equivalent in HyTamePlugin
- Replace `ConfigManager.getConfig()` with HyTame's ConfigManager

---

## ActionTame.java (KEEP)

**Path**: `com.tameableanimals.actions.ActionTame`

### Constructor
```java
public ActionTame(BuilderActionTame builder, BuilderSupport support) {
    super(builder);
    this.lovedFood = new HashSet<>(Arrays.asList(builder.getLovedFood(support)));
}
```

### Fields
```java
protected final Set<String> lovedFood;  // Parsed from NPC role JSON
```

### execute()
```java
public boolean execute(Ref<EntityStore> ref, Role role, InfoProvider sensorInfo, double dt, Store<EntityStore> store) {
    // 1. Get player from interaction target
    Ref<EntityStore> refStore = role.getStateSupport().getInteractionIterationTarget();
    PlayerRef playerRef = store.getComponent(refStore, PlayerRef.getComponentType());
    Player player = store.getComponent(refStore, Player.getComponentType());
    UUIDComponent playerUUID = store.getComponent(refStore, UUIDComponent.getComponentType());

    // 2. Get NPC components
    NPCEntity npcEntity = store.getComponent(ref, NPCEntity.getComponentType());
    WorldSupport worldSupport = role.getWorldSupport();

    // 3. Set tamed state
    TameComponent tameComponent = store.getComponent(ref, TameComponent.getComponentType());
    tameComponent.setTamed(playerUUID.getUuid(), player.getDisplayName());

    // 4. Set attitude to REVERED
    TameableAnimalsPlugin.getAttitudeField().set(worldSupport, Attitude.REVERED);

    // 5. Remove from spawn tracking
    npcEntity.updateSpawnTrackingState(false);

    // 6. Send success message
    Debug.msg(playerRef, npcEntity.getRoleName() + " successfully tamed", Level.INFO);
    return true;
}
```

**Note:** This action is triggered by NPC behavior trees when player feeds correct food.

---

## BuilderActionTame.java (KEEP)

**Path**: `com.tameableanimals.actions.BuilderActionTame`

### Fields
```java
protected StringArrayHolder lovedFoodHolder = new StringArrayHolder();
```

### Config Reading
```java
public Builder<Action> readConfig(JsonElement data) {
    this.requireStringArray(data, "Food", lovedFoodHolder, 1, Integer.MAX_VALUE, null,
        BuilderDescriptorState.Stable,
        "The food used for taming.",
        "The NPC's loved food item type that was used for this tame.");
    return super.readConfig(data);
}
```

**JSON Config Key:** `"Food"` (string array)

Example NPC role JSON:
```json
{
  "Tame": {
    "Food": ["Wheat", "Carrot"]
  }
}
```

---

## ActionRemovePlayerHeldItems.java (KEEP)

**Path**: `com.tameableanimals.actions.ActionRemovePlayerHeldItems`

### Constructor
```java
public ActionRemovePlayerHeldItems(BuilderActionRemovePlayerHeldItems builder, BuilderSupport support) {
    super(builder);
    this.count = builder.count;
}
```

### Fields
```java
protected final int count;  // Number of items to remove
```

### execute()
```java
public boolean execute(...) {
    // Get player and inventory
    Player player = store.getComponent(refStore, Player.getComponentType());
    Inventory inventory = player.getInventory();

    // Get active slot item
    byte slot = inventory.getActiveHotbarSlot();
    ItemStack itemStack = inventory.getHotbar().getItemStack(slot);

    // Remove items (BUG: only removes if quantity > count, should be >=)
    if (itemStack.getQuantity() > count) {
        inventory.getHotbar().removeItemStackFromSlot(slot, count);
    }

    player.sendInventory();
    return true;
}
```

**Known Bug:** Doesn't remove item if quantity equals count exactly.

---

## BuilderActionRemovePlayerHeldItems.java (KEEP)

**Path**: `com.tameableanimals.actions.BuilderActionRemovePlayerHeldItems`

### Config Reading
```java
public BuilderActionRemovePlayerHeldItems readConfig(JsonElement data) {
    this.getInt(data, "Count", (c) -> this.count = c, 1,
        IntRangeValidator.fromExclToIncl(0, 100),
        BuilderDescriptorState.Stable,
        "The amount of items to remove", null);
    return this;
}
```

**JSON Config Key:** `"Count"` (integer, default 1, range 1-100)

---

## SensorTamed.java (KEEP)

**Path**: `com.tameableanimals.sensors.SensorTamed`

### Constructor
```java
public SensorTamed(BuilderSensorTamed builder, BuilderSupport support) {
    super(builder);
    this.value = builder.getValue(support);  // Expected tame state
}
```

### Fields
```java
protected final boolean value;  // true = check if tamed, false = check if NOT tamed
```

### matches()
```java
public boolean matches(Ref<EntityStore> ref, Role role, double dt, Store<EntityStore> store) {
    TameComponent tameComponent = store.getComponent(ref, TameComponent.getComponentType());
    if (tameComponent == null) return false;

    return super.matches(...) && tameComponent.isTamed() == this.value;
}
```

### getSensorInfo()
Returns `null` - no additional sensor info provided.

---

## BuilderSensorTamed.java (KEEP)

**Path**: `com.tameableanimals.sensors.BuilderSensorTamed`

### Config Reading
```java
public Builder<Sensor> readConfig(JsonElement data) {
    this.getBoolean(data, "Set", this.value, true,  // Default: true
        BuilderDescriptorState.Stable,
        "Whether the entity is tamed or not", null);
    return this;
}
```

**JSON Config Key:** `"Set"` (boolean, default true)

Example NPC role JSON:
```json
{
  "Tamed": {
    "Set": true
  }
}
```

---

## TameableAnimalsConfig.java (TO DELETE - MERGE)

**Path**: `com.tameableanimals.config.TameableAnimalsConfig`

### Fields
```java
private boolean debugChatMessages = true;
private Set<String> tameableAnimalGroups = new HashSet<>();  // Initialized in constructor
```

### Constructor (Default Values)
```java
public TameableAnimalsConfig() {
    tameableAnimalGroups.add("PreyBig");
    tameableAnimalGroups.add("PreySmall");
    tameableAnimalGroups.add("Livestock");
    tameableAnimalGroups.add("Critters");
}
```

### Methods
```java
public Set<String> getTameableAnimalGroups()
public void setTameableAnimalGroups(Set<String> groups)
public boolean getDebugChatMessages()
public void setDebugChatMessages(boolean enabled)
public void loadTameConfig(TameableAnimalsConfig other)  // Partial config merge
```

**Integration:** Add these fields to HyTame's ConfigManager.

---

## ConfigManager.java (TO DELETE)

**Path**: `com.tameableanimals.config.ConfigManager`

Simple GSON-based config loader. Not needed - use HyTame's ConfigManager.

### Methods
```java
public static void load(File configFile)
public static void save(File configFile)
public static TameableAnimalsConfig getConfig()
```

---

## Debug.java (KEEP or MERGE)

**Path**: `com.tameableanimals.utils.Debug`

### Static Fields
```java
public static HytaleLogger LOGGER = TameableAnimalsPlugin.get().getLogger();
```

### Null Check Helpers
```java
public static Boolean isNullLog(Object obj, String message)
// If null: logs SEVERE, returns true
// If not null: returns false

public static Boolean isNullMsg(PlayerRef player, Object obj, String message)
// If null: logs SEVERE, sends message to player, returns true
// If not null: returns false
```

### Logging
```java
public static void log(String message, Level level)
// Logs to HytaleLogger

public static void msg(PlayerRef player, String message, Level level)
// Logs AND sends to player chat (if debugChatMessages enabled)
// Colors:
//   SEVERE  → #FF5555 bold
//   WARNING → #FFFF55
//   default → #FFFFFF
```

---

## Commands (TO DELETE - MERGE INTO /breed)

### TameableAnimalsCommand.java
```java
public class TameableAnimalsCommand extends AbstractCommandCollection {
    // Registered as "/TameableAnimals" and "/TA" (alias)
    // Subcommands: Attitude, Tamed, Foods, Debug
}
```

### TamedCommand.java
```java
// Usage: /TA Tamed [target]
// Output: "{RoleName}: IsTamed({bool}), Owner({name})"
// Extends AbstractTargetEntityCommand
```

### AttitudeCommand.java
```java
// Usage: /TA Attitude [target]
// Output: "{RoleName}: Default({attitude}), Current({attitude})"
// Shows both defaultPlayerAttitude and current attitude toward player
```

### DebugCommand.java
```java
// Usage: /TA Debug [target]
// Logs whether entity has TameComponent (to console only)
```

### FavouriteFoodCommand.java
```java
// Usage: /TA Foods
// Displays hardcoded food list:
// Default  - Carrot
// Horse    - Carrot
// Rabbit   - Carrot
// Bunny    - Carrot
// Sheep    - Lettuce
// Cow      - Cauliflower
// Pig      - Brown Mushroom
// Chicken  - Corn
// Turkey   - Corn
// Goat     - Apple
// Ram      - Apple
// Mouflon  - Apple
// Camel    - Wheat
// Boar     - Red Mushroom
// Skrill   - Chilli
```

**Note:** This hardcoded list is useful as a reference for default taming foods!

---

## TameInteraction.java (TO DELETE)

**Path**: `com.tameableanimals.interactions.TameInteraction`

Empty interaction - does nothing. Not used.

```java
public class TameInteraction extends SimpleInstantInteraction {
    @Override
    protected void firstRun(...) {
        // Empty - no implementation
    }
}
```
