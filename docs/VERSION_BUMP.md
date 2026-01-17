# How to Bump the Version

When releasing a new version, update the version number in these locations:

## Files to Update

### 1. build.gradle (line 8)
```groovy
version = '1.2.0'  // Change this
```

### 2. LaitsBreedingPlugin.java (line 74)
```java
public static final String VERSION = "1.2.0";  // Change this
```

### 3. manifest.json
No manual change needed - version is injected automatically from `build.gradle` during build via:
```groovy
processResources {
    filesMatching('manifest.json') {
        expand('version': project.version)
    }
}
```

## Build Command

After updating, run a clean build to generate the new JAR:

```cmd
cmd.exe /c "set JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot&& G:\Hytale\laits-animal-breeding\gradlew.bat -p G:\Hytale\laits-animal-breeding clean build -x test"
```

## Output

JAR will be created at:
```
build/libs/laits-animal-breeding-{VERSION}.jar
```

## Checklist

- [ ] Update `build.gradle` version
- [ ] Update `LaitsBreedingPlugin.java` VERSION constant
- [ ] Update `CHANGELOG.md` with new version section
- [ ] Update `README.md` "What's New" section
- [ ] Run clean build
- [ ] Verify JAR filename has correct version
- [ ] Verify manifest.json inside JAR has correct version
