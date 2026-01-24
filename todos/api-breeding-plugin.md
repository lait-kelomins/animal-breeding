# API Reference: Breeding Plugin (com.laits.breeding)

Current main plugin that will become HyTame.

---

## LaitsBreedingPlugin.java (→ HyTamePlugin.java)

### Singleton Access
```java
public static LaitsBreedingPlugin getInstance()
```

### Lifecycle
```java
protected void setup()    // Early init - register components, load config
protected void start()    // Register commands, systems, interactions
protected void shutdown() // Cleanup
```

### Managers (Getters)
```java
public BreedingManager getBreedingManager()
public TamingManager getTamingManager()
public ConfigManager getConfigManager()
public PersistenceManager getPersistenceManager()
```

### Component Types (to add)
```java
public ComponentType<EntityStore, TameComponent> getTameComponentType()  // NEW
```

### Utility Methods
```java
public static boolean isVerboseLogging()
public static String getOriginalInteractionId(Ref<EntityStore> ref, AnimalType type)
public void spawnBabyAnimal(AnimalType type, Vector3d pos, UUID parent1, UUID parent2)
```

---

## BreedingManager.java

### Core Methods
```java
public FeedResult tryFeed(UUID animalId, AnimalType type, String itemId, Ref<EntityStore> ref)
public FeedResult tryFeedCustomAnimal(UUID animalId, String modelAssetId, Ref<EntityStore> ref)
public BreedingData getData(UUID animalId)
public void removeData(UUID animalId)
public Set<UUID> getTrackedAnimalIds()
```

### FeedResult Enum
```java
SUCCESS, ALREADY_IN_LOVE, ON_COOLDOWN, NOT_ADULT, WRONG_FOOD
```

---

## TamingManager.java

### Taming Operations
```java
public boolean isTamed(UUID animalId)
public TamedAnimalData getTamedData(UUID animalId)
public TamedAnimalData tameAnimal(UUID animalId, UUID ownerUuid, String name, AnimalType type)
public TamedAnimalData tameAnimal(UUID animalId, UUID ownerUuid, String name, AnimalType type, Object entityRef, double x, double y, double z, GrowthStage stage)
public boolean untameAnimal(UUID animalId, UUID playerUuid)
public boolean renameAnimal(UUID animalId, UUID playerUuid, String newName)
```

### Ownership
```java
public boolean canPlayerInteract(UUID animalId, UUID playerUuid)
public UUID getOwner(UUID animalId)
public boolean isOwner(UUID animalId, UUID playerUuid)
```

### Lifecycle
```java
public void onTamedAnimalDespawn(UUID animalId, double x, double y, double z)
public void onTamedAnimalDeath(UUID animalId)
public void updatePosition(UUID animalId, double x, double y, double z)
public void updateEntityRef(UUID animalId, Object entityRef)
```

### Sync (to add)
```java
public void syncFromTameComponent(UUID animalId, TameComponent comp)  // NEW
public void syncToTameComponent(UUID animalId, Ref<EntityStore> ref)  // NEW
```

---

## ConfigManager.java

### Food Queries (current)
```java
public boolean isBreedingFood(AnimalType type, String itemId)
public List<String> getBreedingFoods(AnimalType type)
```

### Food Queries (to add)
```java
public List<String> getBaseFoods(AnimalType type)       // NEW
public List<String> getTamingFoods(AnimalType type)     // NEW - returns tamingFoods ?? baseFoods
public Set<String> getHealingFoods(AnimalType type)     // NEW - union of all
public boolean isTamingFood(AnimalType type, String itemId)   // NEW
public boolean isHealingFood(AnimalType type, String itemId)  // NEW
```

### Config Versioning (to add)
```java
public int getConfigVersion()       // NEW
public void migrateConfig()         // NEW - v1 → v2 migration
```

---

## FeedAnimalInteraction.java

### Entry Point
```java
protected void tick0(boolean firstRun, float time, InteractionType type,
                     InteractionContext context, CooldownHandler cooldownHandler)
```

### Key Internal Methods
```java
private String getModelAssetIdFromEntity(Ref<EntityStore> ref)
private UUID getUuidFromRef(Ref<EntityStore> ref)
private boolean isPlayerEntity(Ref<EntityStore> ref)
private void triggerFallbackInteraction(InteractionContext ctx, Ref<EntityStore> ref, AnimalType type)
private void playSoundAndConsumeItem(...)
private void spawnHeartParticles(Ref<EntityStore> ref)
private void checkForMateAndBreedInstantly(...)
```

---

## NameAnimalInteraction.java

### Entry Point
```java
protected void tick0(boolean firstRun, float time, InteractionType type,
                     InteractionContext context, CooldownHandler cooldownHandler)
```

### Key Internal Methods
```java
private void sendPlayerMessage(InteractionContext ctx, String msg, String color)
private void consumePlayerHeldItem(InteractionContext ctx)
private void playTamingSound(Ref<EntityStore> ref)
private void spawnHeartParticles(Ref<EntityStore> ref)
```

---

## Models

### AnimalType (Enum)
```java
COW, PIG, CHICKEN, SHEEP, HORSE, RABBIT, GOAT, DUCK, TURKEY, ...

public static AnimalType fromModelAssetId(String modelAssetId)
public static boolean isBabyVariant(String modelAssetId)
public String getBreedingFood()  // Default food
public String getBabyRoleId()    // NPC role for baby
```

### TamedAnimalData
```java
UUID animalUuid, ownerUuid
String customName
AnimalType animalType
GrowthStage growthStage
double lastX, lastY, lastZ
boolean isDespawned, isDead
Object entityRef  // Runtime only
```

### GrowthStage (Enum)
```java
BABY, JUVENILE, ADULT
```
