param(
    [Parameter(Mandatory)][string]$QaRoot,
    [Parameter(Mandatory)][string]$OutputRoot,
    [int]$CaptureSeconds = 180
)

$ErrorActionPreference = 'Stop'
$QaRoot = (Resolve-Path -LiteralPath $QaRoot).Path
if ((Split-Path -Leaf $QaRoot) -ne 'run-performance-audit') { throw 'Refusing to run outside run-performance-audit.' }
$properties = Get-Content -LiteralPath (Join-Path $QaRoot 'server.properties')
if ($properties -notcontains 'level-name=world-qa' -or $properties -contains 'server-port=25565') {
    throw 'QA server isolation check failed.'
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$run = Join-Path $OutputRoot "idle-$stamp"
New-Item -ItemType Directory -Path $run -Force | Out-Null
$stdout = Join-Path $run 'server-console.log'
$stderr = Join-Path $run 'server-stderr.log'
$metrics = Join-Path $run 'process-metrics.csv'

$psi = [Diagnostics.ProcessStartInfo]::new()
$psi.FileName = 'java'
$psi.WorkingDirectory = $QaRoot
$psi.Arguments = '@user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.226/win_args.txt nogui'
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true
$process = [Diagnostics.Process]::new()
$process.StartInfo = $psi
$launchTime = Get-Date
$process.Start() | Out-Null

$outTask = $process.StandardOutput.ReadToEndAsync()
$errTask = $process.StandardError.ReadToEndAsync()
$started = $false
try {
    $deadline = (Get-Date).AddMinutes(8)
    while ((Get-Date) -lt $deadline -and -not $process.HasExited) {
        Start-Sleep -Seconds 2
        # latest.log is QA-local and receives the authoritative startup marker.
        $latest = Join-Path $QaRoot 'logs\latest.log'
        if ((Test-Path -LiteralPath $latest) -and (Get-Item -LiteralPath $latest).LastWriteTime -gt $launchTime) {
            if (Select-String -LiteralPath $latest -Pattern 'Done \(.+\)! For help' -Quiet) { $started = $true; break }
        }
    }
    if (-not $started) { throw 'QA server did not reach the Done marker within eight minutes.' }

    $process.StandardInput.WriteLine("spark profiler start --timeout $CaptureSeconds")
    $samples = [Collections.Generic.List[object]]::new()
    $previousCpu = $process.TotalProcessorTime.TotalSeconds
    for ($i=0; $i -lt $CaptureSeconds; $i++) {
        Start-Sleep -Seconds 1
        $process.Refresh()
        $cpu = $process.TotalProcessorTime.TotalSeconds
        $samples.Add([pscustomobject]@{
            timestamp=(Get-Date).ToString('o'); elapsed_s=$i+1
            cpu_percent=[math]::Round(($cpu-$previousCpu)*100,2)
            working_set_bytes=$process.WorkingSet64; private_bytes=$process.PrivateMemorySize64
            peak_working_set_bytes=$process.PeakWorkingSet64
        })
        $previousCpu=$cpu
    }
    $samples | Export-Csv -LiteralPath $metrics -NoTypeInformation -Encoding utf8
    Start-Sleep -Seconds 10
    $process.StandardInput.WriteLine('spark healthreport')
    Start-Sleep -Seconds 15
} finally {
    if (-not $process.HasExited) {
        $process.StandardInput.WriteLine('stop')
        if (-not $process.WaitForExit(120000)) { throw 'QA server did not stop gracefully within two minutes.' }
    }
    [IO.File]::WriteAllText($stdout, $outTask.Result)
    [IO.File]::WriteAllText($stderr, $errTask.Result)
    [pscustomobject]@{start_marker_found=$started; exit_code=$process.ExitCode; capture_seconds=$CaptureSeconds; qa_root=$QaRoot; output=$run} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $run 'run-metadata.json') -Encoding utf8
}

Write-Output $run
