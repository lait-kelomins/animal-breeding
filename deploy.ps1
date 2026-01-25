# deploy.ps1 - Lait's Animal Breeding build and deploy script
# Builds both default (F key) and experimental (E key) variants
# Usage: .\deploy.ps1
#        .\deploy.ps1 -Reset    (clears saved configuration)
# Interactive mode: Press SPACE to deploy, Q to quit

param(
    [switch]$Reset
)

$ErrorActionPreference = "Stop"

# Handle -Reset flag
if ($Reset) {
    Write-Host ""
    Write-Host "Resetting saved configuration..." -ForegroundColor Yellow

    $varsToReset = @("HYTALE_JAVA_HOME", "HYTALE_INSTALL_PATH", "HYTALE_SERVER_PATH")

    foreach ($varName in $varsToReset) {
        $currentValue = [Environment]::GetEnvironmentVariable($varName, "User")
        if ($currentValue) {
            [Environment]::SetEnvironmentVariable($varName, $null, "User")
            Write-Host "  Cleared: $varName" -ForegroundColor DarkYellow
        } else {
            Write-Host "  Not set: $varName" -ForegroundColor DarkGray
        }
    }

    Write-Host ""
    Write-Host "Configuration reset. You will be prompted for values on next run." -ForegroundColor Green
    Write-Host ""
    exit 0
}

# ============================================
# CONFIGURATION
# ============================================

$PLUGIN_NAME = "laits-animal-breeding"

# Read version from build.gradle automatically
$buildGradle = Get-Content "build.gradle" -Raw
if ($buildGradle -match "version\s*=\s*'([^']+)'") {
    $VERSION = $matches[1]
} else {
    Write-Host "ERROR: Could not read version from build.gradle" -ForegroundColor Red
    exit 1
}

