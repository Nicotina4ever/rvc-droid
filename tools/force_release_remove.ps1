$ErrorActionPreference = 'Continue'
$p = 'K:\AI\Android\rvc-portable-android\app\build\outputs\apk\release\app-release.apk'
for ($i = 0; $i -lt 5; $i++) {
    if (Test-Path $p) {
        try {
            Remove-Item -Force $p -ErrorAction Stop
            Write-Host "iter ${i}: deleted"
            break
        } catch {
            Write-Host "iter ${i}: locked ($($_.Exception.Message))"
            Start-Sleep -Seconds 2
        }
    } else {
        Write-Host "iter ${i}: already gone"
        break
    }
}
