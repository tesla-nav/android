<img src="docs/icon.svg" alt="Tesla Nav icon" width="72" height="72" />

# Tesla Nav

Android app for in-car head units. Watches the Tesla Fleet API for the active nav destination, launches it in Waze or Google Maps.

Target hardware: model 1026_EC30W2D_EN_WTBGM, device code uis8581a2h10, UNISOC/Spreadtrum, firmware SPRD/s9863a1h10_Natv, Android 13 (API 29), build QP1A.190711.020.

## 1. Tesla Fleet API setup

Uses the official Fleet API (legacy `owner-api` is dead since mid-2026). No app server — just a static file host + the app itself.

1. **Create the app** on developer.tesla.com:
   - Allowed Origin: `https://<your-domain>` (any HTTPS host you control, e.g. GitHub Pages)
   - Allowed Redirect URI: `http://localhost:8765/callback` (loopback, RFC 8252 — captured on-device by `LoopbackRedirectServer.kt`, not a real server)
   - Save the generated `client_id` / `client_secret`

2. **Host the public key** (required for partner registration) at:
   ```
   https://<your-domain>/.well-known/appspecific/com.tesla.3p.public-key.pem
   ```
   ```bash
   openssl ecparam -name prime256v1 -genkey -noout -out private-key.pem
   openssl ec -in private-key.pem -pubout -out public-key.pem
   ```
   - GitHub Pages: add `.nojekyll` at the root, or `.well-known` 404s.
   - Never commit/share the private key — the app is read-only, doesn't need it.

3. **Set runtime config** — `client_id`/`client_secret` are never baked into the APK, only stored in `SharedPreferences` (`SettingsManager`):
   - via the app: Tesla screen → "Tesla app (developer.tesla.com)" card, or
   - via `.env` + script — copy [`.env.example`](.env.example) to `.env` (gitignored), fill in `TESLA_CLIENT_ID`/`TESLA_CLIENT_SECRET`, then run the login (step 5) which pushes everything in one shot, or
   - via adb, by hand:
     ```bash
     adb root
     adb shell run-as io.github.teslanav.app cat shared_prefs/TeslaNavSettings.xml
     # edit locally, add:
     #   <string name="tesla_client_id">...</string>
     #   <string name="tesla_client_secret">...</string>
     adb push TeslaNavSettings.xml /data/local/tmp/TeslaNavSettings.xml
     adb shell run-as io.github.teslanav.app sh -c 'cp /data/local/tmp/TeslaNavSettings.xml shared_prefs/TeslaNavSettings.xml'
     adb shell am force-stop io.github.teslanav.app
     adb shell am start -n io.github.teslanav.app/.MainActivity
     ```

