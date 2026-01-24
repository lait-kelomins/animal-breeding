# Plan: Taming Integration

**Status**: Draft
**Branch**: `feature/taming-integration`
**Created**: 2026-01-24

---

## Summary

Integrate food-based taming into the breeding plugin so that animals must be tamed before they can breed. Name tags become rename-only (require tamed animal).

**Also renaming plugin**: "Lait's Animal Breeding" → **"HyTame"**

### Key Changes
1. Feed **taming food** to wild animal → animal becomes tamed
2. Feed **breeding food** to tamed animal → enters love mode (hearts)
3. Feed **any food** to tamed animal → heals only
4. Use **name tag** on tamed animal → open naming UI
5. Use **name tag** on wild animal → "Must tame first!" message

---

## Config Design

Uses `baseFoods` with optional overrides to keep config minimal:

```json
{
  "configVersion": 2,
  "animals": {
    "COW": {
      "baseFoods": ["Wheat"]
    },
    "PIG": {
      "baseFoods": ["Carrot", "Potato", "Beetroot"],
      "breedingFoods": ["Carrot"]
    },
    "HORSE": {
      "baseFoods": ["Apple", "Golden_Apple", "Sugar"],
      "tamingFoods": ["Golden_Apple"],
      "breedingFoods": ["Golden_Apple"]
    }
  }
}
```

| Field | Required | Default | Purpose |
|-------|----------|---------|---------|
| `configVersion` | Yes | 1 | Config schema version for migrations |
| `baseFoods` | Yes | - | Foods that tame, breed, and heal |
| `tamingFoods` | No | `baseFoods` | Override: only these foods tame |
| `breedingFoods` | No | `baseFoods` | Override: only these foods breed |

**Healing**: Always uses union of all configured foods.

### Config Migration (v1 → v2)

When loading a config with `configVersion < 2` or no version:
1. For each animal, copy `breedingFoods` → `baseFoods` (if `baseFoods` missing)
2. Set `configVersion = 2`
3. Save updated config

```java
// In ConfigManager.loadConfig()
if (configVersion == null || configVersion < 2) {
    for (AnimalConfig animal : animals) {
        if (animal.baseFoods == null && animal.breedingFoods != null) {
            animal.baseFoods = new ArrayList<>(animal.breedingFoods);
        }
    }
    configVersion = 2;
    saveConfig(); // Write migrated config back
}
```

Same migration applies to preset files in `presets/` folder.

---

## Existing Tame Plugin Code

The `tameable-animals-merge` branch contains a working tame plugin at `com.tameableanimals.*`.
We keep this package structure and wire it into `HyTamePlugin` (single JAR output).

### Existing Files to Reuse (from `com.tameableanimals`)

| File | Purpose | Integration Notes |
|------|---------|-------------------|
| `tame/TameComponent.java` | ECS component with `isTamed`, `tamerUUID`, `tamerName` | Already has BuilderCodec, reuse as-is |
| `tame/TameSystems.java` | `TameActivateSystem` sets REVERED attitude | Register from `HyTamePlugin.start()` |
| `actions/ActionTame.java` | NPC action for taming | Keep for NPC behavior tree support |
| `actions/BuilderActionTame.java` | Builder for ActionTame | Keep for JSON parsing |
| `sensors/SensorTamed.java` | NPC sensor to check tame state | Keep for NPC behavior tree support |
| `sensors/BuilderSensorTamed.java` | Builder for SensorTamed | Keep for JSON parsing |
| `utils/Debug.java` | Debug logging utility | Can reuse or merge with existing logging |

### Files to Remove/Replace

| File | Reason |
|------|--------|
| `TameableAnimalsPlugin.java` | Merged into `HyTamePlugin` |
| `config/ConfigManager.java` | Use `com.laits.breeding.util.ConfigManager` |
| `config/TameableAnimalsConfig.java` | Merge settings into existing config |
| `commands/*` | Merge into existing `/breed` command |
| `interactions/TameInteraction.java` | Empty, not needed |

---

## Implementation Tasks

### 1. Wire TameComponent into HyTamePlugin
**Keep**: `src/main/java/com/tameableanimals/tame/TameComponent.java`

In `HyTamePlugin.setup()`:
```java
// Register tame component from tameableanimals package
tameComponentType = getEntityStoreRegistry().registerComponent(
    TameComponent.class, "Tame", TameComponent.CODEC);
```

Expose getter: `getTameComponentType()` for other classes to use.

---

### 2. Wire TameActivateSystem
**Keep**: `src/main/java/com/tameableanimals/tame/TameSystems.java`

In `HyTamePlugin.start()`:
```java
// Register tame system
getEntityStoreRegistry().registerSystem(new TameSystems.TameActivateSystem());

// Register NPC core components for behavior trees
NPCPlugin.get().registerCoreComponentType("Tame", BuilderActionTame::new);
NPCPlugin.get().registerCoreComponentType("Tamed", BuilderSensorTamed::new);
```

---

### 3. Fix TameComponent Static Reference
**File**: `src/main/java/com/tameableanimals/tame/TameComponent.java`

Problem: `TameComponent.getComponentType()` calls `TameableAnimalsPlugin.get()` which we're deleting.

**Option A - Update reference** (simple):
```java
public static ComponentType<EntityStore, TameComponent> getComponentType() {
    return HyTamePlugin.getInstance().getTameComponentType();  // Point to new plugin
}
```

