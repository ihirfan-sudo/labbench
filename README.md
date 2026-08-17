# LabBench

An offline Android app for cell culture and bench work: calculators, culture and
passage tracking, protocol timers that survive a locked screen, and freezer
inventory down to the vial position.

Everything is stored locally in SQLite. No account, no network calls, no
analytics — the app declares no internet permission at all.

---

## Getting a build

You have three options, in order of effort.

### 1. GitHub Actions (no local setup)

1. Push this folder to a new GitHub repository.
2. Open the **Actions** tab. The `Build APK` workflow runs on every push.
3. When it finishes, open the run and download the `labbench-debug` artifact.
   Unzip it and you have an installable APK.

A debug APK installs fine on your own phone (enable "Install unknown apps" for
your browser or file manager). It's signed with a throwaway debug key, so it
can't be published to Play — see signing below for that.

### 2. Android Studio

Install Android Studio (Ladybug or newer), then `File > Open` this folder. It
downloads the SDK and Gradle wrapper on first sync. Plug in a phone with USB
debugging on and press Run, or use `Build > Build APK(s)`.

### 3. Command line

Requires JDK 17 and the Android SDK with `ANDROID_HOME` set.

```bash
./gradlew test           # run the calculator test suite
./gradlew assembleDebug  # app/build/outputs/apk/debug/app-debug.apk
```

> The Gradle wrapper JAR is not included here (binaries don't belong in a source
> drop). Generate it once with `gradle wrapper --gradle-version 8.10.2`, or let
> Android Studio create it when you first open the project. The CI workflow
> handles this automatically.

### Signing for release

Create a keystore once and keep it somewhere safe — lose it and you can never
update the app on Play under the same listing.

```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias labbench
base64 -w0 release.jks   # paste this into the KEYSTORE_BASE64 secret
```

Add four repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`. The workflow then produces a signed release APK.
Without them it silently skips that step and still gives you the debug build.

---

## What's built

**Calculators — 21, driven by data**
`calc/Engine.kt` defines a calculator as a list of fields plus a compute
function. `CultureCalculators.kt` and `LabCalculators.kt` are the catalog. One
generic screen renders all of them, so adding a calculator means adding one
object and nothing else. Results recompute as you type.

Input parsing accepts `0,5`, `1 000 000`, and `1e6` interchangeably — decimal
comma handling in particular is a bug that has shipped in more than one lab app.

Calculators don't just compute; they refuse impossible combinations with a
specific explanation ("stock is too dilute: you'd need 14 mL of suspension but
the vessels only hold 12 mL") and warn on out-of-range values — under 20 cells
per hemocytometer square, viability below 80%, colony counts outside 30–300,
A260 above 1.0.

**Cultures**
Passages, feeds, splits, and observations with timestamps and operator. Feed
schedules per culture drive the Today board. Confluency is projected forward
from the last measured value using the cell line's doubling time, so the list
shows an estimate now rather than whatever you last typed in. Passing a line's
maximum passage automatically creates a task to thaw a fresh vial.

**Timers**
A foreground service runs one protocol at a time with notification actions for
pause, next step, and stop. The countdown derives from a wall-clock end
timestamp stored in the database, never an in-memory counter — if Android kills
the process mid-step, the remaining time is still right when it comes back.
That matters for a 45-second heat shock. 12 built-in protocols seeded on first
launch, plus quick presets.

**Inventory**
Self-referencing storage hierarchy: freezer → rack → box → position, any depth,
custom box geometry. Boxes render as their real grid with A1 at top-left so the
screen matches the box in your hand. Reagents support multiple lots with
separate expiry dates.

**Audit trail**
Every write goes through `LabRepository`, and every write produces an
`AuditRecord` with operator, timestamp, action, and the previous value. The DAOs
are deliberately not exposed to the UI so this can't be bypassed. This is the
part that makes the data defensible to a reviewer, and it's the main thing the
iOS app it was inspired by doesn't do.

---

## Layout

```
app/src/main/java/com/labbench/
  calc/       Engine.kt + calculator catalog (pure Kotlin, fully unit-tested)
  data/       Room entities, DAOs, database, repository
  timer/      Foreground protocol timer service
  ui/         Compose screens, one package per tab
app/src/test/ Calculator and formula tests
```

The `calc` package has no Android dependencies at all, which is why the tests
run on the JVM in about a second.

---

## Not built yet

Honest list, roughly in the order I'd tackle them:

1. **Notebook UI.** Entities, DAO, and auto-recording all exist; there's no
   screen yet. Calculations and culture events are already being written to it.
2. **CSV export / import.** Schema is stable enough to serialise. Round-tripping
   through Excel and re-importing safely needs a preview-and-confirm flow.
3. **PDF reports.** Android's `PdfDocument` handles this without a library.
4. **Plate layout planner.** 6- to 96-well, color-coded conditions, PNG export.
5. **Barcode scanning** for vials — needs CameraX plus ML Kit, which adds the
   first real third-party dependency.
6. **Photo capture and annotation** for gels and microscopy.
7. **Widgets and quick settings tile** for the running timer.
8. **Sync.** Deliberately last. If you want it, a self-hosted CouchDB or a
   Supabase project keeps labs in control of their own data, which is a better
   story than iCloud for anyone in a regulated environment.
9. **Multi-user.** The `operator` field is threaded through the audit log
   already but there's no login. Institutional deployments will want it.

## On the iOS app

This is a fresh implementation. No code, text, protocol content, or assets were
taken from Cell Culture and Lab Assistant — features aren't protectable but
those things are. The seeded cell lines and protocols here are written from
standard published practice.

Verify the seeded values against your own SOPs before relying on them. Cell line
doubling times in particular vary between labs.
