# DTB Studio

[![Build Status](https://github.com/Anomali1304/DTB-Editor/actions/workflows/build-release.yml/badge.svg)](https://github.com/Anomali1304/DTB-Editor/actions/workflows/build-release.yml)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-purple.svg)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20%7C%20Material%203-blue.svg)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-orange.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg)](LICENSE)

**DTB Studio** is a professional, standalone Android engineering utility designed to inspect, decompile, edit, compile, and compare Linux Device Tree files (`.dtb`, `.dtbo`, and `.dts`). 

Engineered specifically for Android kernel developers, ROM builders, hardware engineers, and low-level system enthusiasts, DTB Studio runs **entirely offline** on physical devices and emulators—**without requiring Termux, root access, or an active internet connection**.

---

## 📸 Key Features

### 1. Robust Offline DTC Backend
- Compliant with **Devicetree Specification v0.4** Flattened Device Tree (FDT) binary standard (`0xd00dfeed`).
- Full roundtrip decompilation (`DTB → DTS`) and compilation (`DTS → DTB`).
- Native handling of 4-byte struct alignments, memory reservation blocks (`/memreserve/`), and deduplicated strings blocks.
- Real compiler console output displaying parsed node counts, property counts, warnings, syntax errors, and generated binary size.

### 2. Modern Android Storage Access Framework (SAF)
- Seamlessly import `.dtb`, `.dtbo`, and `.dts` files from external storage, cloud providers, or download directories.
- Automatic binary magic header detection.
- Safe export protecting original source files from unintended overwrite.

### 3. Hierarchical DTB Explorer
- Expandable tree viewer with node path hierarchy and category badges:
  - `CPU`: Core nodes (`/cpus`, `cpu@*`) and cluster maps
  - `THERMAL`: Thermal zones, trip points, and cooling maps
  - `REGULATOR`: Voltage and current supply nodes
  - `CLOCK`: Hardware clock tree and fixed frequency generators
  - `MEMORY`: Physical RAM mappings and reserved memory
  - `DVFS`: Operating performance points (`operating-points-v2`)
  - `INTR`: Interrupt controllers and routing
- Real-time search and filter capabilities.
- Deep Property Inspector displaying formatted values, raw hex dumps, ASCII strings, and phandle cross-references.

### 4. Full-Featured DTS Code Editor
- Gutter with synchronized line numbers.
- Integrated search and find-and-replace bar.
- Multi-level **Undo** and **Redo** history stacks.
- **Restore Original** function to revert workspace changes at any time.
- Single-tap **Compile DTB** button with instant compiler status bottom sheet.

### 5. CPU Cluster & Topology Manager
- Visual CPU core layout with physical hardware addresses (`reg`) and phandles.
- Preset cluster arrangements for modern multi-core SoCs:
  - **4 + 4** (Dual-cluster / big.LITTLE)
  - **4 + 2 + 2** (Tri-cluster)
  - **2 + 3 + 3** (Heterogeneous computing)
  - **4 + 3 + 1** (Prime core configuration)
- **Hardware Safety Guarantee:** Explicit separation between `/cpus/cpu-map` (kernel scheduler affinity) and hardware CPUFreq / OPP DVFS domains. Never invents synthetic frequencies, register offsets, or voltages.

### 6. Structured Diff & Comparison Inspector
- Comprehensive comparison between original imported files and modified workspace DTS.
- Quantitative badges tracking added (`+`), removed (`-`), and modified (`~`) items.
- Line-by-line diff and categorized property change cards.

### 7. Android 13+ Themed Icons & Modern UI
- Developer-oriented dark theme with terminal cyan and emerald accents.
- Adaptive icon support across all screen densities.
- Dedicated monochrome vector asset for **Android 13+ Material You Themed App Icons**.

---

## 🏗️ Architecture & Technology Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin 2.2 |
| **Architecture** | Model-View-ViewModel (MVVM) + Clean Architecture |
| **UI Framework** | Jetpack Compose with Material Design 3 (M3) |
| **Concurrency** | Kotlin Coroutines & `StateFlow` / `collectAsState` |
| **Storage** | Android Storage Access Framework (SAF) |
| **FDT Engine** | Custom high-performance binary FDT parser, tokenizer, and compiler |
| **Package ID** | `com.dtb.editor` |
| **Target SDK** | Android 15 (API 36, Min SDK 24) |

---

## 🚀 GitHub Actions CI/CD Pipeline

The project includes an enterprise-grade automated CI/CD workflow located at [`.github/workflows/build-release.yml`](.github/workflows/build-release.yml).

### Automated Pipeline Capabilities:
1. **Continuous Integration on Push**:
   - Triggers on every push to `main` or `master` and on pull requests.
   - Executes local unit and Robolectric tests (`testDebugUnitTest`).
   - Compiles an optimized, minified Release APK (`DTBStudio-release.apk`) using R8 code shrinking and resource stripping.
   - Computes SHA256 cryptographic checksums for release verification.
   - Uploads the Release APK as a downloadable artifact in the GitHub Actions tab.

2. **Automated GitHub Releases on Version Tags**:
   - Creating and pushing a Git tag (e.g. `git tag v1.0.0 && git push origin v1.0.0`) automatically publishes a new GitHub Release with generated release notes and the attached Release APK.

3. **Keystore Signing Configuration**:
   - **Out-of-the-Box Fallback**: The workflow includes an intelligent signing fallback that signs release binaries automatically even before custom repository secrets are configured.
   - **Custom Production Signing (Optional)**: To sign with your own private release key:
     1. Convert your `.jks` keystore to Base64:
        ```bash
        base64 -w 0 my-release-key.jks
        ```
     2. In your GitHub repository, navigate to **Settings** → **Secrets and variables** → **Actions**.
     3. Add the following repository secrets:
        - `RELEASE_KEYSTORE_BASE64`: The Base64 string of your keystore.
        - `RELEASE_STORE_PASSWORD`: Keystore password.
        - `RELEASE_KEY_PASSWORD`: Key alias password.

---

## 🛠️ Local Development & Build

### Prerequisites
- JDK 17 or JDK 21
- Android Studio Ladybug (or newer) / Android SDK Command-line Tools

### Build Commands
Clone the repository and run the Gradle wrapper:

```bash
# Clone the repository
git clone https://github.com/Anomali1304/DTB-Editor.git
cd DTB-Editor

# Run unit tests
./gradlew testDebugUnitTest

# Build Debug APK
./gradlew assembleDebug

# Build Optimized Release APK (R8 minified)
./gradlew assembleRelease
```

Generated APK binaries are placed in:
```
app/build/outputs/apk/release/app-release.apk
app/build/outputs/apk/debug/app-debug.apk
```

---

## 🛡️ Safety & Integrity Guidelines

When modifying Device Tree Blobs:
- Always maintain untouched backups of original vendor device tree binaries.
- Validate DTS syntax prior to flashing or packing into boot images.
- A successful compilation does not guarantee that custom hardware definitions are boot-safe on your specific device. Always test modifications via transient boot (`fastboot boot boot.img`) before writing to recovery or boot partitions.

---

## 📄 License

```
Copyright (C) 2026 DTB Studio Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
