param([switch]$Elevated)
$ErrorActionPreference='Stop'
$nqbRoot=Split-Path -Parent $PSScriptRoot
$nqbAdmin=([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if(-not $nqbAdmin) {
    if($Elevated){throw 'Administrator access is needed to create the Server firewall rule.'}
    $nqbArguments='-NoProfile -ExecutionPolicy Bypass -File "'+$PSCommandPath+'" -Elevated'
    $nqbChild=Start-Process -FilePath 'powershell.exe' -ArgumentList $nqbArguments -Verb RunAs -WindowStyle Hidden -PassThru -Wait
    exit $nqbChild.ExitCode
}
. (Join-Path $PSScriptRoot 'Server-Settings.ps1')
$nqbSettings=Read-NqbSettings
$nqbPort=[int]$nqbSettings.server.port
if($nqbPort -lt 1 -or $nqbPort -gt 65535){throw 'Configure a fixed TCP port first.'}
$nqbJava=(Resolve-Path -LiteralPath (Join-Path $nqbRoot 'runtime/bin/java.exe')).Path
$nqbInterfaces=@(Get-NetAdapter | Where-Object {$_.InterfaceDescription -match 'Tailscale' -and $_.Status -eq 'Up'})
if($nqbInterfaces.Count -ne 1){throw 'Install and connect Tailscale first; exactly one active Tailscale adapter is required.'}
$nqbSha=[Security.Cryptography.SHA256]::Create()
try {$nqbId=([BitConverter]::ToString($nqbSha.ComputeHash([Text.Encoding]::UTF8.GetBytes($nqbJava)))).Replace('-','').Substring(0,12)}finally{$nqbSha.Dispose()}
$nqbName='QuizBattle-Tailscale-'+$nqbId
$nqbArgs=@{Enabled='True';Direction='Inbound';Action='Allow';Profile='Any';Program=$nqbJava;Protocol='TCP';LocalPort=$nqbPort;RemoteAddress=@('100.64.0.0/10','fd7a:115c:a1e0::/48');InterfaceAlias=$nqbInterfaces[0].Name}
if(Get-NetFirewallRule -Name $nqbName -ErrorAction SilentlyContinue){Set-NetFirewallRule -Name $nqbName @nqbArgs | Out-Null}
else {New-NetFirewallRule -Name $nqbName -DisplayName 'QuizBattle TCP over Tailscale' @nqbArgs | Out-Null}
Write-Host 'QUIZBATTLE_TAILSCALE_FIREWALL_READY: only this Java runtime, TCP port, Tailscale interface and address ranges.'
