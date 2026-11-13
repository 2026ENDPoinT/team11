param([string]$BindAddress = '127.0.0.1', [int]$Port = 8000)
$ErrorActionPreference = 'Stop'
if (-not $env:AI_INTERNAL_KEY) { throw 'Set AI_INTERNAL_KEY before starting.' }
$workspace = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $workspace
try {
    $retryDelay = 1
    while ($true) {
        $startedAt = Get-Date
        & python -B -X utf8 -m ai_server --host $BindAddress --port $Port
        $serverExitCode = $LASTEXITCODE
        if ($serverExitCode -eq 0 -or $serverExitCode -eq 2) { break }
        if (((Get-Date) - $startedAt).TotalSeconds -gt 60) { $retryDelay = 1 }
        Write-Warning "Server stopped with code $serverExitCode; restarting in $retryDelay seconds."
        Start-Sleep -Seconds $retryDelay
        $retryDelay = [Math]::Min(30, $retryDelay * 2)
    }
} finally {
    Pop-Location
}
