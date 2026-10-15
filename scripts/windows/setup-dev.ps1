<#
.SYNOPSIS
  Sets up the MahaLegal knowledge-graph development environment on Windows.

.DESCRIPTION
  Safe to run again at any time: every step checks what is already there.

    1. Installs missing tools with winget: Git, Temurin JDK 11 (required by
       Gradle 5.4.1 / Spring Boot 2.1), Node.js LTS, Docker Desktop.
    2. Clones (or updates) the repository into <Root>\spring-angular-auth.
    3. Starts MongoDB 4.4 in Docker (the version CI tests against) with a
       persistent volume.
    4. Downloads the backend dependencies (Gradle) and installs the frontend
       packages (npm ci).
    5. Writes helper scripts into <Root>: start-all, start-mongo,
       start-backend, start-frontend, load-sample-data, run-tests.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\setup-dev.ps1
  powershell -ExecutionPolicy Bypass -File .\setup-dev.ps1 -Root E:\work\kg -NoDocker
#>
[CmdletBinding()]
param(
    [string]$Root = 'D:\Legal_KnowlageGraph',
    [string]$RepoUrl = 'https://github.com/kalaskaratul321/spring-angular-auth.git',
    [string]$Branch = 'master',
    # Do not install tools with winget (check only).
    [switch]$SkipInstall,
    # Do not use Docker for MongoDB (install MongoDB 4.4 yourself).
    [switch]$NoDocker,
    # Skip the Gradle dependency download and npm install.
    [switch]$SkipBuild
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$RepoDir = Join-Path $Root 'spring-angular-auth'
$MongoContainer = 'mahalegal-mongo'
$MongoImage = 'mongo:4.4'
$script:Problems = New-Object System.Collections.Generic.List[string]

function Write-Step([string]$Text) { Write-Host ''; Write-Host "==> $Text" -ForegroundColor Cyan }
function Write-Ok([string]$Text) { Write-Host "    [ok] $Text" -ForegroundColor Green }
function Write-Warn2([string]$Text) { Write-Host "    [!]  $Text" -ForegroundColor Yellow; $script:Problems.Add($Text) }

function Test-Command([string]$Name) {
    return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

# Picks up PATH changes made by installers without opening a new terminal.
function Update-SessionPath {
    $machine = [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $user = [Environment]::GetEnvironmentVariable('Path', 'User')
    $env:Path = (@($machine, $user) | Where-Object { $_ }) -join ';'
}

function Install-WithWinget([string]$Id, [string]$Label) {
    if ($SkipInstall) {
        Write-Warn2 "$Label is missing (-SkipInstall given). Install it with: winget install --id $Id -e"
        return $false
    }
    if (-not (Test-Command 'winget')) {
        Write-Warn2 "$Label is missing and winget is not available. Install 'App Installer' from the Microsoft Store, or install $Label manually."
        return $false
    }
    Write-Host "    installing $Label ($Id) ..."
    & winget install --id $Id --exact --silent --accept-package-agreements --accept-source-agreements
    if ($LASTEXITCODE -ne 0) {
        Write-Warn2 "winget could not install $Label (exit code $LASTEXITCODE). Install it manually and run this script again."
        return $false
    }
    Update-SessionPath
    return $true
}

# Temurin 11 installs to C:\Program Files\Eclipse Adoptium\jdk-11.x.y-hotspot.
function Find-Jdk11 {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    foreach ($base in @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft")) {
        if (Test-Path $base) {
            $candidates += Get-ChildItem -Path $base -Directory -Filter 'jdk-11*' -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending | ForEach-Object { $_.FullName }
        }
    }
    foreach ($dir in $candidates) {
        $release = Join-Path $dir 'release'
        if ((Test-Path (Join-Path $dir 'bin\java.exe')) -and (Test-Path $release)) {
            if ((Get-Content $release -Raw) -match 'JAVA_VERSION="11\.') { return $dir }
        }
    }
    return $null
}

function Get-NodeMajorMinor {
    if (-not (Test-Command 'node')) { return $null }
    $v = (& node --version) -replace '^v', ''
    $parts = $v.Split('.')
    return [pscustomobject]@{ Major = [int]$parts[0]; Minor = [int]$parts[1]; Text = $v }
}

# Angular 20 needs Node ^20.19 || ^22.12 || >=24.
function Test-NodeSupported($n) {
    if (-not $n) { return $false }
    if ($n.Major -eq 20) { return $n.Minor -ge 19 }
    if ($n.Major -eq 22) { return $n.Minor -ge 12 }
    return $n.Major -ge 24
}

function Test-DockerRunning {
    if (-not (Test-Command 'docker')) { return $false }
    & docker info *> $null
    return ($LASTEXITCODE -eq 0)
}

function Write-HelperFile([string]$Name, [string]$Content) {
    $path = Join-Path $Root $Name
    Set-Content -Path $path -Value $Content -Encoding UTF8
    Write-Ok "wrote $path"
}

# ---------------------------------------------------------------- 1. tools

Write-Step 'Checking tools'
$onWindows = ($env:OS -eq 'Windows_NT')
if (-not $onWindows) { Write-Warn2 'This script is meant for Windows; tool installation and Docker steps may not apply.' }

if (Test-Command 'git') { Write-Ok ((& git --version) -join ' ') }
else { [void](Install-WithWinget 'Git.Git' 'Git'); if (Test-Command 'git') { Write-Ok 'Git installed' } }

$jdk = Find-Jdk11
if (-not $jdk) {
    [void](Install-WithWinget 'EclipseAdoptium.Temurin.11.JDK' 'Temurin JDK 11')
    $jdk = Find-Jdk11
}
if ($jdk) { Write-Ok "JDK 11: $jdk" } else { Write-Warn2 'JDK 11 not found. The backend needs JDK 11 (newer JDKs do not work with this Gradle/Spring Boot version).' }

$node = Get-NodeMajorMinor
if (-not (Test-NodeSupported $node)) {
    if ($node) { Write-Host "    Node $($node.Text) is too old for Angular 20 (needs 20.19+, 22.12+ or 24+)." }
    [void](Install-WithWinget 'OpenJS.NodeJS.LTS' 'Node.js LTS')
    $node = Get-NodeMajorMinor
}
if (Test-NodeSupported $node) { Write-Ok "Node $($node.Text), npm $((& npm --version) -join '')" }
else { Write-Warn2 'A supported Node.js was not found (needs 20.19+, 22.12+ or 24+).' }

$useDocker = -not $NoDocker
if ($useDocker -and -not (Test-Command 'docker')) {
    [void](Install-WithWinget 'Docker.DockerDesktop' 'Docker Desktop')
    if (Test-Command 'docker') {
        Write-Warn2 'Docker Desktop was just installed. Start it (a restart may be needed for WSL 2), then run this script again to start MongoDB.'
    }
}

# ---------------------------------------------------------------- 2. repository

Write-Step "Repository in $RepoDir"
if (-not (Test-Path $Root)) { New-Item -ItemType Directory -Path $Root | Out-Null; Write-Ok "created $Root" }
if (-not (Test-Command 'git')) {
    Write-Warn2 'Git is not available; cannot clone the repository.'
} elseif (Test-Path (Join-Path $RepoDir '.git')) {
    Push-Location $RepoDir
    try {
        $dirty = (& git status --porcelain)
        if ($dirty) {
            Write-Warn2 'The repository has local changes; not updating it. Commit or stash them and run again to pull the latest code.'
        } else {
            & git fetch origin $Branch
            & git checkout $Branch
            & git pull --ff-only origin $Branch
            if ($LASTEXITCODE -eq 0) { Write-Ok "up to date with origin/$Branch" } else { Write-Warn2 "git pull failed; resolve it in $RepoDir" }
        }
    } finally { Pop-Location }
} else {
    # autocrlf: Windows line endings for .bat files, whatever the global Git setting is.
    & git clone -c core.autocrlf=true --branch $Branch $RepoUrl $RepoDir
    if ($LASTEXITCODE -ne 0) { throw "git clone failed. If the repository is private, sign in to GitHub (e.g. 'gh auth login' or Git Credential Manager) and run again." }
    Write-Ok 'cloned'
}

# ---------------------------------------------------------------- 3. MongoDB

Write-Step "MongoDB ($MongoImage)"
if (-not $useDocker) {
    Write-Host '    -NoDocker: install MongoDB 4.4 Community yourself and keep it on localhost:27017.'
    Write-Host '    Archive: https://www.mongodb.com/try/download/community-edition/releases/archive'
} elseif (-not (Test-DockerRunning)) {
    Write-Warn2 'Docker is not running. Start Docker Desktop, then run start-mongo.ps1 (or this script) to start MongoDB.'
} else {
    $existing = (& docker ps -a --filter "name=^/$MongoContainer$" --format '{{.Status}}')
    if (-not $existing) {
        & docker run -d --name $MongoContainer -p 27017:27017 -v mahalegal-mongo-data:/data/db --restart unless-stopped $MongoImage | Out-Null
        if ($LASTEXITCODE -eq 0) { Write-Ok "container $MongoContainer started (data in volume mahalegal-mongo-data)" }
        else { Write-Warn2 'docker run failed. Is port 27017 already used by another MongoDB?' }
    } elseif ($existing -notmatch '^Up') {
        & docker start $MongoContainer | Out-Null
        Write-Ok "container $MongoContainer started"
    } else {
        Write-Ok "container $MongoContainer already running"
    }
}

# ---------------------------------------------------------------- 4. build

if (-not $SkipBuild -and (Test-Path $RepoDir)) {
    Write-Step 'Backend: downloading Gradle and dependencies (first run takes a few minutes)'
    if ($jdk) {
        Push-Location $RepoDir
        try {
            $env:JAVA_HOME = $jdk
            & .\gradlew.bat --no-daemon compileJava compileTestJava
            if ($LASTEXITCODE -eq 0) { Write-Ok 'backend compiles' } else { Write-Warn2 'Gradle build failed; see the output above.' }
        } finally { Pop-Location }
    } else {
        Write-Warn2 'Skipped: JDK 11 missing.'
    }

    Write-Step 'Frontend: npm ci'
    if (Test-NodeSupported $node) {
        Push-Location (Join-Path $RepoDir 'frontend')
        try {
            & npm ci
            if ($LASTEXITCODE -eq 0) { Write-Ok 'frontend packages installed' } else { Write-Warn2 'npm ci failed; see the output above.' }
        } finally { Pop-Location }
    } else {
        Write-Warn2 'Skipped: supported Node.js missing.'
    }
}

# ---------------------------------------------------------------- 5. helper scripts

Write-Step "Helper scripts in $Root"

Write-HelperFile 'dev-common.ps1' @'
# Shared settings for the helper scripts (dot-sourced).
$Root = $PSScriptRoot
$RepoDir = Join-Path $Root 'spring-angular-auth'
$MongoContainer = 'mahalegal-mongo'
$BackendUrl = 'http://localhost:8080'
$FrontendUrl = 'http://localhost:4200'

function Update-SessionPath {
    $machine = [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $user = [Environment]::GetEnvironmentVariable('Path', 'User')
    $env:Path = (@($machine, $user) | Where-Object { $_ }) -join ';'
}

function Find-Jdk11 {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    foreach ($base in @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft")) {
        if (Test-Path $base) {
            $candidates += Get-ChildItem -Path $base -Directory -Filter 'jdk-11*' -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending | ForEach-Object { $_.FullName }
        }
    }
    foreach ($dir in $candidates) {
        $release = Join-Path $dir 'release'
        if ((Test-Path (Join-Path $dir 'bin\java.exe')) -and (Test-Path $release)) {
            if ((Get-Content $release -Raw) -match 'JAVA_VERSION="11\.') { return $dir }
        }
    }
    return $null
}

# True when something answers HTTP on the URL (any status, including 401).
function Test-HttpUp([string]$Url) {
    try {
        Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3 | Out-Null
        return $true
    } catch {
        return [bool]($_.Exception.PSObject.Properties['Response'] -and $_.Exception.Response)
    }
}

function Wait-HttpUp([string]$Url, [int]$Seconds) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-HttpUp $Url) { return $true }
        Start-Sleep -Seconds 3
    }
    return $false
}

function Test-PortInUse([int]$Port) {
    return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

Update-SessionPath
'@

Write-HelperFile 'start-mongo.ps1' @'
# Starts the MongoDB 4.4 container used for development.
. (Join-Path $PSScriptRoot 'dev-common.ps1')
$ErrorActionPreference = 'Stop'
& docker info *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker is not running. Start Docker Desktop and try again.' }
$status = (& docker ps -a --filter "name=^/$MongoContainer$" --format '{{.Status}}')
if (-not $status) {
    & docker run -d --name $MongoContainer -p 27017:27017 -v mahalegal-mongo-data:/data/db --restart unless-stopped mongo:4.4 | Out-Null
} elseif ($status -notmatch '^Up') {
    & docker start $MongoContainer | Out-Null
}
Write-Host "MongoDB is running on localhost:27017 (container $MongoContainer)." -ForegroundColor Green
'@

Write-HelperFile 'start-backend.ps1' @'
# Runs the Spring Boot backend on http://localhost:8080 with JDK 11.
. (Join-Path $PSScriptRoot 'dev-common.ps1')
$ErrorActionPreference = 'Stop'
$jdk = Find-Jdk11
if (-not $jdk) { throw 'JDK 11 not found. Run setup-dev.ps1 or install Temurin 11: winget install --id EclipseAdoptium.Temurin.11.JDK -e' }
if (Test-PortInUse 8080) { throw 'Port 8080 is already in use (is the backend already running?).' }
$env:JAVA_HOME = $jdk
Set-Location $RepoDir
Write-Host "Starting backend with JAVA_HOME=$jdk ..." -ForegroundColor Cyan
& .\gradlew.bat bootRun
'@

Write-HelperFile 'start-frontend.ps1' @'
# Runs the Angular dev server on http://localhost:4200 (/api is proxied to :8080).
. (Join-Path $PSScriptRoot 'dev-common.ps1')
$ErrorActionPreference = 'Stop'
if (Test-PortInUse 4200) { throw 'Port 4200 is already in use (is the frontend already running?).' }
Set-Location (Join-Path $RepoDir 'frontend')
if (-not (Test-Path 'node_modules')) { & npm ci }
& npm start
'@

Write-HelperFile 'load-sample-data.ps1' @'
# Loads the illustrative sample cases into the running backend.
param(
    [string]$Email = 'admin@sandbox.com',
    [string]$Password = 'password'
)
. (Join-Path $PSScriptRoot 'dev-common.ps1')
$ErrorActionPreference = 'Stop'
if (-not (Test-HttpUp $BackendUrl)) { throw "Backend is not running on $BackendUrl. Start it with start-backend.ps1 first." }

$login = Invoke-RestMethod -Method Post -Uri "$BackendUrl/api/auth/login" -ContentType 'application/json' `
    -Body (@{ email = $Email; password = $Password } | ConvertTo-Json)
$headers = @{ Authorization = "Bearer $($login.token)" }

$sample = Join-Path $RepoDir 'src\main\resources\kg\sample-cases.json'
$bytes = [System.Text.Encoding]::UTF8.GetBytes((Get-Content $sample -Raw -Encoding UTF8))
$saved = Invoke-RestMethod -Method Post -Uri "$BackendUrl/api/kg/cases/bulk" -Headers $headers `
    -ContentType 'application/json; charset=utf-8' -Body $bytes
Write-Host "Loaded $(@($saved).Count) sample cases." -ForegroundColor Green

$summary = Invoke-RestMethod -Uri "$BackendUrl/api/kg/categories/summary" -Headers $headers
Write-Host 'Cases per category:'
# foreach (not the pipeline): Invoke-RestMethod can hand a JSON array over as one object.
foreach ($s in $summary) {
    if ($s.totalCases -gt 0) { Write-Host ('  {0,-28} {1,5}' -f $s.displayName, $s.totalCases) }
}
'@

Write-HelperFile 'run-tests.ps1' @'
# Runs backend tests (needs MongoDB) and frontend unit tests (needs Chrome).
. (Join-Path $PSScriptRoot 'dev-common.ps1')
$ErrorActionPreference = 'Stop'
$jdk = Find-Jdk11
if (-not $jdk) { throw 'JDK 11 not found.' }
$env:JAVA_HOME = $jdk
Set-Location $RepoDir
& .\gradlew.bat test
$backend = $LASTEXITCODE
Set-Location (Join-Path $RepoDir 'frontend')
& npm run test:ci
$frontend = $LASTEXITCODE
Write-Host ''
Write-Host "Backend tests:  $(if ($backend -eq 0) { 'passed' } else { 'FAILED (report: spring-angular-auth\build\reports\tests\test\index.html)' })"
Write-Host "Frontend tests: $(if ($frontend -eq 0) { 'passed' } else { 'FAILED' })"
if ($backend -ne 0 -or $frontend -ne 0) { exit 1 }
'@

Write-HelperFile 'start-all.ps1' @'
# Starts MongoDB, the backend and the frontend (each in its own window),
# waits for them, optionally loads the sample data, and opens the browser.
param([switch]$LoadSampleData)
. (Join-Path $PSScriptRoot 'dev-common.ps1')
$ErrorActionPreference = 'Stop'

& (Join-Path $PSScriptRoot 'start-mongo.ps1')

$shell = (Get-Process -Id $PID).Path
if (-not (Test-PortInUse 8080)) {
    Start-Process $shell -ArgumentList '-NoExit', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $PSScriptRoot 'start-backend.ps1')
}
if (-not (Test-PortInUse 4200)) {
    Start-Process $shell -ArgumentList '-NoExit', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $PSScriptRoot 'start-frontend.ps1')
}

