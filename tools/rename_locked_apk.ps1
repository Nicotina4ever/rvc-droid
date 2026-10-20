$ErrorActionPreference = 'Continue'
$src = 'K:\AI\Android\rvc-portable-android\app\build\outputs\apk\release\app-release.apk'
$dst = 'K:\AI\Android\rvc-portable-android\app\build\outputs\apk\release\app-release.old.apk'
if (Test-Path $src) {
    for ($i = 0; $i -lt 5; $i++) {
        try {
            Move-Item -Force $src $dst -ErrorAction Stop
            Write-Host "iter ${i}: renamed"
            exit 0
        } catch {
            Write-Host "iter ${i}: locked ($($_.Exception.Message))"
            Start-Sleep -Seconds 2
        }
    }
    Write-Host "FAILED to rename"
    exit 1
}
Write-Host "source not present"
exit 0
