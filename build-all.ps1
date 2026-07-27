$ErrorActionPreference = "Stop"
$ROOT = Split-Path $MyInvocation.MyCommand.Path

# ============================================================
# 1. Check Requirements
# ============================================================
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Checking Requirements" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# JDK
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\javac.exe")) {
    $JAVA_HOME = $env:JAVA_HOME
}
elseif ((Get-Command javac.exe -ErrorAction SilentlyContinue).Source) {
    $javacPath = (Get-Command javac.exe).Source
    $JAVA_HOME = (Get-Item $javacPath).Directory.Parent.FullName
}
else {
    Write-Host "ERROR: Cannot find JDK 21." -ForegroundColor Red
    Write-Host "  Set JAVA_HOME or add javac to PATH." -ForegroundColor Yellow
    exit 1
}
Write-Host "  Java: $JAVA_HOME" -ForegroundColor Green

# Ant
$ANT_CMD = (Get-Command ant.bat -ErrorAction SilentlyContinue).Source
if (-not $ANT_CMD) {
    $ANT_CMD = (Get-Command ant -ErrorAction SilentlyContinue).Source
}
if (-not $ANT_CMD) {
    Write-Host "ERROR: Ant not found." -ForegroundColor Red
    Write-Host "  Install Apache Ant and add it to PATH." -ForegroundColor Yellow
    exit 1
}
Write-Host "  Ant: $ANT_CMD" -ForegroundColor Green

# Set JAVA_HOME for Ant
$env:JAVA_HOME = $JAVA_HOME
Write-Host ""

# ============================================================
# 2. Build Gameserver (build.xml → clean → compile → jar → dist)
# ============================================================
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  STEP 1: Gameserver Build" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$GS_BUILD_XML = "$ROOT\aCis_gameserver\build.xml"

Push-Location "$ROOT\aCis_gameserver"
& $ANT_CMD -f "$GS_BUILD_XML" dist 2>&1
$EXIT = $LASTEXITCODE
Pop-Location

if ($EXIT -ne 0) {
    Write-Host "  GAMESERVER BUILD FAILED!" -ForegroundColor Red
    exit 1
}
Write-Host "  Gameserver build successful." -ForegroundColor Green
Write-Host ""

# ============================================================
# 3. Build Datapack (build.xml → sync data/sql/tools/login)
# ============================================================
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  STEP 2: Datapack Build" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$DP_BUILD_XML = "$ROOT\aCis_datapack\build.xml"

Push-Location "$ROOT\aCis_datapack"
& $ANT_CMD -f "$DP_BUILD_XML" build 2>&1
$EXIT = $LASTEXITCODE
Pop-Location

if ($EXIT -ne 0) {
    Write-Host "  DATAPACK BUILD FAILED!" -ForegroundColor Red
    exit 1
}
Write-Host "  Datapack build successful." -ForegroundColor Green
Write-Host ""

# ============================================================
# Done
# ============================================================
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  BUILD COMPLETE!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Gameserver: $ROOT\aCis_gameserver\build\l2jserver.jar" -ForegroundColor White
Write-Host "  Gameserver dist: $ROOT\aCis_gameserver\build\dist\gameserver" -ForegroundColor White
Write-Host "  LoginServer dist: $ROOT\aCis_gameserver\build\dist\login" -ForegroundColor White
Write-Host "  Datapack: $ROOT\aCis_datapack\build\gameserver\data" -ForegroundColor White
Write-Host ""

Write-Host "Press any key to close..." -ForegroundColor Yellow
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
