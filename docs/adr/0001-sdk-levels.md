# 0001 — SDK levels, JVM target, and build toolchain

Status: **Accepted** · 2026-07-26

## Context

Company-owned Android fleet in India, enrolled as fully managed (Device Owner) devices. We need SDK
levels that (a) cover the realistic fleet, (b) keep the security primitives we depend on, and (c) are
buildable with a toolchain that actually exists on the build machine today.

## Options considered

- `minSdk 24/26` — wider device coverage, but pre-29 lacks the scoped-storage and background-location
  model the rest of the design assumes, and adds fallback paths we would never exercise on a
  purchased-new fleet.
- `minSdk 29` — Android 10. Scoped storage, the `ACCESS_BACKGROUND_LOCATION` split, and native
  `java.time` are all present. Devices are company-purchased, so old hardware is a procurement
  decision, not a constraint we inherit.
- `compileSdk 37` — API 37 platform exists in the SDK, but AGP 9.3.1 is validated against 36 and
  moving both at once mixes two risks.

## Decision

- **`minSdk = 29`, `targetSdk = 36`, `compileSdk = 36`**
- **JVM target 17**, `sourceCompatibility`/`targetCompatibility` 17
- **Gradle 9.6.1, AGP 9.3.1, Kotlin 2.4.10, KSP 2.3.10**
- All versions pinned in `gradle/libs.versions.toml`. No inline version strings.

**AGP 9 has built-in Kotlin support.** The `org.jetbrains.kotlin.android` plugin must NOT be applied —
AGP 9.x fails the build with *"The 'org.jetbrains.kotlin.android' plugin is no longer required for
Kotlin support since AGP 9.0."* Only the Compose compiler plugin
(`org.jetbrains.kotlin.plugin.compose`) is applied separately. The `kotlin { compilerOptions { } }`
extension still exists and is `org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension`.

## Consequences

- `java.time` is available natively; no core library desugaring needed. Detekt forbids
  `java.util.Date`.
- Devices below Android 10 are out of scope. This must be stated in the procurement spec.
- API 37 is deliberately left for a follow-up bump once AGP support is confirmed.
- Every platform API used elsewhere must record its introduction API level; anything above 29 needs an
  explicit, tested fallback.

## Sources

- Verified empirically on this machine, 2026-07-26: minimal AGP 9.3.1 + Kotlin 2.4.10 + KSP 2.3.10 +
  Room 2.8.4 + Hilt 2.60.1 + Compose BOM 2026.06.01 + SQLCipher 4.17.0 project produced
  `app-debug.apk`, `BUILD SUCCESSFUL`.
- Versions read from Maven metadata (`dl.google.com/dl/android/maven2`, `repo1.maven.org`), not from
  memory.