Write-Host 'Waiting for the backend on :8080 (first start can take a minute) ...'
if (-not (Wait-HttpUp $BackendUrl 300)) { throw 'Backend did not start; check its window for errors.' }
Write-Host 'Waiting for the frontend on :4200 ...'
if (-not (Wait-HttpUp $FrontendUrl 300)) { throw 'Frontend did not start; check its window for errors.' }

if ($LoadSampleData) { & (Join-Path $PSScriptRoot 'load-sample-data.ps1') }

Write-Host "Ready: $FrontendUrl  (sign in as admin@sandbox.com / password)" -ForegroundColor Green
Start-Process $FrontendUrl
'@

Write-HelperFile 'README-DEV.md' @'
# MahaLegal knowledge graph - local development

| Script | What it does |
|---|---|
| `start-all.ps1 [-LoadSampleData]` | MongoDB + backend + frontend, then opens http://localhost:4200 |
| `start-mongo.ps1` | MongoDB 4.4 in Docker (container `mahalegal-mongo`, data kept in volume `mahalegal-mongo-data`) |
| `start-backend.ps1` | Spring Boot on :8080 with JDK 11 (`gradlew bootRun`) |
| `start-frontend.ps1` | Angular dev server on :4200, `/api` proxied to :8080 |
| `load-sample-data.ps1` | Logs in as the seeded admin and loads `sample-cases.json` |
| `run-tests.ps1` | Backend tests (need MongoDB running) and frontend tests (need Chrome) |
| `setup-dev.ps1` | Re-run any time to install missing tools or pull the latest code |

