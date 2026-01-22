# Versioning Strategy

This document describes the versioning and build variant strategy for Lait's Animal Breeding.

## Version Format

```
{major}.{minor}.{patch}[-experimental]
```

Examples:
- `1.3.0` - Default/stable release
- `1.3.0-experimental` - Experimental build

## Build Variants

Starting with v1.3.0, we provide two build variants:

### Default Build (Recommended)

| Property | Value |
|----------|-------|
| Version | `1.3.0` |
| Mod Name | `AnimalBreeding` |
| Feed Key | **F** (Use interaction) |
| JAR File | `laits-animal-breeding-1.3.0.jar` |

**Features:**
- Entity-based interactions (F key to feed)
- Dynamic hint switching:
  - Shows "Press F to Feed" when animal can be fed
  - Shows "Press F to Feed / Mount" for horses
  - Restores original hint when animal is in love mode or cooldown
- Food template assets **excluded** (no E key feeding)

**Build Command:**
```bash
./gradlew build
```

### Experimental Build

| Property | Value |
|----------|-------|
| Version | `1.3.0-experimental` |
| Mod Name | `AnimalBreeding (Experimental)` |
| Feed Key | **E** (Ability2 interaction) |
| JAR File | `laits-animal-breeding-1.3.0-experimental.jar` |

**Features:**
- Item-based Ability2 interactions (E key to feed)
- Food templates include `Ability2: Root_FeedAnimal`
- Shows "(Experimental)" suffix in mod list to differentiate

**Build Command:**
```bash
./gradlew build -PbuildVariant=experimental
```

## Why Two Variants?

The game's interaction system has different trade-offs:

### F Key (Entity-Based) - Default
**Pros:**
- Feels more natural (same key as mount/interact)
- Dynamic hints based on animal state
- No conflict with food consumption (right-click)

**Cons:**
- More complex implementation
- Requires saving/restoring original entity interactions

### E Key (Item Ability2) - Experimental
**Pros:**
- Simpler implementation
- Works through item system (more portable)

**Cons:**
- Different key than other interactions
- No dynamic hint switching
- Requires food templates to be modified

## Build Configuration

Build variants are controlled by:

1. **Gradle Property:** `-PbuildVariant=experimental`
2. **Generated BuildConfig.java:**
   ```java
   public static final boolean USE_ENTITY_BASED_INTERACTIONS = true/false;
   public static final boolean IS_EXPERIMENTAL = false/true;
   public static final String VERSION = "1.3.0" / "1.3.0-experimental";
   ```

3. **manifest.json:** Name field changes
   - Default: `"Name": "AnimalBreeding"`
   - Experimental: `"Name": "AnimalBreeding (Experimental)"`

4. **Resource Filtering:**
   - Default: Excludes food template assets
   - Experimental: Includes all assets

## Deploy Script

The `deploy.ps1` script builds and deploys **both** variants:

```powershell
.\deploy.ps1
```

Output:
- `laits-animal-breeding-1.3.0.jar`
- `laits-animal-breeding-1.3.0-experimental.jar`

**Important:** Only enable ONE version at a time in your mods folder!

## Version History

| Version | Date | Notes |
|---------|------|-------|
| 1.3.0 | 2026-01 | Introduced dual build system. Default = F key, Experimental = E key |
| 1.2.x | - | E key only (Ability2) |
| 1.1.x | - | Initial F key attempts |
| 1.0.x | - | First release |

## Future Considerations

- May consolidate to single build once interaction system is proven stable
- Could add config option to switch between F/E key at runtime
- Per-animal-type key configuration possible in future
