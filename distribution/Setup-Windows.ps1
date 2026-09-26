[CmdletBinding()]
param(
    [ValidateSet('Menu','Install','AllowAll','RestoreCatalog','ApprovePackage','Status','OpenSettings')]
    [string]$Action='Menu',
    [string]$AdbPath,
    [string]$Serial,
    [string]$PackageName
)
$ErrorActionPreference='Stop'
Set-StrictMode -Version 2.0
$catalogProperty='persist.sys.tc.allow.third.install'
$toolboxPackage='com.rc29.toolbox'

function Find-Adb {
    if ($AdbPath) {
        if (!(Test-Path -LiteralPath $AdbPath -PathType Leaf)) { throw 'The supplied adb.exe path does not exist.' }
        return (Resolve-Path -LiteralPath $AdbPath).Path
    }
    $candidates=@((Join-Path $PSScriptRoot 'platform-tools\adb.exe'))
    if ($env:LOCALAPPDATA) { $candidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe') }
    foreach($candidate in $candidates) { if(Test-Path -LiteralPath $candidate -PathType Leaf) {return $candidate} }
    $found=Get-Command adb.exe -ErrorAction SilentlyContinue
    if($found) {return $found.Source}
    throw 'Android Platform Tools were not found. Download them from https://developer.android.com/tools/releases/platform-tools and place the extracted platform-tools folder beside this script. Then run Start-Windows.cmd again.'
}

function Invoke-Adb {
    param([string[]]$Command)
    $lines=@(& $script:adb @Command 2>&1 | ForEach-Object {"$_"})
    if($LASTEXITCODE -ne 0) {throw ($lines -join [Environment]::NewLine)}
    return $lines
}
function Invoke-Device {
    param([string[]]$Command)
    Invoke-Adb -Command (@('-s',$script:selectedSerial)+$Command)
}
function Read-Device {
    param([string[]]$Command)
    return ((Invoke-Device -Command $Command) -join "`n").Trim()
}
function Select-Device {
    $listing=Invoke-Adb -Command @('devices')
    $ready=@()
    foreach($line in $listing) {
        if($line -match '^([^\s]+)\s+device$') {$ready += $Matches[1]}
    }
    if($Serial) {
        if($ready -notcontains $Serial) {throw 'That device is not connected and authorized. Check the USB debugging prompt on the RC29.'}
        return $Serial
    }
    if($ready.Count -eq 0) {throw 'No authorized device was found. Enable USB debugging, use a data-capable cable, and accept the computer authorization on the RC29.'}
    if($ready.Count -eq 1) {return $ready[0]}
    Write-Host 'More than one device is connected. Choose the RC29:'
    for($index=0;$index -lt $ready.Count;$index++) {Write-Host ('{0}. {1}' -f ($index+1),$ready[$index])}
    $choice=Read-Host 'Device number'
    $number=0
    if(![int]::TryParse($choice,[ref]$number) -or $number -lt 1 -or $number -gt $ready.Count) {throw 'No valid device was selected.'}
    return $ready[$number-1]
}
function Check-Model {
    $manufacturer=Read-Device -Command @('shell','getprop','ro.product.manufacturer')
    $model=Read-Device -Command @('shell','getprop','ro.product.model')
    $sdk=Read-Device -Command @('shell','getprop','ro.build.version.sdk')
    Write-Host ('Connected: {0} {1}, Android API {2}' -f $manufacturer,$model,$sdk)
    $script:knownModel=($manufacturer -ieq 'TC' -and $model -ieq 't88' -and $sdk -eq '29')
    $script:currentUser=Read-Device -Command @('shell','am','get-current-user')
    if($script:currentUser -notmatch '^\d+$') {throw 'Could not determine the active Android user.'}
}
function Require-Model {
    if(!$script:knownModel) {
        Write-Host 'This beta was tested on TC t88 / Android 10. This device differs.'
        if((Read-Host 'Continue on this device? Type YES') -cne 'YES') {throw 'No changes made.'}
    }
}
function Show-Status {
    $value=Read-Device -Command @('shell','getprop',$catalogProperty)
    if(!$value) {$value='unset (default false on the tested firmware)'}
    Write-Host ('Catalog bypass: {0}' -f $value)
    Write-Host ('Developer mode: {0}' -f (Read-Device -Command @('shell','settings','get','global','development_settings_enabled')))
    Write-Host ('USB debugging: {0}' -f (Read-Device -Command @('shell','settings','get','global','adb_enabled')))
}
function Set-Catalog {
    param([bool]$Allow)
    Require-Model
    $value=if($Allow){'1'}else{'0'}
    if($Allow) {Write-Host 'Allowing catalog bypass for Google Play AND sideloaded APKs.'}
    else {Write-Host 'Restoring the catalog restriction. Existing apps and individual approvals remain.'}
    Invoke-Device -Command @('shell','setprop',$catalogProperty,$value) | Out-Null
    if((Read-Device -Command @('shell','getprop',$catalogProperty)) -ne $value) {throw 'The device did not confirm the requested catalog setting.'}
    Write-Host ('Verified catalog bypass value: {0}' -f $value)
}
function Install-Toolbox {
    Require-Model
    $apk=Join-Path $PSScriptRoot 'RC29-Toolbox.apk'
    if(!(Test-Path -LiteralPath $apk -PathType Leaf)) {throw 'RC29-Toolbox.apk is missing. Extract the whole download into one folder.'}
    $old=Read-Device -Command @('shell','settings','--user',$script:currentUser,'get','system',$toolboxPackage)
    $changed=$false
    # Some earlier T88 firmware accepts the master property but still applies
    # its per-package allowlist. Always approve Toolbox itself before install.
    if($old -ne '1') {
        Invoke-Device -Command @('shell','settings','--user',$script:currentUser,'put','system',$toolboxPackage,'1') | Out-Null
        $changed=$true
        if((Read-Device -Command @('shell','settings','--user',$script:currentUser,'get','system',$toolboxPackage)) -ne '1') {throw 'Could not approve Toolbox for installation.'}
    }
    try {
        Invoke-Device -Command @('install','-r','--user',$script:currentUser,$apk) | Write-Host
    } catch {
        if($changed) {
            if($old -eq 'null') {Invoke-Device -Command @('shell','settings','--user',$script:currentUser,'delete','system',$toolboxPackage) | Out-Null}
            else {Invoke-Device -Command @('shell','settings','--user',$script:currentUser,'put','system',$toolboxPackage,$old) | Out-Null}
        }
        throw
    }
    Invoke-Device -Command @('shell','am','start','-n','com.rc29.toolbox/com.rc29.toolbox.MainActivity') | Out-Null
    Write-Host 'Toolbox is installed and opened. Its individual approval was preserved. Use App installs in the app, or option 2 here, to try the master bypass.'
}
function Approve-Package {
    Require-Model
    $name=$PackageName
    if(!$name) {$name=Read-Host 'Package name, for example com.block.juggle'}
    if($name.Length -gt 200 -or $name -notmatch '^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$') {throw 'Invalid package name. Enter only the package ID, not an app title or URL.'}
    $old=Read-Device -Command @('shell','settings','--user',$script:currentUser,'get','system',$name)
    Write-Host ('Previous approval value: {0}' -f $old)
    Invoke-Device -Command @('shell','settings','--user',$script:currentUser,'put','system',$name,'1') | Out-Null
    if((Read-Device -Command @('shell','settings','--user',$script:currentUser,'get','system',$name)) -ne '1') {throw 'The device did not confirm the package approval.'}
    Write-Host ('Approved {0}. Retry its installation.' -f $name)
    if($old -eq 'null') {Write-Host ('Undo: adb shell settings --user {0} delete system {1}' -f $script:currentUser,$name)}
    else {Write-Host ('Previous value was {0}; restore that value if you undo this approval.' -f $old)}
}
function Run-Action {
    param([string]$ChosenAction)
    switch($ChosenAction) {
        'Install' {Install-Toolbox}
        'AllowAll' {Set-Catalog -Allow $true}
        'RestoreCatalog' {Set-Catalog -Allow $false}
        'ApprovePackage' {Approve-Package}
        'Status' {Show-Status}
        'OpenSettings' {Invoke-Device -Command @('shell','am','start','-a','android.settings.SETTINGS') | Out-Null}
    }
}

try {
    $script:adb=Find-Adb
    $script:selectedSerial=Select-Device
    Check-Model
    if($Action -ne 'Menu') {Run-Action -ChosenAction $Action; exit 0}
    while($true) {
        Write-Host "`nRC29 Toolbox - Windows helper"
        Write-Host '1  Install/open standard Toolbox'
        Write-Host '2  Allow all app installs (Google Play and APK files)'
        Write-Host '3  Restore the catalog restriction'
        Write-Host '4  Approve one package'
        Write-Host '5  Read device status'
        Write-Host '6  Open Android Settings'
        Write-Host '0  Exit'
        $choice=Read-Host 'Choose an option'
        if($choice -eq '0') {break}
        $actions=@{'1'='Install';'2'='AllowAll';'3'='RestoreCatalog';'4'='ApprovePackage';'5'='Status';'6'='OpenSettings'}
        if($actions.ContainsKey($choice)) {
            try {Run-Action -ChosenAction $actions[$choice]}
            catch {Write-Host ('Could not complete the action: {0}' -f $_.Exception.Message) -ForegroundColor Red}
        }
    }
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}
