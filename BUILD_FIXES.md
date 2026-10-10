# Build fixes applied

## 1. The reported error — Incompatible Gradle JVM version

`Gradle 8.11 is incompatible with the Gradle JVM version 25`

**Root cause:** Android Studio was running Gradle on JDK 25. Gradle 8.11 accepts
Java 8–23. Java 25 needs Gradle 9.1+, which AGP 8.7.3 does not support — so the
fix is to lower the JDK, not raise Gradle.

**What you must do in the IDE (this cannot be fixed from files alone):**

Settings -> Build, Execution, Deployment -> Build Tools -> Gradle
-> **Gradle JDK** -> `jbr-21`
(If absent: "Download JDK…" -> version 21, vendor JetBrains Runtime.)
Then File -> Sync Project with Gradle Files.

**What was changed in the project:**
- `.idea/gradle.xml` — `gradleJvm` was `#GRADLE_LOCAL_JAVA_HOME` (which resolved
  to your system JDK 25); now pinned to `jbr-21`.
- `gradle/wrapper/gradle-wrapper.properties` — bumped 8.11 -> 8.11.1 and
  rewrote the header comments, which previously claimed the file was on Gradle
  8.14 with JDK 25 when the `distributionUrl` actually said 8.11.
- `gradle.properties` — added, with a commented-out `org.gradle.java.home` if you
  prefer pinning the JDK per-project instead of per-IDE.

## 2. Errors you would have hit on the very next sync

- **`app/build.gradle` applied no plugins.** It had the root project's block
  copy-pasted in, `version '8.7.3' apply false` and all. With `apply false` the
  Android plugin never loads, so the `android { }` block below fails with
  "Could not find method android()". Now declares the ids with no version and no
  `apply false`.
- **`gradle.properties` did not exist.** AGP hard-fails without
  `android.useAndroidX=true`, and every AndroidX dependency in this project
  needs it. Added, along with `nonTransitiveRClass` and daemon memory.
- **`compileSdk 34` with AGP 8.7.3.** Raised to 35 (your own inline comment
  flagged this). `targetSdk` left at 34 so no behaviour changes are pulled in.
- **`app/proguard-rules.pro` was missing** but referenced by the `release`
  buildType. Added — including keep rules for the Firestore model classes, which
  would otherwise deserialize to all-null fields in a minified release build.
- **Manifest referenced four resources that did not exist:**
  `@xml/data_extraction_rules`, `@xml/backup_rules`, `@mipmap/ic_launcher`,
  `@mipmap/ic_launcher_round`. All added (adaptive icon plus an API 24–25
  fallback, backup rules that exclude the saved auth session).

## 3. Still on you

`app/google-services.json` is absent and cannot be generated — see
`app/google-services.json.TEMPLATE`. The build will fail until you download it
from the Firebase console. Register **both** `com.dormmate` and
`com.dormmate.debug`, since the debug buildType applies an `applicationIdSuffix`.
