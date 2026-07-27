$ErrorActionPreference = "Stop"
$ROOT = Split-Path $MyInvocation.MyCommand.Path

# ============================================================
# 1. Compile Gameserver
# ============================================================
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  STEP 1: Gameserver Compile" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$GS_DIR = "$ROOT\aCis_gameserver"

# Try to find JDK: 1) JAVA_HOME, 2) javac in PATH, 3) common paths
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\javac.exe")) {
    $JAVA_HOME = $env:JAVA_HOME
    Write-Host "  Using JAVA_HOME: $JAVA_HOME" -ForegroundColor Green
}
elseif ((Get-Command javac.exe -ErrorAction SilentlyContinue).Source) {
    # Found javac in PATH - extract JAVA_HOME from it
    $javacPath = (Get-Command javac.exe).Source
    $JAVA_HOME = (Get-Item $javacPath).Directory.Parent.FullName
    Write-Host "  Found javac in PATH: $javacPath" -ForegroundColor Green
    Write-Host "  Derived JAVA_HOME: $JAVA_HOME" -ForegroundColor Green
}
else {
    # Try common installation paths
    $candidates = @(
        "C:\Program Files\Java\jdk-21",
        "C:\Program Files\Java\jdk-22",
        "C:\Program Files\Java\jdk-23",
        "C:\Program Files\Eclipse Adoptium\jdk-21.*",
        "C:\Program Files\Microsoft\jdk-21.*",
        "C:\Program Files\Java\jdk-1.8*",
        "C:\Program Files (x86)\Java\jdk-21*"
    )
    $JAVA_HOME = $null
    foreach ($c in $candidates) {
        $matches = Get-ChildItem $c -ErrorAction SilentlyContinue
        if ($matches) {
            $JAVA_HOME = $matches[-1].FullName
            Write-Host "  Found JDK at: $JAVA_HOME" -ForegroundColor Green
            break
        }
    }
}

if (-not $JAVA_HOME -or -not (Test-Path "$JAVA_HOME\bin\javac.exe")) {
    Write-Host "ERROR: Cannot find JDK 21." -ForegroundColor Red
    Write-Host "" -ForegroundColor Red
    Write-Host "  Options:" -ForegroundColor Yellow
    Write-Host "  1. Set JAVA_HOME environment variable pointing to your JDK" -ForegroundColor Yellow
    Write-Host "  2. Run: `$env:JAVA_HOME = 'C:\path\to\jdk-21'" -ForegroundColor Yellow
    Write-Host "  3. Make sure javac is in your PATH" -ForegroundColor Yellow
    exit 1
}

$JAVAC = "$JAVA_HOME\bin\javac.exe"
$JAR = "$JAVA_HOME\bin\jar.exe"

# Deploy target
$DEPLOY_DIR = "$env:USERPROFILE\Desktop\server"

$SRC           = "$GS_DIR\java"
$LIB           = "$GS_DIR\lib"
$BUILD         = "$GS_DIR\build"
$BUILD_CLASSES = "$BUILD\classes"

# Clean
if (Test-Path $BUILD) {
    Remove-Item -Recurse -Force $BUILD
    Write-Host "  Cleaned gameserver build directory." -ForegroundColor Yellow
}

New-Item -ItemType Directory -Force -Path $BUILD_CLASSES | Out-Null

# Classpath
$cp = ($LIB | Get-ChildItem -Filter "*.jar" | ForEach-Object { $_.FullName }) -join ";"

# Find Java files
$javaFiles = Get-ChildItem -Path $SRC -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName
Write-Host "  Found $($javaFiles.Count) Java files." -ForegroundColor Green

# Write source list
$listFile = "$BUILD\sources.txt"
[System.IO.File]::WriteAllText($listFile, ($javaFiles -join "`n"))

# Compile
Write-Host "  Compiling..." -ForegroundColor Green
& $JAVAC -encoding UTF-8 -source 21 -target 21 -cp $cp -d $BUILD_CLASSES -g -sourcepath $SRC "@$listFile" 2>&1

if ($LASTEXITCODE -ne 0) {
    Write-Host "  GAMESERVER COMPILE FAILED!" -ForegroundColor Red
    exit 1
}
Write-Host "  Compilation successful." -ForegroundColor Green

# Package jar
$manifestFile = "$BUILD\MANIFEST.MF"
[System.IO.File]::WriteAllText($manifestFile, "Manifest-Version: 1.0`nMain-Class: net.sf.l2j.gameserver.GameServer`nClass-Path: libs/`n")

Push-Location $BUILD_CLASSES
& $JAR cfm "$BUILD\l2jserver.jar" $manifestFile .
Pop-Location

