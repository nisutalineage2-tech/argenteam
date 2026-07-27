$ErrorActionPreference = "Stop"
$ROOT = Split-Path $MyInvocation.MyCommand.Path

$DEPLOY_DIR = "C:\server"

$GS_BUILD   = "$ROOT\aCis_gameserver\build"
$DP_BUILD   = "$ROOT\aCis_datapack\build"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Deploy to C:\server" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check that builds exist
if (-not (Test-Path "$GS_BUILD\l2jserver.jar")) {
    Write-Host "ERROR: Gameserver build not found." -ForegroundColor Red
    Write-Host "  Run build-all.ps1 first!" -ForegroundColor Yellow
    Write-Host "  Expected: $GS_BUILD\l2jserver.jar" -ForegroundColor Yellow
    exit 1
}
if (-not (Test-Path "$DP_BUILD\gameserver")) {
    Write-Host "ERROR: Datapack build not found." -ForegroundColor Red
    Write-Host "  Run build-all.ps1 first!" -ForegroundColor Yellow
    exit 1
}

Write-Host "  Source: aCis_gameserver\build\" -ForegroundColor Green
Write-Host "  Source: aCis_datapack\build\" -ForegroundColor Green
Write-Host "  Target: $DEPLOY_DIR" -ForegroundColor Green
Write-Host ""

# Create target directory
New-Item -ItemType Directory -Force -Path $DEPLOY_DIR | Out-Null

# 1. JAR
Write-Host "  [1/6] Copying l2jserver.jar..." -ForegroundColor Green
Copy-Item "$GS_BUILD\l2jserver.jar" "$DEPLOY_DIR\" -Force

# 2. Libraries (from build/dist/gameserver/libs)
Write-Host "  [2/6] Copying libraries..." -ForegroundColor Green
$LIBS_SRC = "$GS_BUILD\dist\gameserver\libs"
if (Test-Path $LIBS_SRC) {
    New-Item -ItemType Directory -Force -Path "$DEPLOY_DIR\libs" | Out-Null
    Copy-Item "$LIBS_SRC\*.jar" "$DEPLOY_DIR\libs\" -Force
} else {
    Write-Host "    (fallback: copying from aCis_gameserver\lib\)" -ForegroundColor Yellow
    New-Item -ItemType Directory -Force -Path "$DEPLOY_DIR\libs" | Out-Null
    Copy-Item "$ROOT\aCis_gameserver\lib\*.jar" "$DEPLOY_DIR\libs\" -Force
}

# 3. Config (from build/dist/gameserver/config)
Write-Host "  [3/6] Copying config files..." -ForegroundColor Green
$CONFIG_SRC = "$GS_BUILD\dist\gameserver\config"
if (Test-Path $CONFIG_SRC) {
    New-Item -ItemType Directory -Force -Path "$DEPLOY_DIR\config" | Out-Null
    Copy-Item "$CONFIG_SRC\*.properties" "$DEPLOY_DIR\config\" -Force
} else {
    Write-Host "    (fallback: copying from aCis_gameserver\config\)" -ForegroundColor Yellow
    New-Item -ItemType Directory -Force -Path "$DEPLOY_DIR\config" | Out-Null
    Copy-Item "$ROOT\aCis_gameserver\config\*.properties" "$DEPLOY_DIR\config\" -Force
}

# 4. Data
Write-Host "  [4/6] Copying data files..." -ForegroundColor Green
New-Item -ItemType Directory -Force -Path "$DEPLOY_DIR\data" | Out-Null
Remove-Item "$DEPLOY_DIR\data\*" -Recurse -Force -ErrorAction SilentlyContinue
Copy-Item "$DP_BUILD\gameserver\data\*" "$DEPLOY_DIR\data\" -Recurse -Force

# 5. SQL scripts
Write-Host "  [5/6] Copying SQL scripts..." -ForegroundColor Green
New-Item -ItemType Directory -Force -Path "$DEPLOY_DIR\sql" | Out-Null
Copy-Item "$DP_BUILD\sql\*.sql" "$DEPLOY_DIR\sql\" -Force

# 6. Login & Tools (from datapack build)
Write-Host "  [6/6] Copying login & tools..." -ForegroundColor Green
if (Test-Path "$DP_BUILD\login") {
    New-Item -ItemType Directory -Force -Path "$DEPLOY_DIR\login" | Out-Null
    Copy-Item "$DP_BUILD\login\*" "$DEPLOY_DIR\login\" -Recurse -Force
}
if (Test-Path "$DP_BUILD\tools") {
    New-Item -ItemType Directory -Force -Path "$DEPLOY_DIR\tools" | Out-Null
    Copy-Item "$DP_BUILD\tools\*" "$DEPLOY_DIR\tools\" -Recurse -Force
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  DEPLOY COMPLETE!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Destination: $DEPLOY_DIR" -ForegroundColor White
Write-Host ""

# Show deployed structure
Write-Host "  Deployed files:" -ForegroundColor Yellow
Get-ChildItem $DEPLOY_DIR | ForEach-Object {
    if ($_.PSIsContainer) {
        Write-Host "    📁 $($_.Name)\" -ForegroundColor Yellow
    } else {
        Write-Host "    📄 $($_.Name)" -ForegroundColor White
    }
}
# List libs inline
if (Test-Path "$DEPLOY_DIR\libs") {
    Get-ChildItem "$DEPLOY_DIR\libs" -Filter "*.jar" | ForEach-Object {
        Write-Host "    📄 libs\$($_.Name)" -ForegroundColor White
    }
}
Write-Host ""

Write-Host "Press any key to close..." -ForegroundColor Yellow
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
