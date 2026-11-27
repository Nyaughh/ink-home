<# Capture each named screen after the operator navigates to it. Use -Screens for
   a subset or for new destinations. Set ANDROID_SERIAL to select a device. #>
param(
    [Parameter(Mandatory, Position=0)]
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9_-]*$')]
    [string]$Phase,
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9_-]*$')]
    [string[]]$Screens = @('home', 'menu', 'library-grid', 'library-list', 'book-details',
        'collections', 'search', 'apps', 'hidden-apps', 'stats-overview', 'stats-calendar',
        'settings', 'reading-apps', 'wallpaper', 'sleep-screen', 'control-panel', 'reader',
        'reader-controls', 'reader-contents', 'reader-text', 'ink-lab')
)
$ErrorActionPreference = 'Stop'
$adb = (Get-Command adb -CommandType Application).Source
& $adb get-state
if ($LASTEXITCODE -ne 0) { throw 'Select a connected device with ANDROID_SERIAL.' }
$folder = Join-Path (Split-Path $PSScriptRoot) "docs/shots/next/$Phase"
New-Item -ItemType Directory -Force $folder | Out-Null
foreach ($screen in $Screens) {
    $answer = Read-Host "Open $screen. Enter to capture, s to skip, q to finish"
    if ($answer -eq 's') { continue }
    if ($answer -eq 'q') { break }
    $target = Join-Path $folder "$screen.png"
    $temp = [System.IO.Path]::GetTempFileName()
    try {
        # PowerShell 5.1 corrupts native binary output sent through its pipeline.
        $info = New-Object Diagnostics.ProcessStartInfo
        $info.FileName = $adb
        $info.Arguments = 'exec-out screencap -p'
        $info.UseShellExecute = $false
        $info.RedirectStandardOutput = $true
        $proc = [Diagnostics.Process]::Start($info)
        try {
            $stream = [IO.File]::Create($temp)
            try { $proc.StandardOutput.BaseStream.CopyTo($stream) } finally { $stream.Dispose() }
            $proc.WaitForExit()
            if ($proc.ExitCode -ne 0) { throw "Capture failed for $screen" }
        } finally { $proc.Dispose() }
        $reader = [IO.File]::OpenRead($temp)
        try {
            $sig = New-Object byte[] 8
            if ($reader.Read($sig, 0, 8) -ne 8 -or [BitConverter]::ToString($sig) -ne '89-50-4E-47-0D-0A-1A-0A') {
                throw "Device returned no PNG for $screen"
            }
        } finally { $reader.Dispose() }
        Copy-Item -LiteralPath $temp -Destination $target -Force
        Write-Output $target
    } finally { Remove-Item -LiteralPath $temp -Force }
}