4. **Partner registration** (once, per region) — without it every Fleet API call returns `412`:
   ```bash
   # partner token
   curl -X POST "https://fleet-auth.prd.vn.cloud.tesla.com/oauth2/v3/token" \
     --data-urlencode "grant_type=client_credentials" \
     --data-urlencode "client_id=$TESLA_CLIENT_ID" \
     --data-urlencode "client_secret=$TESLA_CLIENT_SECRET" \
     --data-urlencode "scope=openid vehicle_device_data vehicle_location vehicle_cmds vehicle_charging_cmds energy_device_data energy_cmds" \
     --data-urlencode "audience=https://fleet-api.prd.eu.vn.cloud.tesla.com"

   # domain registration
   curl -X POST "https://fleet-api.prd.eu.vn.cloud.tesla.com/api/1/partner_accounts" \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer $PARTNER_TOKEN" \
     --data '{"domain":"<your-domain>"}'
   ```
   - `domain` = bare host of the Allowed Origin, no scheme/port. `localhost` rejected.
   - Swap `fleet-api.prd.eu.` for `.na.`/`.ap.` per account region (`ou_code` in the token's JWT).

5. **User login** — either:
   - on-device: app → Tesla screen → "Log in with Tesla": browser → Tesla login → redirect to `localhost:8765/callback` → code exchanged automatically for `access_token`/`refresh_token`, or
   - from a PC, via [`scripts/TeslaLogin.java`](scripts/TeslaLogin.java) — no dependency beyond the JDK (already required to build this app) and `adb` on `PATH`:
     ```bash
     set -a && source .env && set +a   # needs TESLA_CLIENT_ID/TESLA_CLIENT_SECRET, see step 3
     java scripts/TeslaLogin.java
     ```
     Opens a browser to Tesla's login (with `&prompt=consent`, see gotcha below), catches the loopback callback locally, exchanges the code, and pushes `client_id`/`client_secret`/`tesla_token`/`tesla_refresh_token` into the device's SharedPreferences over adb — same effect as logging in on-device, just run from a PC with an up-to-date browser. `--no-push` prints the tokens instead of pushing them.
     - `redirect_uri` is loopback — anything listening on that port catches it, so this works from any PC with `adb` connected to the device, not just the head unit.
   - Access token: 8h, silently refreshed via `TeslaClient.refreshToken()`.
   - Refresh token: long-lived, but **rotates on every refresh** — Tesla invalidates the old one each time, so don't cache it anywhere (`.env` included); always get a fresh one through step 5 above.

### 5b. If the on-device browser is too old

Symptom: browser opens, nothing happens, `LoopbackRedirectServer` times out (`no callback received`). Cause: outdated system Chrome/WebView (seen: Chrome 87) can't render Tesla's login page. Fix: run the PC-side login from step 5 instead — it doesn't depend on the device's browser at all.

- **Gotcha**: `prompt=login` only forces re-auth (password/2FA), **not** re-consent. If the account already granted a narrower consent before (e.g. just `openid`, from an earlier test), Tesla reuses it silently — no `offline_access`/`vehicle_device_data`, no `refresh_token`, even though the app's own scopes are fine. The script always sends `prompt=consent` to force the full permissions screen and avoid this.
- **Gotcha**: `vehicle_location` (needed for `drive_state`'s GPS fields — `active_route_latitude`/`longitude` etc.) isn't granted by the `auth.tesla.com` consent screen alone, even with `prompt=consent` and the scope enabled on developer.tesla.com — the screen doesn't list scopes explicitly and silently drops it. It must be enabled per-app on the Tesla account itself, at [accounts.tesla.com/account-settings/security](https://accounts.tesla.com/account-settings/security) (Third-Party Apps section), *before* logging in. After enabling it there, redo step 5 — the resulting token's scope (check via `--no-push`, or the script's own "Granted scope" printout) should include `vehicle_location`.

## 2. Install as a system app

> **Device-specific.** Steps below are exact for the head unit above (imagebon M20 Pro / UNISOC uis8581a, see [reference](#reference)). The shape — root, remount `/product` rw, install as system app — generalizes; the toggle/code/whitelist name won't match other units.

1. **Get root**: Settings → Developer options → **Adb Root Privileges** → code **`2846`** → reboot (toggle needs it) → `adb root`
2. **Make `/product` writable**:
   ```bash
   adb remount   # check: adb shell mount | grep "overlay on /product" → should say rw
   ```
   - Redo after every reboot — but only to write again. `/product` always comes back `ro` on boot; what's already written (the overlay upperdir on `/mnt/scratch`) persists on its own. A reboot alone doesn't erase it.
3. **Install as a system app** — third-party (`/data/app`) apps are blocked from auto-start by a vendor whitelist (`PowerController.Guru`); `/product/app` bypasses it:
   ```bash
   adb uninstall io.github.teslanav.app   # only if already installed the normal way (adb install) — skip otherwise
   adb shell mkdir -p /product/app/TeslaNav
   adb push app-debug.apk /product/app/TeslaNav/TeslaNav.apk
   adb shell chmod 644 /product/app/TeslaNav/TeslaNav.apk
   adb shell chown root:root /product/app/TeslaNav/TeslaNav.apk
   adb reboot
   ```
   - **Updating an already-installed system app**: skip `adb uninstall` and `mkdir` — just re-run the `push`/`chmod`/`chown`/`reboot` steps, overwriting the APK in place. This keeps `SharedPreferences` (Tesla tokens, settings) intact, since nothing ever calls `pm uninstall`. Requires the new build to be signed with the same key as what's installed (true for two `app-debug.apk` builds off the same machine/debug keystore).

### Gotchas

- **Never delete `/product/app/TeslaNav` while the package is still registered** (still in `pm list packages`) — orphaned registration crash-loops `system_server`. Always `adb shell pm uninstall --user 0 io.github.teslanav.app` first, confirm it's gone from `pm list packages`, then delete.

### Reference

https://xdaforums.com/t/imagebon-m20-pro-uis8581a-bootloader-unlock-root-android-10-fake-android-13.4695726/
