# Architecture Improvements

This document tracks potential architectural improvements for the Animal Breeding plugin.

---

## 1. Replace Scheduled Scans with ECS Systems

### Current State
- `autoSetupNearbyAnimals()` runs every 30 seconds as a "safety net"
- `PlayerConnectEvent` triggers 3 delayed scans (1s, 3s, 5s)
- `NewAnimalSpawnDetector` ECS system exists but periodic scan is kept "just in case"

### Problem
- Wasteful - scans all entities repeatedly even if already processed
- Not native to the engine - fighting against the ECS architecture
- Race conditions possible between scheduled tasks and ECS systems

### Proposed Solution
1. Trust `NewAnimalSpawnDetector` for new entity detection
2. Single initial scan on first player connect (for pre-loaded entities)
3. Remove the 30-second periodic scan entirely
4. Add entity caching to prevent re-processing

### Files to Change
- `LaitsBreedingPlugin.java`: Remove periodic scan, simplify `attachInteractionsToAnimals()`
- `NewAnimalSpawnDetector.java`: Ensure reliable detection

---

## 2. Replace PlayerMouseButtonEvent with Proper Interaction Flow

### Current State
- `PlayerMouseButtonEvent` is registered but reportedly doesn't work
- `handleMouseClick()` contains dead code for right-click handling
- Actual interaction happens through the interaction system (Root_FeedAnimal → FeedAnimal)

### Problem
- Dead code that suggests mouse events should work but don't
- Confusing flow - unclear what actually triggers feeding

### Proposed Solution
1. Remove `PlayerMouseButtonEvent` handler if it doesn't work
2. Document that interactions are triggered through the interaction system only
3. Or: Investigate why mouse events don't fire and fix if needed

### Files to Change
- `LaitsBreedingPlugin.java`: Remove or fix `onMouseButton()` and `handleMouseClick()`

---

## 3. [BUG] Hint Overwriting & Component State Issues

### Current State
- `setupEntityInteractions()` calls `ensureAndGetComponent()` which returns EXISTING component if present
- We then unconditionally call `setInteractionHint()` which OVERWRITES any existing hint
- We also overwrite `setInteractionId(Use, ...)` unconditionally

### Problem
1. **Hint overwriting:** Horse spawns with "Press F to Mount" → we overwrite with "Press F to Feed"
2. **Component replacement:** If game/NPC system resets components (chunk reload, state change), our changes are lost
3. **No detection of existing state:** We don't check what's already set before overwriting

### Example Flow (Horse)
```
1. Horse spawns with default Interactions component
2. Horse has: Use → Root_Mount, hint → "Press F to Mount"
3. We call ensureAndGetComponent → returns EXISTING component
4. We call setInteractionId(Use, "Root_FeedAnimal") → OVERWRITES mount!
5. We call setInteractionHint("Press F to Feed") → OVERWRITES mount hint!
6. Result: Horse shows "Press F to Feed" instead of "Press F to Mount"
```

### Proposed Solution (Recommended): Dynamic State Switching

Instead of permanently overwriting, **dynamically switch between "feed mode" and "original mode"** based on whether feeding is relevant:

#### State Machine Approach
```
Animal States:
┌─────────────────┐     Feed animal      ┌─────────────────┐
│  ORIGINAL MODE  │ ──────────────────►  │   LOVE MODE     │
│  (Use=Mount)    │                      │  (hearts shown) │
│  (Hint=Mount)   │  ◄──────────────────  │                 │
└─────────────────┘     Love expires     └─────────────────┘
        ▲                                        │
        │                                        │ Mate found
        │         Cooldown expires               ▼
        │  ◄──────────────────────────  ┌─────────────────┐
        │                               │ BREEDING COOLDOWN│
        └───────────────────────────────│  (can't breed)  │
                                        └─────────────────┘
```

#### When to show FEED interaction:
- Animal is NOT in love mode
- Animal is NOT in breeding cooldown
- Animal CAN be fed (has valid food in player hand - optional check)

