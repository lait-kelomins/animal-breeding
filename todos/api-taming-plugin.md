# API Reference: Taming Plugin (com.tameableanimals)

Standalone taming plugin to be integrated into HyTame.

---

## TameableAnimalsPlugin.java (TO DELETE)

### Singleton Access
```java
public static TameableAnimalsPlugin get()
public static Field getAttitudeField()  // Reflection field for defaultPlayerAttitude
```

### Component Types
```java
public ComponentType<EntityStore, TameComponent> getTameComponentType()
```

### Lifecycle
```java
protected void setup()  // Registers TameComponent, loads config, registers commands
protected void start()  // Registers TameActivateSystem, NPC core components
```

**Integration:** Move registration logic to `HyTamePlugin.setup()` and `HyTamePlugin.start()`.

---

## TameComponent.java (KEEP)

### Codec
```java
public static final BuilderCodec<TameComponent> CODEC
```

### Static Access (NEEDS FIX)
```java
public static ComponentType<EntityStore, TameComponent> getComponentType()
// Currently calls TameableAnimalsPlugin.get() - must change to HyTamePlugin or remove
```

### State
```java
private Boolean isTamed = false
private UUID tamerUUID = null
private String tamerName = null
```

### Methods
```java
public boolean isTamed()
public UUID getTamerUUID()
public String getTamerName()
public void setTamed(@Nonnull UUID player, @Nonnull String playerName)
public Component<EntityStore> clone()
```

---

## TameSystems.java (KEEP)

### TameActivateSystem (Inner Class)
```java
public class TameActivateSystem extends HolderSystem<EntityStore>

// Query: NPCEntity AND NOT NPCMountComponent
// Dependency: Runs AFTER RoleBuilderSystem

public void onEntityAdd(Holder<EntityStore> holder, AddReason reason, Store<EntityStore> store)
// If animal is in tameableAnimalGroups AND TameComponent.isTamed():
//   - Set attitude to REVERED via reflection
//   - Remove from overpopulation tracking (updateSpawnTrackingState(false))

public void onEntityRemoved(...)  // Empty
```

**Integration:** Register in `HyTamePlugin.start()`:
```java
getEntityStoreRegistry().registerSystem(new TameSystems.TameActivateSystem());
```

---

## ActionTame.java (KEEP)

### Constructor
```java
public ActionTame(BuilderActionTame builder, BuilderSupport support)
// Reads lovedFood from builder
```

### Execute
```java
public boolean execute(Ref<EntityStore> ref, Role role, InfoProvider sensorInfo, double dt, Store<EntityStore> store)
// 1. Get player from role.getStateSupport().getInteractionIterationTarget()
// 2. Get TameComponent from entity
// 3. Call tameComponent.setTamed(playerUUID, playerName)
// 4. Set attitude to REVERED
// 5. Remove from spawn tracking
```

**Integration:** Register in `HyTamePlugin.start()`:
```java
NPCPlugin.get().registerCoreComponentType("Tame", BuilderActionTame::new);
```

---

## BuilderActionTame.java (KEEP)

### Config Reading
```java
public Builder<Action> readConfig(JsonElement data)
// Reads "Food" string array for loved foods
```

### Getters
```java
public String[] getLovedFood(BuilderSupport support)
```

---

## SensorTamed.java (KEEP)

### Constructor
```java
public SensorTamed(BuilderSensorTamed builder, BuilderSupport support)
// Reads expected value (true/false) from builder
```

### Match
```java
public boolean matches(Ref<EntityStore> ref, Role role, double dt, Store<EntityStore> store)
// Returns true if TameComponent.isTamed() == expected value
```

**Integration:** Register in `HyTamePlugin.start()`:
```java
NPCPlugin.get().registerCoreComponentType("Tamed", BuilderSensorTamed::new);
```

---

## BuilderSensorTamed.java (KEEP)

### Config Reading
```java
// Reads expected boolean value for sensor
```

### Getters
```java
public boolean getValue(BuilderSupport support)
```

---

## ActionRemovePlayerHeldItems.java (KEEP - optional)

Removes items from player's hand after interaction.

```java
public boolean execute(...)
// Removes item from player's held slot
```

**Integration:** Register in `HyTamePlugin.start()`:
```java
NPCPlugin.get().registerCoreComponentType("RemovePlayerHeldItems", BuilderActionRemovePlayerHeldItems::new);
```

---

## Debug.java (KEEP or MERGE)

### Logging
```java
public static void log(String message, Level level)
public static void msg(PlayerRef playerRef, String message, Level level)  // Send to player
```

### Null Checks
```java
public static boolean isNullLog(Object obj, String message)
public static boolean isNullMsg(PlayerRef ref, Object obj, String message)
```

**Integration:** Can merge into existing logging in breeding plugin or keep as-is.

---

## TameableAnimalsConfig.java (TO DELETE - MERGE)

### Fields
```java
private boolean debugChatMessages = true
private Set<String> tameableAnimalGroups = {"PreyBig", "PreySmall", "Livestock", "Critters"}
```

**Integration:** Add these fields to `com.laits.breeding.util.ConfigManager`.

---

## ConfigManager.java (tameableanimals) (TO DELETE)

Simple GSON loader. Not needed - use breeding plugin's ConfigManager.

---

## Commands (TO DELETE - MERGE)

| Command | Purpose | Integration |
|---------|---------|-------------|
| `TameableAnimalsCommand` | Parent command with alias "TA" | Merge into `/breed` |
| `TamedCommand` | Check if entity is tamed | Add as `/breed tamed` |
| `AttitudeCommand` | Debug attitude display | Add as `/breed debug attitude` |
| `DebugCommand` | Toggle debug mode | Merge with existing debug |
| `FavouriteFoodCommand` | Show animal's food | Add as `/breed food <animal>` |

---

## TameInteraction.java (TO DELETE)

Empty class - not used.

```java
public class TameInteraction extends SimpleInstantInteraction {
    @Override
    protected void firstRun(...) { }  // Empty
}
```
