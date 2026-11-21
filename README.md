# App Limit

A daily time limit for the Android apps you choose, and nothing else. Pick an app, set the
minutes, done. No app is limited until you limit it.

- A notification 5 minutes before the time runs out.
- When it runs out, the app is covered by a "Time limit reached" screen: one more minute (once),
  15 more minutes, ignore for today, or close.
- Counters reset at local midnight.
- Optional PIN, off by default, for anything that loosens a limit.

It has no internet permission, no analytics, no accounts and no Google dependencies. It needs
Android 12 or later.

## Battery

The app never polls. An accessibility service listens only for window changes, and only from the
apps that have a limit, so the system does not wake it while other apps are in use. While a
limited app is in front it widens the filter until the next window change, since leaving shows up
as an event from another package. Each session schedules one delayed message for the moment time
runs out, cancelled on leaving or on screen-off. There is no foreground service, no alarm, no job
and no wake lock. Today's usage is rebuilt from the history Android already keeps, once per
session.

## Permissions

- **Accessibility**: to know which app is in front and cover it when time runs out. It reads no
  screen content.
- **Usage access**: to show today's usage per app.
- **Notifications**: for the 5-minute warning.

Apps installed outside a store need "restricted settings" allowed before their accessibility
service can be turned on: App info, ⋮ menu, Allow restricted settings.

## Building

```
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Measure performance on a release build: Compose in a debug build drops frames that a release
build does not.

## Releasing

Pushing a `v*` tag builds a signed APK and attaches it to a GitHub release. The signing key comes
from the repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`;
no key material is in the repository. The workflow fails rather than publishing an APK it could
not sign, or one that requests network access.

## License

GPL-3.0-or-later.
