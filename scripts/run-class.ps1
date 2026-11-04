# Starts the quiz for a real class. Run from anywhere:
#   powershell -ExecutionPolicy Bypass -File scripts\run-class.ps1            # app only
#   powershell -ExecutionPolicy Bypass -File scripts\run-class.ps1 -Tunnel    # app + internet link
#
# - Runs a COPY of the jar, so the code can be rebuilt (mvnw package) while the class is running;
#   to apply a Java fix, press Ctrl+C here and run this script again (about 15 s; phones reconnect
#   by themselves and nothing is lost: answers wait on the phones and the database is a file).
# - Serves the pages straight from src/main/resources/static, so a fix to HTML/JS reaches a phone as
#   soon as it reloads, with no restart.
# - Uses its own database (data/class), so test sessions don't mix with the real one.
# - -Tunnel opens a second window running a Cloudflare quick tunnel. Its https link appears on the
#   dashboard automatically (or paste it there). Restarting the app does NOT change the link; closing
#   the tunnel window does.
param(
    [switch]$Tunnel,
    [int]$Port = 8080,
    [string]$Database = "./data/class/quizdb"
)

$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$built = "target\quiz-system-0.1.0-SNAPSHOT.jar"
if (-not (Test-Path $built)) {
    Write-Host "Building the app (first time only)..."
    & .\mvnw.cmd -q -DskipTests package
}
New-Item -ItemType Directory -Force "target\run" | Out-Null
Copy-Item $built "target\run\quiz-system.jar" -Force

if ($Tunnel) {
    $cloudflared = (Get-Command cloudflared -ErrorAction SilentlyContinue).Source
    if (-not $cloudflared) { $cloudflared = "C:\Program Files (x86)\cloudflared\cloudflared.exe" }
    Start-Process -FilePath $cloudflared -ArgumentList @(
        "tunnel", "--no-autoupdate", "--metrics", "127.0.0.1:20241", "--url", "http://localhost:$Port")
    Write-Host "Tunnel starting in a new window. Its https://....trycloudflare.com link will appear on the dashboard."
}

Write-Host "Instructor dashboard: http://localhost:$Port/instructor.html   (Ctrl+C to stop)"
java -jar target\run\quiz-system.jar `
    "--server.port=$Port" `
    "--spring.datasource.url=jdbc:h2:file:$Database;AUTO_SERVER=TRUE" `
    "--spring.web.resources.static-locations=file:src/main/resources/static/"
