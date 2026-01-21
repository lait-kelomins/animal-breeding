# deploy.ps1 - Lait's Animal Breeding build and deploy script
# Builds both default (F key) and experimental (E key) variants
# Usage: .\deploy.ps1
# Interactive mode: Press SPACE to deploy, Q to quit

$ErrorActionPreference = "Stop"

# ============================================
# CONFIGURATION
# ============================================

$PLUGIN_NAME = "laits-animal-breeding"
$VERSION = "1.3.0"

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
    # BUILD BOTH VERSIONS IN PARALLEL
    # ========================================
    # Use separate build directories to allow parallel builds
    $buildDir = Get-Location

    Write-Host "Building BOTH versions in parallel..." -ForegroundColor Cyan
    Write-Host "  - DEFAULT (F key) -> build-default/" -ForegroundColor DarkGray
    Write-Host "  - EXPERIMENTAL (E key) -> build-experimental/" -ForegroundColor DarkGray
    Write-Host ""

    # Start both builds in parallel with separate build directories
    $defaultJob = Start-Job -ScriptBlock {
        param($dir, $javaHome)
        Set-Location $dir
        $env:JAVA_HOME = $javaHome
        & .\gradlew.bat build -x test --no-daemon -q --build-dir=build-default 2>&1
        return $LASTEXITCODE
    } -ArgumentList $buildDir, $JAVA_HOME

    $expJob = Start-Job -ScriptBlock {
        param($dir, $javaHome)
        Set-Location $dir
        $env:JAVA_HOME = $javaHome
        & .\gradlew.bat build -PbuildVariant=experimental -x test --no-daemon -q --build-dir=build-experimental 2>&1
        return $LASTEXITCODE
    } -ArgumentList $buildDir, $JAVA_HOME

    # Wait for both jobs with progress indication
    Write-Host "Building..." -NoNewline
    while (($defaultJob.State -eq 'Running') -or ($expJob.State -eq 'Running')) {
        Write-Host "." -NoNewline
        Start-Sleep -Milliseconds 500
    }
    Write-Host " Done!"
    Write-Host ""

    # Check results
    $defaultResult = Receive-Job -Job $defaultJob
    $defaultExitCode = $defaultJob.ChildJobs[0].JobStateInfo.Reason.ExitCode
    if ($null -eq $defaultExitCode) { $defaultExitCode = if ($defaultJob.State -eq 'Completed') { 0 } else { 1 } }

    $expResult = Receive-Job -Job $expJob
    $expExitCode = $expJob.ChildJobs[0].JobStateInfo.Reason.ExitCode
    if ($null -eq $expExitCode) { $expExitCode = if ($expJob.State -eq 'Completed') { 0 } else { 1 } }

    Remove-Job -Job $defaultJob, $expJob -Force

    # Check for build failures
    $defaultJar = "build-default\libs\$PLUGIN_NAME-$VERSION.jar"
    $expJar = "build-experimental\libs\$PLUGIN_NAME-$VERSION-experimental.jar"

    if (-not (Test-Path $defaultJar)) {
        Write-Host "DEFAULT BUILD FAILED!" -ForegroundColor Red
        Write-Host $defaultResult -ForegroundColor DarkRed
        return $false
    }
    Write-Host "Default build successful!" -ForegroundColor Green

    if (-not (Test-Path $expJar)) {
        Write-Host "EXPERIMENTAL BUILD FAILED!" -ForegroundColor Red
        Write-Host $expResult -ForegroundColor DarkRed
        return $false
    }
    Write-Host "Experimental build successful!" -ForegroundColor Green
    Write-Host ""

    # Copy default JAR
    $defaultDestFile = Join-Path $dest "$PLUGIN_NAME-$VERSION.jar"
    Copy-Item $defaultJar $defaultDestFile -Force
    Write-Host "DEPLOYED: $defaultDestFile" -ForegroundColor Green

    if (-not [string]::IsNullOrWhiteSpace($serverDest)) {
        $serverDefaultFile = Join-Path $serverDest "$PLUGIN_NAME-$VERSION.jar"
        Copy-Item $defaultJar $serverDefaultFile -Force
        Write-Host "DEPLOYED (server): $serverDefaultFile" -ForegroundColor Green
    }

    # Copy experimental JAR
    $expDestFile = Join-Path $dest "$PLUGIN_NAME-$VERSION-experimental.jar"
    Copy-Item $expJar $expDestFile -Force
    Write-Host "DEPLOYED: $expDestFile" -ForegroundColor Green

    if (-not [string]::IsNullOrWhiteSpace($serverDest)) {
        $serverExpFile = Join-Path $serverDest "$PLUGIN_NAME-$VERSION-experimental.jar"
        Copy-Item $expJar $serverExpFile -Force
        Write-Host "DEPLOYED (server): $serverExpFile" -ForegroundColor Green
    }

    Write-Host ""
    Write-Host "============================================" -ForegroundColor Green
    Write-Host "         DEPLOYMENT COMPLETE!               " -ForegroundColor Green
    Write-Host "============================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Deployed JARs:" -ForegroundColor Yellow
    Write-Host "  - $PLUGIN_NAME-$VERSION.jar (Default, F key)" -ForegroundColor White
    Write-Host "  - $PLUGIN_NAME-$VERSION-experimental.jar (E key)" -ForegroundColor White
    Write-Host ""
    Write-Host "NOTE: Only enable ONE version at a time!" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Reload commands:" -ForegroundColor Yellow
    Write-Host "  /plugin unload Lait:AnimalBreeding" -ForegroundColor DarkGray
    Write-Host "  /plugin load Lait:AnimalBreeding" -ForegroundColor DarkGray
    Write-Host ""

    return $true
}

function ShowPrompt {
    Write-Host "============================================" -ForegroundColor DarkCyan
    Write-Host "  Press [SPACE] to deploy  |  [Q] to quit  " -ForegroundColor White
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
        elseif ($key.Key -eq [ConsoleKey]::Q) {
            Write-Host "Exiting..." -ForegroundColor Yellow
            break
        }
    }
    Start-Sleep -Milliseconds 100
}