function Get-ConfigValue {
    param(
        [string]$EnvVarName,
        [string]$PromptMessage,
        [string]$DefaultValue = ""
    )

    $value = [Environment]::GetEnvironmentVariable($EnvVarName, "User")

    if ([string]::IsNullOrWhiteSpace($value)) {
        Write-Host ""
        Write-Host "Environment variable '$EnvVarName' is not set." -ForegroundColor Yellow

        if ($DefaultValue) {
            Write-Host "Default: $DefaultValue" -ForegroundColor DarkGray
        }

        $input = Read-Host $PromptMessage

        if ([string]::IsNullOrWhiteSpace($input) -and $DefaultValue) {
            $value = $DefaultValue
        } else {
            $value = $input
        }

        if ([string]::IsNullOrWhiteSpace($value)) {
            Write-Host "Value cannot be empty. Exiting." -ForegroundColor Red
            exit 1
        }

        $save = Read-Host "Save '$value' to environment variable '$EnvVarName'? (Y/n)"
        if ($save -ne 'n' -and $save -ne 'N') {
            [Environment]::SetEnvironmentVariable($EnvVarName, $value, "User")
            Write-Host "Saved to user environment variables." -ForegroundColor Green
        }
    }

    return $value
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   LAIT'S ANIMAL BREEDING DEPLOY SETUP     " -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

# Use existing JAVA_HOME as default if available
$javaDefault = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot" }
$hytaleInstallDefault = "C:\Users\$env:USERNAME\AppData\Roaming\Hytale"

$JAVA_HOME = Get-ConfigValue -EnvVarName "HYTALE_JAVA_HOME" `
    -PromptMessage "Enter Java 21 home path" `
    -DefaultValue $javaDefault

$HYTALE_INSTALL_PATH = Get-ConfigValue -EnvVarName "HYTALE_INSTALL_PATH" `
    -PromptMessage "Enter Hytale install path" `
    -DefaultValue $hytaleInstallDefault

# Optional server path
$SERVER_PATH = [Environment]::GetEnvironmentVariable("HYTALE_SERVER_PATH", "User")

if ([string]::IsNullOrWhiteSpace($SERVER_PATH)) {
    Write-Host ""
    $addServer = Read-Host "Do you want to add a server mods path? (y/N)"
    if ($addServer -eq 'y' -or $addServer -eq 'Y') {
        $SERVER_PATH = Get-ConfigValue -EnvVarName "HYTALE_SERVER_PATH" `
            -PromptMessage "Enter server mods path (e.g., C:\HytaleServer\mods)"
    }
}

# ============================================

$env:JAVA_HOME = $JAVA_HOME
$dest = "$HYTALE_INSTALL_PATH\UserData\Mods"
$serverDest = $SERVER_PATH

# Which build to deploy to server: "default" or "experimental"
$script:serverBuildType = "default"

function Deploy {
    Clear-Host
    Write-Host ""
    Write-Host "============================================" -ForegroundColor Cyan
    Write-Host "     BUILDING AND DEPLOYING PLUGIN          " -ForegroundColor Cyan
    Write-Host "============================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Plugin: $PLUGIN_NAME" -ForegroundColor Yellow
    Write-Host "Version: $VERSION (default) / $VERSION-experimental" -ForegroundColor Yellow
    Write-Host ""

    # Create mods folder if needed
    if (-not (Test-Path $dest)) {
        New-Item -ItemType Directory -Path $dest -Force | Out-Null
        Write-Host "Created Mods folder" -ForegroundColor Yellow
    }

    # Delete old versions
    $oldJars = Get-ChildItem -Path $dest -Filter "$PLUGIN_NAME*.jar" -ErrorAction SilentlyContinue
    foreach ($jar in $oldJars) {
        try {
            Remove-Item $jar.FullName -Force -ErrorAction Stop
            Write-Host "Deleted old: $($jar.Name)" -ForegroundColor DarkYellow
        } catch {
            Write-Host "Could not delete $($jar.Name) (in use)" -ForegroundColor DarkYellow
        }
    }

    # Delete old versions from server if configured
    if (-not [string]::IsNullOrWhiteSpace($serverDest)) {
        if (-not (Test-Path $serverDest)) {
            New-Item -ItemType Directory -Path $serverDest -Force | Out-Null
            Write-Host "Created server Mods folder" -ForegroundColor Yellow
        }

        $oldServerJars = Get-ChildItem -Path $serverDest -Filter "$PLUGIN_NAME*.jar" -ErrorAction SilentlyContinue
        foreach ($jar in $oldServerJars) {
            try {
                Remove-Item $jar.FullName -Force -ErrorAction Stop
                Write-Host "Deleted old (server): $($jar.Name)" -ForegroundColor DarkYellow
            } catch {
                Write-Host "Could not delete $($jar.Name) from server (in use)" -ForegroundColor DarkYellow
            }
        }
    }

    Write-Host ""

    # ========================================
    # BUILD DEFAULT VERSION (F key)
    # ========================================
    Write-Host "Building DEFAULT version (F key)..." -ForegroundColor Cyan

    $startTime = Get-Date
    & .\gradlew.bat clean build -x test --no-daemon -q

    if ($LASTEXITCODE -ne 0) {
        Write-Host "DEFAULT BUILD FAILED!" -ForegroundColor Red
        return $false
    }

    $elapsed = ((Get-Date) - $startTime).TotalSeconds
    Write-Host "Default build successful! ($([Math]::Round($elapsed))s)" -ForegroundColor Green

    # Copy default JAR to client
    $defaultJar = "build\libs\$PLUGIN_NAME-$VERSION.jar"
    $defaultDestFile = Join-Path $dest "$PLUGIN_NAME-$VERSION.jar"
    Copy-Item $defaultJar $defaultDestFile -Force
    Write-Host "DEPLOYED (client): $defaultDestFile" -ForegroundColor Green

    # Deploy to server immediately if default is selected
    if (-not [string]::IsNullOrWhiteSpace($serverDest) -and $script:serverBuildType -eq "default") {
        $serverFile = Join-Path $serverDest "$PLUGIN_NAME-$VERSION.jar"
        Copy-Item $defaultJar $serverFile -Force
        Write-Host "DEPLOYED (server): $serverFile" -ForegroundColor Green
    }

    Write-Host ""

    # ========================================
    # BUILD EXPERIMENTAL VERSION (E key)
    # ========================================
    Write-Host "Building EXPERIMENTAL version (E key)..." -ForegroundColor Cyan

    $startTime = Get-Date
    # No clean - JARs have different names, keep both in build/libs
    & .\gradlew.bat build -PbuildVariant=experimental -x test --no-daemon -q

    if ($LASTEXITCODE -ne 0) {
        Write-Host "EXPERIMENTAL BUILD FAILED!" -ForegroundColor Red
        return $false
    }

    $elapsed = ((Get-Date) - $startTime).TotalSeconds
    Write-Host "Experimental build successful! ($([Math]::Round($elapsed))s)" -ForegroundColor Green

    # Copy experimental JAR to client
    $expJar = "build\libs\$PLUGIN_NAME-$VERSION-experimental.jar"
    $expDestFile = Join-Path $dest "$PLUGIN_NAME-$VERSION-experimental.jar"
    Copy-Item $expJar $expDestFile -Force
    Write-Host "DEPLOYED (client): $expDestFile" -ForegroundColor Green

    # Deploy to server immediately if experimental is selected
    if (-not [string]::IsNullOrWhiteSpace($serverDest) -and $script:serverBuildType -eq "experimental") {
        $serverFile = Join-Path $serverDest "$PLUGIN_NAME-$VERSION-experimental.jar"
        Copy-Item $expJar $serverFile -Force
        Write-Host "DEPLOYED (server): $serverFile" -ForegroundColor Yellow
    }

    Write-Host ""
    Write-Host "============================================" -ForegroundColor Green
    Write-Host "         DEPLOYMENT COMPLETE!               " -ForegroundColor Green
    Write-Host "============================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Client JARs:" -ForegroundColor Yellow
    Write-Host "  - $PLUGIN_NAME-$VERSION.jar (Default, F key)" -ForegroundColor White
    Write-Host "  - $PLUGIN_NAME-$VERSION-experimental.jar (E key)" -ForegroundColor White
    if (-not [string]::IsNullOrWhiteSpace($serverDest)) {
        Write-Host ""
        Write-Host "Server JAR: $($script:serverBuildType.ToUpper())" -ForegroundColor Yellow
    }
    Write-Host ""
    Write-Host "Reload commands:" -ForegroundColor Yellow
    Write-Host "  /plugin unload Lait:AnimalBreeding" -ForegroundColor DarkGray
    Write-Host "  /plugin load Lait:AnimalBreeding" -ForegroundColor DarkGray
    Write-Host ""

    return $true
}

function Get-CurrentServerVersion {
    if ([string]::IsNullOrWhiteSpace($serverDest) -or -not (Test-Path $serverDest)) {
        return "none"
    }

    $defaultJar = Join-Path $serverDest "$PLUGIN_NAME-$VERSION.jar"
    $expJar = Join-Path $serverDest "$PLUGIN_NAME-$VERSION-experimental.jar"

    $hasDefault = Test-Path $defaultJar
    $hasExp = Test-Path $expJar

    if ($hasDefault -and $hasExp) {
        return "both"
    } elseif ($hasDefault) {
        return "default"
    } elseif ($hasExp) {
        return "experimental"
    } else {
        return "none"
    }
}

function Show-ServerVersionMenu {
    Write-Host ""
    Write-Host "============================================" -ForegroundColor Magenta
    Write-Host "       SERVER BUILD SELECTION               " -ForegroundColor Magenta
    Write-Host "============================================" -ForegroundColor Magenta
    Write-Host ""

    Write-Host "  Currently selected: " -NoNewline -ForegroundColor White
    if ($script:serverBuildType -eq "experimental") {
        Write-Host "EXPERIMENTAL" -ForegroundColor Yellow
    } else {
        Write-Host "DEFAULT" -ForegroundColor Green
    }

    Write-Host ""
    Write-Host "  [1] Default (F key)" -ForegroundColor Green
    Write-Host "  [2] Experimental (E key)" -ForegroundColor Yellow
    Write-Host "  [0] Cancel" -ForegroundColor DarkGray
    Write-Host ""
    Write-Host "  Select build for server: " -NoNewline -ForegroundColor White
}

function Set-ServerBuildType {
    param([string]$BuildType)

    if ([string]::IsNullOrWhiteSpace($serverDest)) {
        Write-Host "No server path configured!" -ForegroundColor Red
        return
    }

    $script:serverBuildType = $BuildType
    Write-Host ""
    if ($BuildType -eq "experimental") {
        Write-Host "Server build set to: EXPERIMENTAL" -ForegroundColor Yellow
    } else {
        Write-Host "Server build set to: DEFAULT" -ForegroundColor Green
    }
    Write-Host "Press SPACE to build and deploy." -ForegroundColor DarkGray
}

function ShowPrompt {
    Write-Host "============================================" -ForegroundColor DarkCyan
    Write-Host "  [SPACE] Deploy  [S] Server version  [Q] Quit" -ForegroundColor White
    if (-not [string]::IsNullOrWhiteSpace($serverDest)) {
        if ($script:serverBuildType -eq "experimental") {
            Write-Host "  [Server: EXPERIMENTAL]" -ForegroundColor Yellow
        } else {
            Write-Host "  [Server: DEFAULT]" -ForegroundColor Green
        }
    }
    Write-Host "============================================" -ForegroundColor DarkCyan
}

# Main
Clear-Host
Write-Host ""
Write-Host "  LAIT'S ANIMAL BREEDING DEPLOY" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Build variants:" -ForegroundColor White
Write-Host "    - Default (F key): Press F to feed" -ForegroundColor DarkGray
Write-Host "    - Experimental (E key): Press E to feed" -ForegroundColor DarkGray
Write-Host ""
ShowPrompt

while ($true) {
    if ([Console]::KeyAvailable) {
        $key = [Console]::ReadKey($true)

        if ($key.Key -eq [ConsoleKey]::Spacebar) {
            $success = Deploy
            if ($success) {
                Write-Host "Deployed at $(Get-Date -Format 'HH:mm:ss')" -ForegroundColor Green
            } else {
                Write-Host "Failed at $(Get-Date -Format 'HH:mm:ss')" -ForegroundColor Red
            }
            Write-Host ""
            ShowPrompt
        }
        elseif ($key.Key -eq [ConsoleKey]::S) {
            if ([string]::IsNullOrWhiteSpace($serverDest)) {
                Write-Host ""
                Write-Host "No server path configured!" -ForegroundColor Red
                Write-Host ""
                ShowPrompt
            } else {
                Show-ServerVersionMenu

                $versionKey = [Console]::ReadKey($true)
                if ($versionKey.KeyChar -eq '1') {
                    Set-ServerBuildType -BuildType "default"
                } elseif ($versionKey.KeyChar -eq '2') {
                    Set-ServerBuildType -BuildType "experimental"
                } else {
                    Write-Host "Cancelled" -ForegroundColor DarkGray
                }

                Write-Host ""
                ShowPrompt
            }
        }
        elseif ($key.Key -eq [ConsoleKey]::Q) {
            Write-Host "Exiting..." -ForegroundColor Yellow
            break
        }
    }
    Start-Sleep -Milliseconds 100
}
