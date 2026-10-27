# Third-party components and assets

The MIT license in this repository covers returnABLEs application source and
original artwork. It does not relicense vendor libraries, SDKs, machine-learning
models, or Android platform components. Their own licenses and terms continue to
apply, including any notices packaged with the resolved dependencies.

## Application dependencies

| Component | Use | License or terms |
| --- | --- | --- |
| Kotlin standard library and compiler tooling | Kotlin/JVM implementation | [Apache License 2.0](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt); additional upstream notices are described in [Kotlin's license directory](https://github.com/JetBrains/kotlin/tree/master/license). |
| AndroidX, including Compose UI/Material 3, Activity, Lifecycle, WorkManager and Material icons | Native interface, lifecycle, file sharing, background reminders | [AndroidX Apache License 2.0](https://github.com/androidx/androidx/blob/androidx-main/LICENSE.txt). |
| Google ML Kit Text Recognition (`com.google.mlkit:text-recognition:16.0.1`) and its bundled Latin-script model | On-device text extraction from receipt images | [ML Kit Terms & Privacy](https://developers.google.com/ml-kit/terms), incorporating [Google APIs Terms of Service](https://developers.google.com/terms). These SDK/model terms are separate from this app's MIT license. |

The [official Android integration guide](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
describes `com.google.mlkit:text-recognition` as the bundled installation option,
with the model linked into the app at build time. The app uses the Latin-script
recognizer. It does not bundle the separate Chinese, Devanagari, Japanese or Korean
recognition libraries, and does not claim a measured OCR accuracy rate.

## ML Kit privacy context

Google states that ML Kit processes input and output on device. Its terms also
describe SDK performance/utilization metrics and possible contact with Google
servers for updates and compatibility information. See the
[official privacy disclosure](https://developers.google.com/ml-kit/terms#privacy).

returnABLEs does not add an analytics service. Its merged application manifest
removes Internet permission, and receipt processing uses the bundled model. These
application choices do not change the upstream SDK/model terms or represent an
audit of every dependency's internal behavior.

## Original assets

The receipt launcher vector in `app/src/main/res/drawable/ic_receipt.xml`, its
adaptive-icon composition, the returnABLEs wordmark styling, and the application
layout/color treatment were created for this project. They are covered by the
application's MIT license. The icon is a simple receipt drawing, not a downloaded
photograph, external illustration or pretrained model output.

Interface glyphs supplied by the AndroidX Material icons library retain that
library's license. Typography uses Android's system font selection; this repository
does not distribute an external font file.

## Build and test components

Gradle, the Android Gradle Plugin, Android SDK/build tools, JUnit, AndroidX test
libraries and the JVM test-only `org.json` dependency are development dependencies.
They retain their upstream licensing and are not covered by the application MIT
license. `org.json` is used for host-side unit tests; Android supplies `org.json`
for application runtime code. The source dependency declarations are in
`app/build.gradle.kts`, `build.gradle.kts`, and the Gradle wrapper configuration.
