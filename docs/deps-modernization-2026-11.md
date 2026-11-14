# Dependency modernization — 2026-11

A sweep that brought the toolchain, Jetpack libraries and release-side
optimizations up to current stable versions, plus APK/AAB size tuning.

All work landed on `main` as a chain of small commits (one logical change each).
No release was cut; CI/test on real devices and emulators will happen later.

## Toolchain

| Component | Before | After | Notes |
|---|---|---|---|
| Android Gradle Plugin | 8.13.2 | **9.5.1** | AGP 9 ships Kotlin support built-in, so the standalone `org.jetbrains.kotlin.android` plugin is removed from `build.gradle.kts` / `libs.versions.toml`. The old `kotlinOptions { jvmTarget = "17" }` block is gone; the JVM target now follows `compileOptions`. |
| Gradle wrapper | 8.13 | **9.9.1** | Required by AGP 9.5 (min 9.6). 9.9.1 is the current stable `services.gradle.org/versions/current`. |
| Kotlin | 2.2.10 | **2.4.21** | Kotlin 2.2 fails to apply on Gradle 9.9 (`Cannot add extension with name 'kotlin'`). 2.4.21 is the current bug-fix release on the 2.4 line (support through 2027-12). The matching `kotlin-compose` compiler plugin follows the same version automatically. |
| compileSdk / targetSdk | 36 | **37** | Required by the new Compose UI 1.12 and androidx `core` 1.19+. `minSdk` stays at 24 (Play's automatic protection requirement). |

## Jetpack / Compose

| Dependency | Before | After |
|---|---|---|
| `androidx.compose:compose-bom` | 2024.09.00 | **2026.09.00** (Material3 1.4.0, UI/foundation/runtime 1.12.1) |
| `androidx.core:core-ktx` | 1.17.0 | **1.19.1** |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.10.0 | **2.11.0** |
| `androidx.activity:activity-compose` | 1.12.0 | **1.13.0** |
| `androidx.navigation:navigation-compose` | 2.8.3 | **2.10.2** |

Compose BOM 2024.09.00 → 2026.09.00 is the largest single jump; everything is
coordinated through the BOM so no explicit per-artifact Compose versions are
pinned.

## Utility libraries

| Dependency | Before | After |
|---|---|---|
| `com.google.android.gms:play-services-location` | 21.2.0 | **21.4.0** |
| `org.jetbrains.kotlinx:kotlinx-coroutines-play-services` | 1.7.3 | **1.11.0** |
| `com.google.accompanist:accompanist-permissions` | 0.34.0 | **0.37.3** |

Unchanged: `com.maifee:minidb` (0.4.0), `com.maifee.velvet:velvet` (0.1.0),
JUnit4 (4.13.2), `androidx.test.ext:junit` (1.3.0), `androidx.test.espresso:core`
(3.7.0).

## Size / release optimizations

Enabled on `release` only — `debug` is untouched to keep iteration fast.

- **R8 minification**: `isMinifyEnabled = true`.
- **Resource shrinking**: `isShrinkResources = true`.
- **ABI filters**: `arm64-v8a` + `armeabi-v7a` only. The app ships no native
  code; dependencies carry prebuilt libs for every ABI. Universal APKs now
  skip x86 / x86_64 variants. The Play App Bundle still splits per ABI
  automatically, so this is a no-op for the bundle delivery path.
- **buildConfig disabled**: `buildFeatures.buildConfig = false`. Unused by this
  app, so we skip generating `BuildConfig.java` entirely.
- **vectorDrawables.useSupportLibrary**: enabled — lets the support library
  rasterize vector drawables on older runtimes where the Android drawable
  pipeline is less complete. Safe above `minSdk 21`.
- **Packaging excludes**: drops redundant `META-INF/*` entries, stray
  `kotlin_module` files and old `kotlin/**` stdlib metadata from the final
  archive.
- **`jniLibs.useLegacyPackaging = false`**: aligns JNI libs for `android:extractNativeLibs="false"`
  (default on `minSdk 23+`), which makes native libs stream straight from the
  APK without an on-install copy.
- **`dependenciesInfo.includeInApk / includeInBundle = false`**: strips the
  generated dependency-info blob from the shipped artifact. The Play Console
  still receives the metadata through the upload pipeline.
- **Bundle splits**: `bundle { language; density; abi { enableSplit = true } }`
  — all three split dimensions on, so per-device downloads stay minimal.

## R8 keep rules

`app/proguard-rules.pro` was rewritten from scratch (previously only the AGP
template with every rule commented out):

- Source/line attributes retained so crash stack traces symbolicate against the
  mapping file.
- Data-model classes under `com.mua.prayertracker.data.entity.*` and
  `com.mua.prayertracker.domain.model.*` are fully kept — their names and
  fields are the on-disk MiniDb format and must stay stable across R8 renames.
- Manifest-referenced broadcast receiver `PrayerNotificationReceiver` is kept
  so the manifest entry resolves after shrinking.

Compose, kotlinx-coroutines and Play Services ship their own consumer rules
through their AARs, so nothing extra is needed for those.

## NDK note

No native code lives in this project, so no NDK version pin is written.
AGP 9.5's default NDK (28.2.13676358) is used implicitly only to strip debug
symbols from third-party `.so` files during the release build.

## Verification

- `./gradlew :app:assembleDebug` — **PASS** (one pre-existing deprecation
  warning on `statusBarColor` in `Theme.kt`, unchanged by this sweep).
- `./gradlew :app:minifyReleaseWithR8` — **PASS** (R8 runs clean with the new
  proguard rules and shrunk resources).
- `./gradlew :app:test` — **PASS**.
- `./gradlew :app:assembleRelease` — stops at `validateSigningRelease` because
  no local keystore is present; expected, the keystore lives outside the repo.

On-device / emulator verification is deferred to the next session.

## Commit trail

All on `main`:

```
ade8131 [toolchain]: drop kotlin-android plugin and kotlinOptions block for AGP 9
bb0530b [deps]: bump Kotlin to 2.4.21 for Gradle 9.9 / AGP 9.5 compatibility
0bea1e2 [toolchain]: bump AGP 9.5.1, Gradle 9.9.1, compileSdk/targetSdk 37
9144669 [size]: enable R8 + resource shrinking; abi filters; packaging excludes
22b1ea7 [deps]: bump play-services-location 21.4.0, coroutines 1.11.0, accompanist 0.37.3
d957331 [deps]: bump androidx core 1.19.1, lifecycle 2.11.0, activity 1.13.0, navigation 2.10.2
cfb38ac [deps]: bump compose bom to 2026.09.00
```