if ($LASTEXITCODE -ne 0) {
    Write-Host "  JAR creation FAILED!" -ForegroundColor Red
    exit 1
}

Write-Host "  JAR created: $BUILD\l2jserver.jar" -ForegroundColor Green

# Copy to dist
$DIST_GAME = "$GS_DIR\build\dist\gameserver"
New-Item -ItemType Directory -Force -Path "$DIST_GAME\libs" | Out-Null
Copy-Item "$BUILD\l2jserver.jar" "$DIST_GAME\libs\"
Copy-Item "$LIB\*.jar" "$DIST_GAME\libs\"
Write-Host "  Gameserver dist ready at: $DIST_GAME" -ForegroundColor Green

# ============================================================
# 2. Sync Datapack
# ============================================================
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  STEP 2: Datapack Sync" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$DP_DIR   = "$ROOT\aCis_datapack"
$DP_BUILD = "$DP_DIR\build"

New-Item -ItemType Directory -Force -Path "$DP_BUILD\gameserver\data"  | Out-Null
New-Item -ItemType Directory -Force -Path "$DP_BUILD\login"           | Out-Null
New-Item -ItemType Directory -Force -Path "$DP_BUILD\sql"             | Out-Null
New-Item -ItemType Directory -Force -Path "$DP_BUILD\tools"           | Out-Null

# Sync data (remove stale files first, like Ant <sync> does)
Write-Host "  Syncing data files..." -ForegroundColor Green
Remove-Item "$DP_BUILD\gameserver\data\*" -Recurse -Force -ErrorAction SilentlyContinue
Copy-Item "$DP_DIR\data\*" "$DP_BUILD\gameserver\data\" -Recurse -Force -Exclude ".project"
Write-Host "  Data synced." -ForegroundColor Green

# Sync SQL
Write-Host "  Syncing SQL files..." -ForegroundColor Green
Copy-Item "$DP_DIR\sql\*.*" "$DP_BUILD\sql\" -Force
Write-Host "  SQL synced." -ForegroundColor Green

# Sync tools
Write-Host "  Syncing tools..." -ForegroundColor Green
Copy-Item "$DP_DIR\tools\*.bat" "$DP_BUILD\tools\" -Force
Copy-Item "$DP_DIR\tools\*.sh" "$DP_BUILD\tools\" -Force
Write-Host "  Tools synced." -ForegroundColor Green

# Sync serverNames.xml to login
Copy-Item "$DP_BUILD\gameserver\data\serverNames.xml" "$DP_BUILD\login\" -Force

# ============================================================
# Done
# ============================================================
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  BUILD COMPLETE!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Gameserver JAR: $BUILD\l2jserver.jar" -ForegroundColor White
Write-Host "  Gameserver dist: $DIST_GAME" -ForegroundColor White
Write-Host "  Datapack build: $DP_BUILD\gameserver\data" -ForegroundColor White
# ============================================================
# 3. Deploy to Server Folder
# ============================================================
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  STEP 3: Deploy to Desktop\\server" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

if (-not (Test-Path $DEPLOY_DIR)) {
    New-Item -ItemType Directory -Force -Path $DEPLOY_DIR | Out-Null
    Write-Host "  Created server directory." -ForegroundColor Yellow
}

Write-Host "  Copying JAR + libraries..." -ForegroundColor Green
Copy-Item "$BUILD\l2jserver.jar" "$DEPLOY_DIR\" -Force
New-Item -ItemType Directory -Force -Path "$DEPLOY_DIR\libs" | Out-Null
Copy-Item "$LIB\*.jar" "$DEPLOY_DIR\libs\" -Force

Write-Host "  Copying config files..." -ForegroundColor Green
New-Item -ItemType Directory -Force -Path "$DEPLOY_DIR\config" | Out-Null
Copy-Item "$GS_DIR\config\*.properties" "$DEPLOY_DIR\config\" -Force

Write-Host "  Copying data files..." -ForegroundColor Green
New-Item -ItemType Directory -Force -Path "$DEPLOY_DIR\data" | Out-Null
Copy-Item "$DP_BUILD\gameserver\data\*" "$DEPLOY_DIR\data\" -Recurse -Force

Write-Host "  Server deployed to: $DEPLOY_DIR" -ForegroundColor Green

# ============================================================
# Done
# ============================================================
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  BUILD COMPLETE!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Gameserver JAR: $BUILD\l2jserver.jar" -ForegroundColor White
Write-Host "  Datapack build: $DP_BUILD\gameserver\data" -ForegroundColor White
Write-Host "  Server deployed: $DEPLOY_DIR" -ForegroundColor White
Write-Host ""

# Keep window open when double-clicked
Write-Host "Press any key to close..." -ForegroundColor Yellow
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
