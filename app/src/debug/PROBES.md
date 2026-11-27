# Debug sleep and owner probes

All commands below are prospective. Set `$serial` to an explicitly approved target.
Use PowerShell on the host. Device installs and setting changes on the C69 require
Aarin's approval at the time of each operation. Leave the physical owner probe unrun.

## S4 · Generated sleep picture

Generate the sleep picture through Ink Home dev's ordinary settings first. Keep
the device awake and unlocked during setup. Check the dream shell supports
`start-dreaming` and `stop-dreaming` with `adb -s $serial shell cmd dreams help`.
Capture the target user and original setting before changing anything:

```powershell
$userId = (adb -s $serial shell am get-current-user).Trim()
if ($LASTEXITCODE -ne 0 -or $userId -notmatch '^\d+$') { throw 'User lookup failed' }
$key = 'screensaver_components'
$settings = adb -s $serial shell settings --user $userId list secure
if ($LASTEXITCODE -ne 0 -or !$settings) { throw 'Settings snapshot failed' }
$saved = @($settings | Where-Object { $_.StartsWith("$key=") })
if ($saved.Count -gt 1) { throw 'Ambiguous setting' }
$original = if ($saved.Count) { $saved[0].Substring($key.Length + 1) }
if ($saved.Count -and $original -notmatch '^[A-Za-z0-9_.$/,]+$') { throw 'Restore value requires manual quoting' }
$saved | Out-Host
$component = 'dev.inkhome.dev/dev.inkhome.SleepProbeDream'
adb -s $serial shell settings --user $userId put secure $key $component
adb -s $serial shell cmd dreams start-dreaming
```

Record whether the generated image appears and whether ordinary Boox standby
also displays it. A successful Android dream preview alone cannot establish the
standby result. On failure or completion, use the same PowerShell session:

```powershell
adb -s $serial shell cmd dreams stop-dreaming
if ($saved.Count) {
    adb -s $serial shell settings --user $userId put secure $key $original
} else {
    adb -s $serial shell settings --user $userId delete secure $key
}
$settings = adb -s $serial shell settings --user $userId list secure
if ($LASTEXITCODE -ne 0 -or !$settings) { throw 'Restore verification failed' }
$after = @($settings | Where-Object { $_.StartsWith("$key=") })
if (($after -join "`n") -cne ($saved -join "`n")) { throw 'Restore mismatch' }
```

The harness does not change dream activation, charging, ambient display, doze,
or OEM settings. If the OEM ignores the dream, record that outcome.

## S5 · Device owner

Use only a separate, disposable Android emulator with a verified cold snapshot
from before provisioning. Leave the layout emulator alone. Use a fresh user 0
with no owner, a visible status bar and an installed, visible disposable fixture
named `dev.inkhome.probefixture` if testing app hiding. Install a test-only debug
APK built with `-Pandroid.injected.testOnly=true` using `adb -s $serial install -t PATH_TO_APK`.
Verify `TEST_ONLY` in the package flags before provisioning. Recovery is discard
of the clone or restoration of its offline snapshot if DPM removal fails.

```powershell
$admin = 'dev.inkhome.dev/dev.inkhome.lab.OwnerProbe'
adb -s $serial shell dpm set-device-owner --user 0 $admin
```

Attach the Android Studio Java debugger to the running dev launcher and pause at
a breakpoint in an instance method of MainActivity. Use Evaluate Expression,
resuming the UI between observations. These Java calls are debugger-only operations.

| Test | Evaluate | Restore |
| --- | --- | --- |
| Status bar and shade | `dev.inkhome.lab.OwnerProbe.statusBar(this, true)` | `dev.inkhome.lab.OwnerProbe.statusBar(this, false)` |
| Lock-task policy | Record `dev.inkhome.lab.OwnerProbe.lockFeatures(this)`, then call `dev.inkhome.lab.OwnerProbe.lockFeatures(this, 0)` | Pass the recorded integer to the same setter, then verify with the getter |
| Hide the visible test fixture | `dev.inkhome.lab.OwnerProbe.hideFixture(this, true)` | `dev.inkhome.lab.OwnerProbe.hideFixture(this, false)` |

Check the boolean result and visible behavior for each operation. The lock-task
test only checks configuration readback. It does not start kiosk mode. Silent
self-update is outside this harness. An emulator cannot verify Onyx system UI.

```powershell
adb -s $serial shell dpm remove-active-admin --user 0 $admin
adb -s $serial shell dumpsys device_policy
```

Check the dev component is absent from both active admins and owners, and verify
shade and fixture visibility after restoration. Discard the dedicated clone after
the test. No physical owner removal guarantee has been established.