Run a script with: `powershell -ExecutionPolicy Bypass -File .\start-all.ps1 -LoadSampleData`

Default login: `admin@sandbox.com` / `password` (created on first backend start; change it for anything shared).

Code: `spring-angular-auth\` (backend `src\`, frontend `frontend\`, docs in `docs\`).
Recommended editors: IntelliJ IDEA (open `spring-angular-auth` as a Gradle project, set the project SDK to JDK 11)
and VS Code for `frontend\` (Angular Language Service extension).
'@

# Keep a copy of this script next to the helpers for re-runs.
if ($PSCommandPath -and ((Split-Path $PSCommandPath -Parent) -ne $Root)) {
    Copy-Item -Path $PSCommandPath -Destination (Join-Path $Root 'setup-dev.ps1') -Force
}

# ---------------------------------------------------------------- summary

Write-Step 'Summary'
if ($script:Problems.Count -eq 0) {
    Write-Host '    Everything is set up.' -ForegroundColor Green
} else {
    Write-Host '    Set up with some open items:' -ForegroundColor Yellow
    foreach ($p in $script:Problems) { Write-Host "      - $p" -ForegroundColor Yellow }
}
Write-Host ''
Write-Host "    Next: cd $Root"
Write-Host '          powershell -ExecutionPolicy Bypass -File .\start-all.ps1 -LoadSampleData'
Write-Host '    Then sign in at http://localhost:4200 as admin@sandbox.com / password'
