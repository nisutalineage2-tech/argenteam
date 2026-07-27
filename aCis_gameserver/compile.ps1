$ErrorActionPreference = "Stop"

$JAVA_HOME = "C:\Program Files\Java\jdk-21"
$JAVAC = "$JAVA_HOME\bin\javac.exe"
$JAR = "$JAVA_HOME\bin\jar.exe"

$BASE = Split-Path $MyInvocation.MyCommand.Path
$SRC = "$BASE\java"
$LIB = "$BASE\lib"
$BUILD = "$BASE\build"
$BUILD_CLASSES = "$BUILD\classes"

Write-Host "=== aCis Gameserver Compile ===" -ForegroundColor Cyan

# Clean
if (Test-Path $BUILD) {
    Remove-Item -Recurse -Force $BUILD
    Write-Host "Cleaned build directory." -ForegroundColor Yellow
}

# Create dirs
New-Item -ItemType Directory -Force -Path $BUILD_CLASSES | Out-Null

# Build classpath from lib/*.jar
$cp = ($lib | Get-ChildItem -Filter "*.jar" | ForEach-Object { $_.FullName }) -join ";"

# Find all .java files
$javaFiles = Get-ChildItem -Path $SRC -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName
Write-Host "Compiling $($javaFiles.Count) Java files..." -ForegroundColor Green

# Write file list to temp file (too long for command line)
$listFile = "$BUILD\sources.txt"
[System.IO.File]::WriteAllText($listFile, ($javaFiles -join "`n"))

# Compile
& $JAVAC -encoding UTF-8 -source 21 -target 21 -cp $cp -d $BUILD_CLASSES -g -sourcepath $SRC "@$listFile" 2>&1

if ($LASTEXITCODE -ne 0) {
    Write-Host "COMPILE FAILED!" -ForegroundColor Red
    exit 1
}

Write-Host "Compile successful. Packaging l2jserver.jar..." -ForegroundColor Green

# Package jar
$manifestFile = "$BUILD\MANIFEST.MF"
[System.IO.File]::WriteAllText($manifestFile, "Manifest-Version: 1.0`nMain-Class: net.sf.l2j.gameserver.GameServer`nClass-Path: libs/`n")

Push-Location $BUILD_CLASSES
& $JAR cfm "$BUILD\l2jserver.jar" $manifestFile .
Pop-Location

if ($LASTEXITCODE -ne 0) {
    Write-Host "JAR creation FAILED!" -ForegroundColor Red
    exit 1
}

# Copy to dist
$DIST_GAME = "$BASE\build\dist\gameserver"
New-Item -ItemType Directory -Force -Path "$DIST_GAME\libs" | Out-Null
Copy-Item "$BUILD\l2jserver.jar" "$DIST_GAME\libs\"
Copy-Item "$LIB\*.jar" "$DIST_GAME\libs\"

Write-Host "=== DONE! l2jserver.jar built ===" -ForegroundColor Cyan
Write-Host "Output: $BUILD\l2jserver.jar" -ForegroundColor White