**Option B - Remove static method** (cleaner):
- Delete `getComponentType()` from `TameComponent`
- All callers use `HyTamePlugin.getInstance().getTameComponentType()` directly
- Update `TameSystems.java`, `SensorTamed.java`, `ActionTame.java` to use new path

**Recommend Option B** - avoids cross-package dependency from tameableanimals → laits.breeding.

---

### 4. Create TameHelper Utility
**File**: `src/main/java/com/laits/breeding/util/TameHelper.java`

Convenience wrapper to avoid verbose calls throughout codebase:

```java
public class TameHelper {
    public static ComponentType<EntityStore, TameComponent> getComponentType() {
        return HyTamePlugin.getInstance().getTameComponentType();
    }

    public static boolean isTamed(Ref<EntityStore> ref) {
        Store<EntityStore> store = ref.getStore();
        TameComponent comp = store.getComponent(ref, getComponentType());
        return comp != null && comp.isTamed();
    }

    public static void tameAnimal(Ref<EntityStore> ref, UUID playerUuid, String playerName) {
        Store<EntityStore> store = ref.getStore();
        TameComponent comp = store.ensureAndGetComponent(ref, getComponentType());
        comp.setTamed(playerUuid, playerName);
        // Also sync to TamingManager...
    }
}
```

---

### 5. Extend ConfigManager
**File**: `src/main/java/com/laits/breeding/util/ConfigManager.java`

Add config versioning:
- `int configVersion` (default 1, current = 2)
- Migration logic for v1 → v2 (copy breedingFoods to baseFoods)

Add to `AnimalConfig`:
- `List<String> baseFoods`
- `List<String> tamingFoods` (optional override)
- Keep existing `breedingFoods` (now optional override)

Add methods:
- `List<String> getTamingFoods(AnimalType)` → tamingFoods ?? baseFoods
- `List<String> getBreedingFoods(AnimalType)` → breedingFoods ?? baseFoods
- `Set<String> getHealingFoods(AnimalType)` → union of all configured foods
- `boolean isTamingFood(AnimalType, String itemId)`
- `boolean isBreedingFood(AnimalType, String itemId)`
- `boolean isHealingFood(AnimalType, String itemId)`

Merge from `TameableAnimalsConfig`:
- `Set<String> tameableAnimalGroups` (PreyBig, PreySmall, Livestock, Critters)

---

### 6. Modify FeedAnimalInteraction (PROTECTED)
**File**: `src/main/java/com/laits/breeding/interactions/FeedAnimalInteraction.java`

Logic change in `tick0()`:
```
if (animal is NOT tamed) {
    if (holding tamingFood) → TAME animal, consume food, show message
    else if (holding healingFood) → HEAL only
    else → fallback
} else {
    if (holding breedingFood) → BREED (current logic)
    else if (holding healingFood) → HEAL only
    else → fallback
}
```

Use `TameHelper.isTamed()` and `TameHelper.tameAnimal()` for tame checks/actions.

---

### 7. Modify NameAnimalInteraction (PROTECTED)
**File**: `src/main/java/com/laits/breeding/interactions/NameAnimalInteraction.java`

Add check at start of `tick0()`:
```java
if (!TameHelper.isTamed(targetRef)) {
    sendPlayerMessage(context, "Must tame this animal first!", "#FF5555");
    shouldFail = true;
    return;
}
// ... existing naming logic
```

---

### 8. Sync TamingManager with TameComponent
**File**: `src/main/java/com/laits/breeding/managers/TamingManager.java`

Two-way sync between ECS `TameComponent` and plugin `TamedAnimalData`:
- `tameAnimal()` → also calls `TameHelper.tameAnimal()` to set ECS component
- Add `syncFromTameComponent(UUID animalId, TameComponent comp)` for world load
- Add `syncToTameComponent(UUID animalId, Ref<EntityStore> ref)` for runtime

---

### 9. Delete Unused Tame Plugin Files

Remove after integration is complete:
- `com/tameableanimals/TameableAnimalsPlugin.java`
- `com/tameableanimals/config/` (entire folder)
- `com/tameableanimals/commands/` (entire folder)
- `com/tameableanimals/interactions/TameInteraction.java`

---

### 10. Rename Plugin to "HyTame"

**Files to update:**

| File | Change |
|------|--------|
| `manifest.json` | `"Name": "HyTame"`, `"Group": "HyTame"` |
| `build.gradle` | `archivesBaseName = 'hytame'` |
| `LaitsBreedingPlugin.java` | Rename file and class to `HyTamePlugin` |
| `README.md` | Update title and references |
| `CHANGELOG.md` | Add rename note |

**Future tasks (not this PR):**
- Rename repo from `animal-breeding` to `hytame`
- Rename package from `com.laits.breeding` to `com.hytame`
- Update GitHub URLs in docs

---

## Testing Checklist

- [ ] Build compiles: `gradle build -x test`
- [ ] JAR output is `hytame-{version}.jar`
- [ ] Plugin loads with name "HyTame" in server logs
- [ ] Wild cow + wheat → tames
- [ ] Tamed cow + wheat → enters love mode
- [ ] Two tamed cows in love mode → baby spawns
- [ ] Wild cow + name tag → "Must tame first!"
- [ ] Tamed cow + name tag → naming UI opens
- [ ] Wild cow + carrot (not taming food) → heals only
- [ ] Tamed cow + hay (healing only) → heals, no hearts
- [ ] Old config (no version) migrates correctly to v2

---

## Notes

- TameComponent persists via Hytale ECS (automatic world save)
- TamedAnimalData in TamingManager still used for plugin features (names, ownership)
- Show proposed changes for protected files before editing
