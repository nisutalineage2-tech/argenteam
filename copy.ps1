$ErrorActionPreference = "Stop"
$ROOT = Split-Path $MyInvocation.MyCommand.Path

$DEPLOY_DIR = "C:\server"

$GS_DIST   = "$ROOT\aCis_gameserver\build\dist"
$DP_BUILD  = "$ROOT\aCis_datapack\build"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Copy to C:\server" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check that builds exist
if (-not (Test-Path "$GS_DIST\gameserver")) {
    Write-Host "ERROR: Gameserver dist not found." -ForegroundColor Red
    Write-Host "  Run build-all.ps1 first!" -ForegroundColor Yellow
    exit 1
}
if (-not (Test-Path "$DP_BUILD\gameserver")) {
    Write-Host "ERROR: Datapack build not found." -ForegroundColor Red
    Write-Host "  Run build-all.ps1 first!" -ForegroundColor Yellow
    exit 1
}

Write-Host "  Source Gameserver: aCis_gameserver\build\dist" -ForegroundColor Green
Write-Host "  Source Datapack:   aCis_datapack\build" -ForegroundColor Green
Write-Host "  Target:            $DEPLOY_DIR" -ForegroundColor Green
Write-Host ""

# Check login dist too
if (-not (Test-Path "$GS_DIST\login")) {
    Write-Host "WARNING: LoginServer dist not found. Login server won't be copied." -ForegroundColor Yellow
}

Write-Host "  (target directories will be cleaned before copy)" -ForegroundColor Yellow
Write-Host ""

# Create target
New-Item -ItemType Directory -Force -Path $DEPLOY_DIR | Out-Null

# 1. Gameserver dist → C:\server\gameserver\
Write-Host "  [1/5] Copying gameserver..." -ForegroundColor Green
New-Item -ItemType Directory -Force -Path "$DEPLOY_DIR\gameserver" | Out-Null
Remove-Item "$DEPLOY_DIR\gameserver\*" -Recurse -Force -ErrorAction SilentlyContinue
Copy-Item "$GS_DIST\gameserver\*" "$DEPLOY_DIR\gameserver\" -Recurse -Force

# 2. Login dist → C:\server\login\
Write-Host "  [2/5] Copying login server..." -ForegroundColor Green
New-Item -ItemType Directory -Force -Path "$DEPLOY_DIR\login" | Out-Null
Remove-Item "$DEPLOY_DIR\login\*" -Recurse -Force -ErrorAction SilentlyContinue
Copy-Item "$GS_DIST\login\*" "$DEPLOY_DIR\login\" -Recurse -Force

# 3. Data from datapack → C:\server\gameserver\data\
Write-Host "  [3/5] Copying data files..." -ForegroundColor Green
New-Item -ItemType Directory -Force -Path "$DEPLOY_DIR\gameserver\data" | Out-Null
Remove-Item "$DEPLOY_DIR\gameserver\data\*" -Recurse -Force -ErrorAction SilentlyContinue
Copy-Item "$DP_BUILD\gameserver\data\*" "$DEPLOY_DIR\gameserver\data\" -Recurse -Force

# 4. SQL scripts
Write-Host "  [4/5] Copying SQL scripts..." -ForegroundColor Green
New-Item -ItemType Directory -Force -Path "$DEPLOY_DIR\sql" | Out-Null
Copy-Item "$DP_BUILD\sql\*.sql" "$DEPLOY_DIR\sql\" -Force

# 5. Tools
Write-Host "  [5/5] Copying tools..." -ForegroundColor Green
New-Item -ItemType Directory -Force -Path "$DEPLOY_DIR\tools" | Out-Null
Copy-Item "$DP_BUILD\tools\*" "$DEPLOY_DIR\tools\" -Recurse -Force

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  COPY COMPLETE!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Show deployed structure
Write-Host "  $DEPLOY_DIR\" -ForegroundColor Yellow
Get-ChildItem $DEPLOY_DIR | ForEach-Object {
    if ($_.PSIsContainer) {
        Write-Host "    📁 $($_.Name)\" -ForegroundColor Yellow
        # Show one level deeper
        Get-ChildItem $_.FullName -Depth 0 | ForEach-Object {
            if ($_.PSIsContainer) {
                Write-Host "      📁 $($_.Name)\" -ForegroundColor Gray
            } else {
                Write-Host "      📄 $($_.Name)" -ForegroundColor White
            }
        }
    } else {
        Write-Host "    📄 $($_.Name)" -ForegroundColor White
    }
}
Write-Host ""

Write-Host "Press any key to close..." -ForegroundColor Yellow
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
