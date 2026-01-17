# Pre-Publish Checklist

Run through this checklist before publishing a new release.

## Version Check

- [ ] Version bumped in `build.gradle`
- [ ] Version bumped in `LaitsBreedingPlugin.java`
- [ ] Version appears in JAR filename: `laits-animal-breeding-X.X.X.jar`
- [ ] Version in `manifest.json` inside JAR matches (extract and check)

```cmd
jar xf build\libs\laits-animal-breeding-X.X.X.jar manifest.json && type manifest.json && del manifest.json
```

## Build Verification

- [ ] Clean build succeeds without errors

```cmd
cmd.exe /c "set JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot&& gradlew.bat clean build -x test"
```

- [ ] JAR contains all required files:
  - `manifest.json`
  - `config.json`
  - All `.class` files in `com/laits/breeding/`
  - Asset pack files in `Server/`

```cmd
jar tf build\libs\laits-animal-breeding-X.X.X.jar
```

## Code Quality

- [ ] No compilation errors
- [ ] No critical warnings (deprecation warnings from Gradle plugin are OK)
- [ ] All new animals have:
  - Entry in `AnimalType.java` enum
  - Default food defined
  - Configuration in all preset methods in `ConfigManager.java`
  - Entry in `config.json`

## Documentation

- [ ] `CHANGELOG.md` updated with new version section
- [ ] `README.md` "What's New" section updated
- [ ] New commands documented in README
- [ ] Upgrade notes added if breaking changes

## Presets

- [ ] All built-in presets include all animals
- [ ] `initializePresets()` creates/updates all preset files
- [ ] New animals have correct values in each preset:
  - `default` - default food, 30min growth, 5min cooldown
  - `default_extended` - curated foods, 30min growth, 5min cooldown
  - `lait_curated` - curated foods, balanced timings
  - `zoo` - same as lait_curated, real animals enabled
  - `all` - same as lait_curated, everything enabled

## Config Compatibility

- [ ] Existing config files will load without errors
- [ ] New animals added to existing configs on load/save
- [ ] Preset files auto-updated with missing animals on startup

## Asset Verification

- [ ] All food item IDs exist in game (check against `docs/GAME_ASSETS_REFERENCE.md`)
- [ ] All animal model IDs match game assets
- [ ] No fake/placeholder item IDs

## Quick Verification Commands

```cmd
:: Check JAR exists and size
dir build\libs\*.jar

:: List JAR contents
jar tf build\libs\laits-animal-breeding-X.X.X.jar

:: Check manifest version
jar xf build\libs\laits-animal-breeding-X.X.X.jar manifest.json && type manifest.json && del manifest.json

:: Count animals in AnimalType.java
findstr /c:"Category." src\main\java\com\laits\breeding\models\AnimalType.java | find /c /v ""
```

## Final Sign-off

- [ ] All items above checked
- [ ] Ready to publish