#### When to show ORIGINAL interaction:
- Animal IS in love mode (already fed, show hearts)
- Animal IS in breeding cooldown (can't breed, let them mount/tame/etc)

#### Implementation
```java
// Store BOTH original interaction and hint
Map<String, OriginalInteractionState> originalStates = new ConcurrentHashMap<>();

class OriginalInteractionState {
    String interactionId;  // e.g., "Root_Mount"
    String hint;           // e.g., "Press F to Mount"
}

// In the tick loop or on state change:
void updateAnimalInteractionState(Ref<EntityStore> entityRef, AnimalType type) {
    BreedingData data = breedingManager.getData(entityRef);

    boolean shouldShowFeed = true;
    if (data != null) {
        if (data.isInLove()) shouldShowFeed = false;
        if (data.isOnCooldown()) shouldShowFeed = false;
    }

    if (shouldShowFeed) {
        setInteraction(entityRef, "Root_FeedAnimal", "Press F to Feed");
    } else {
        OriginalInteractionState original = originalStates.get(key);
        if (original != null) {
            setInteraction(entityRef, original.interactionId, original.hint);
        }
    }
}
```

#### Benefits
- Respects original entity behavior when feeding isn't relevant
- Horses show "Mount" when in love or cooldown
- Clean separation of concerns
- No permanent state corruption

#### Hint Selection Logic

The hint shown should depend on:
1. **Interaction mode** (entity-based vs Ability2)
2. **Animal type** (mountable vs non-mountable)
3. **Animal state** (can feed vs can't feed)

```java
String selectHint(AnimalType type, boolean canFeed, boolean useEntityBasedInteractions) {
    if (!canFeed) {
        // In love or cooldown - show original hint
        return originalStates.get(entityKey).hint;
    }

    if (useEntityBasedInteractions) {
        // Entity-based (F key for both feed and mount)
        if (type.isMountable()) {
            return "server.interactionHints.feedAndMount";  // "Press F to Feed / Mount"
        } else {
            return "server.interactionHints.feed";  // "Press F to Feed"
        }
    } else {
        // Ability2-based (E for feed, F stays for mount)
        return "server.interactionHints.feed";  // "Press E to Feed"
        // Mount hint untouched - still shows "Press F to Mount"
    }
}
```

#### Language Keys Needed
```properties
# server.lang
interactionHints.feed = Press [E] (or Ability 2) to Feed
interactionHints.legacyFeed = Press [{key}] to Feed
interactionHints.feedAndMount = Press [{key}] to Feed / Mount
```

### Alternative Solutions (Less Recommended)

1. **Merge hints for mountable animals:**
   ```java
   if (animalType.isMountable()) {
       setHint.invoke(interactions, "Press F to Feed / Mount");
   }
   ```

2. **Use separate interaction types:**
   - Keep Use for mount (horse's default)
   - Use Ability2 for feeding (our addition)
   - No conflict, but requires player to know two keys

### Files to Change
- `LaitsBreedingPlugin.java`: `setupEntityInteractions()`, `setupCustomAnimalInteractions()`
- Potentially add hint caching similar to `originalInteractions` map

### Priority
**HIGH** - This is likely the root cause of hint display issues

---

## 4. Centralize Interaction Setup Logic

### Current State
- `setupEntityInteractions()` for built-in animals
- `setupCustomAnimalInteractions()` for custom animals (copy-pasted code)
- `setupAbility2HintOnly()` for hint-only mode
- Multiple code paths doing similar things

### Problem
- Code duplication between built-in and custom animal setup
- Easy to fix a bug in one place and forget the other
- Hard to maintain

### Proposed Solution
1. Create a single `InteractionSetupService` class
2. Unified method: `setupInteraction(entity, interactionId, hintKey, options)`
3. Options could include: `useType` (Use, Ability2), `saveOriginal`, etc.

### Files to Change
- New file: `services/InteractionSetupService.java`
- `LaitsBreedingPlugin.java`: Delegate to service

---

## 5. Use Proper Dependency Injection

### Current State
- `static LaitsBreedingPlugin instance` singleton pattern
- Managers created directly in `setup()`
- Some classes reach back to plugin via `LaitsBreedingPlugin.getInstance()`

### Problem
- Tight coupling
- Hard to test
- Violates dependency inversion principle

### Proposed Solution
1. Use constructor injection for all managers
2. Remove static `instance` field
3. Pass dependencies explicitly
4. Consider a simple DI container or service locator

### Reference
See `docs/CODE_DESIGN_GUIDELINES.md` for patterns.

---

## 6. Separate Concerns: Detection vs Setup vs Execution

### Current State
- `LaitsBreedingPlugin.java` is ~6000+ lines
- Contains: detection, setup, commands, event handling, utility methods
- Hard to understand flow

### Problem
- God class anti-pattern
- Single file doing too many things
- Difficult to navigate and maintain

### Proposed Solution
Split into focused classes:

```
services/
├── AnimalDetectionService.java    # Finding animals (ECS + scanning)
├── InteractionSetupService.java   # Attaching interactions to entities
├── BreedingService.java           # Love mode, mating, pregnancy
├── GrowthService.java             # Baby growth stages
└── HintService.java               # Interaction hints
```

### Files to Change
- New service files
- `LaitsBreedingPlugin.java`: Becomes thin orchestrator

---

## 7. Improve Error Handling

### Current State
- Many `catch (Exception e) { // Silent }` blocks
- Errors swallowed without logging
- Hard to debug issues

### Problem
- When something fails, no indication of what or why
- "Silent" catches hide bugs

### Proposed Solution
1. At minimum, log errors at DEBUG/VERBOSE level
2. Use specific exception types where possible
3. Create custom exceptions: `InteractionSetupException`, `AnimalDetectionException`
4. Add error recovery strategies instead of just ignoring

---

## 8. Configuration-Driven Interaction Types

### Current State
- Hardcoded `InteractionType.Use` in multiple places
- Toggle flags: `USE_ENTITY_BASED_INTERACTIONS`, `SHOW_ABILITY2_HINTS_ON_ENTITIES`
- Changing interaction type requires code changes

### Problem
- Not flexible
- Can't easily switch between Use/Ability2 without recompiling

### Proposed Solution
1. Add to `config.json`:
   ```json
   {
     "interaction": {
       "type": "Use",  // or "Ability2"
       "showHints": true,
       "hintKey": "server.interactionHints.feed"
     }
   }
   ```
2. Read interaction type from config at runtime
3. Remove hardcoded toggle flags

### Files to Change
- `config.json`
- `ConfigManager.java`
- `LaitsBreedingPlugin.java`

---

## 9. Event-Driven Architecture for Cross-Cutting Concerns

### Current State
- Direct method calls between managers
- Growth callback set via `setOnGrowthCallback()`
- Tight coupling between BreedingManager and GrowthManager

### Problem
- Managers know too much about each other
- Adding new features requires modifying existing code

### Proposed Solution
1. Create internal event bus
2. Events: `AnimalFedEvent`, `AnimalBredEvent`, `BabyBornEvent`, `AnimalGrewEvent`
3. Managers publish events, other managers subscribe
4. Loose coupling, easy to extend

### Reference
See `docs/CODE_DESIGN_GUIDELINES.md` - EventBus pattern

---

## 10. Cache Component Type Lookups

### Current State
- `getInteractableComponentType()` and `getInteractionsComponentType()` cache results
- But `Class.forName("com.hypixel.hytale.protocol.InteractionType")` called repeatedly
- Reflection method lookups done per-entity

### Problem
- Unnecessary reflection overhead
- Same lookups repeated thousands of times

### Proposed Solution
1. Cache all reflection lookups at startup
2. Create `ReflectionCache` class:
   ```java
   class ReflectionCache {
       static Class<?> INTERACTION_TYPE_CLASS;
       static Object USE_TYPE;
       static Object ABILITY2_TYPE;
       static Method SET_INTERACTION_ID;
       static Method SET_INTERACTION_HINT;
       // Initialize once in setup()
   }
   ```

### Files to Change
- New: `util/ReflectionCache.java`
- `LaitsBreedingPlugin.java`: Use cached values

---

## 11. Unit Testing Infrastructure

### Current State
- No unit tests
- Manual testing only
- Bugs discovered in production

### Problem
- No confidence in changes
- Regressions easy to introduce
- Refactoring is risky

### Proposed Solution
1. Add test framework (JUnit 5)
2. Mock Hytale API interfaces
3. Test managers in isolation
4. Integration tests for full flow

### Files to Add
```
src/test/java/com/laits/breeding/
├── managers/
│   ├── BreedingManagerTest.java
│   ├── GrowthManagerTest.java
│   └── TamingManagerTest.java
└── mocks/
    └── MockEntityStore.java
```

---

## Priority Order

| Priority | Item | Impact | Effort |
|----------|------|--------|--------|
| **1** | **[BUG] Fix hint overwriting (#3)** | **Critical** | **Low** |
| 2 | Replace scheduled scans with ECS (#1) | High | Medium |
| 3 | Centralize interaction setup (#4) | High | Low |
| 4 | Cache reflection lookups (#10) | Medium | Low |
| 5 | Configuration-driven interactions (#8) | Medium | Low |
| 6 | Separate concerns - split plugin (#6) | High | High |
| 7 | Remove dead mouse event code (#2) | Low | Low |
| 8 | Improve error handling (#7) | Medium | Medium |
| 9 | Dependency injection (#5) | Medium | High |
| 10 | Event-driven architecture (#9) | Medium | High |
| 11 | Unit testing (#11) | High | High |

---

## Notes

- **Multi-world support:** Currently `autoSetupNearbyAnimals()` only scans `Universe.get().getDefaultWorld()`. Need to iterate all worlds for multi-world support.
- **Multiplayer hint limitation (acceptable):** Entity-based hints are visible to ALL players, regardless of what they're holding. Player A with food and Player B without food both see "Press F to Feed". This is acceptable because:
  - The fallback system handles wrong/no food gracefully
  - Combined hints like "Feed/Mount" cover multiple use cases
  - Per-player hints would require item-based hint system (may not exist in API)
- Changes should be incremental - don't rewrite everything at once
- Each improvement should be a separate branch/PR
- Test thoroughly after each change
- Keep the working system functional during refactoring
