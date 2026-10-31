# DTB Editor

A powerful, native Android application designed for advanced users to inspect, modify, and optimize CPU cluster topologies within Device Tree Blobs (DTB).

## Overview

DTB Editor provides a user-friendly interface to perform low-level modifications to CPU topology without needing a desktop environment. Perfect for kernel developers, custom ROM maintainers, and power users tuning performance.

## Key Features

*   **Native Device Tree Editing:** Parse, inspect, and modify DTB/FDT structures directly on-device.
*   **CPU Topology Management:** Visual interface to re-assign CPU cores to clusters and configure cpufreq controller slot mappings.
*   **Quick Presets:** Easily apply common cluster configurations (e.g., 4+4, 4+2+2) for rapid testing.
*   **DTS Code Editor:** Full raw access to Device Tree Source (DTS) code for granular control.
*   **Predictive Back Navigation:** Enhanced back-navigation handling with confirmation dialogs to prevent accidental data loss.

## Supported Formats

*   `vendor_boot.img`
*   DT Table (`dt.img`)
*   Raw Device Tree Blobs (`.dtb`)

## How to Use

1.  **Open:** Select your `vendor_boot.img` or raw `dtb` file from your device storage.
2.  **Edit:** Use the **CPU** tab to reconfigure cluster assignments or the **DTS** tab for direct code modifications.
3.  **Preview:** Review topology changes in real-time with automatic validation and error detection.
4.  **Save:** Apply your changes, save the file, and flash it to your device.

## Critical Notes & Warnings

*   **Backup Required:** Always maintain a functional backup of your original boot/vendor_boot images before applying changes.
*   **AVB Footer:** This app removes the AVB (Android Verified Boot) footer. Ensure your device has verification disabled (`avbctl disable-verification` or similar) to prevent boot loops.
*   **Stability:** Direct modification of hardware configuration files carries inherent risks. This tool is intended for diagnostic and advanced tuning purposes only.
*   **Compatibility:** Tested primarily on MT6789 platforms. Use with caution on other architectures.

## Technical Credits

*   **Icons:** Powered by Material Symbols (Rounded) by Google.

---
*Built with Jetpack Compose & Kotlin.*
