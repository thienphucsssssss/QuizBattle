param([switch]$Elevated)
$ErrorActionPreference='Stop'
$nqbRoot=Split-Path -Parent $PSScriptRoot
$nqbAdmin=([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if(-not $nqbAdmin) {
    if($Elevated){throw 'Administrator access is needed to create LAN firewall rules.'}
    $nqbArguments='-NoProfile -ExecutionPolicy Bypass -File "'+$PSCommandPath+'" -Elevated'
    $nqbChild=Start-Process -FilePath 'powershell.exe' -ArgumentList $nqbArguments -Verb RunAs -WindowStyle Hidden -PassThru -Wait
    exit $nqbChild.ExitCode
}
. (Join-Path $PSScriptRoot 'Server-Settings.ps1')
$nqbSettings=Read-NqbSettings
Write-NqbRuntimeConfig $nqbSettings
$nqbJava=(Resolve-Path -LiteralPath (Join-Path $nqbRoot 'runtime/bin/java.exe')).Path
$nqbSha=[Security.Cryptography.SHA256]::Create()
try {$nqbId=([BitConverter]::ToString($nqbSha.ComputeHash([Text.Encoding]::UTF8.GetBytes($nqbJava)))).Replace('-','').Substring(0,12)}finally{$nqbSha.Dispose()}
foreach($nqbRule in @(@('TCP',[int]$nqbSettings.server.port),@('UDP',[int]$nqbSettings.discovery.port))) {
    if($nqbRule[1] -lt 1){throw 'Use a fixed nonzero TCP port before configuring the firewall.'}
    $nqbName='QuizBattle-LAN-'+$nqbId+'-'+$nqbRule[0]
    $nqbExisting=Get-NetFirewallRule -Name $nqbName -ErrorAction SilentlyContinue
    if($nqbExisting) {
        Set-NetFirewallRule -Name $nqbName -Enabled True -Direction Inbound -Action Allow -Profile Any -Program $nqbJava -Protocol $nqbRule[0] -LocalPort $nqbRule[1] -RemoteAddress LocalSubnet | Out-Null
    } else {
        New-NetFirewallRule -Name $nqbName -DisplayName ('QuizBattle LAN '+$nqbRule[0]) -Enabled True -Direction Inbound -Action Allow -Profile Any -Program $nqbJava -Protocol $nqbRule[0] -LocalPort $nqbRule[1] -RemoteAddress LocalSubnet | Out-Null
    }
}
Write-Host 'QUIZBATTLE_LAN_FIREWALL_READY: TCP/UDP, this Java runtime, LocalSubnet only, all network profiles.'
