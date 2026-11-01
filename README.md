# DTB Editor

A powerful, native Android application designed for advanced users to inspect, modify, and optimize CPU cluster topologies, CSRAM performance controller slots, and Energy-Aware Scheduling (EAS) core capacities within Device Tree Blobs (DTB).

## Overview

DTB Editor provides a user-friendly interface to perform low-level modifications to CPU topology without needing a desktop environment. Perfect for kernel developers, custom ROM maintainers, and power users tuning performance on MediaTek MT6789 and compatible platforms.

## Key Features

*   **Native Device Tree Editing:** Parse, inspect, and modify DTB/FDT structures directly on-device.
*   **CPU Topology & Cluster Mapping:** Visual interface to inspect core microarchitectures and configure cpufreq controller slot mappings.
*   **CSRAM Slot Frequency Ranges:** Clear indicators and frequency range mappings (`0x11bc10` : 500–2000 MHz, `0x11bd30` : 725–2200 MHz, `0x11be50` : 450–1540 MHz).
*   **EAS Capacity Auto-Tune & Stepper:** Adjust individual core `capacity-dmips-mhz` ratings with interactive steppers or auto-calculate ideal capacities based on slot frequencies.
*   **Quick Presets:** Easily apply common cluster configurations (e.g., 6+2, 4+4, 4+2+2) for rapid testing.
*   **Preset-Based Export Naming:** Saved files are automatically named after your active cluster preset (e.g., `6+2.img` or `4+2+2.dtb`).
*   **DTS Code Editor:** Full raw access to Device Tree Source (DTS) code with live syntax and chunking support.

## Supported Formats

*   `vendor_boot.img` (v3 / v4)
*   Android DT Table (`dt.img`)
*   Raw Device Tree Blobs (`.dtb`)

## How to Use

1.  **Open:** Select your `vendor_boot.img` or raw `dtb` file from your device storage.
2.  **Edit:** Use the **CPU Topology** tab to reconfigure cluster layouts, tune CSRAM slots, and adjust core capacities, or the **DTS** tab for direct code modifications.
3.  **Preview:** Review topology changes in real-time with automatic validation and error detection.
4.  **Save:** Apply your changes, save the file (named automatically after your preset), and flash it to your device.

## Critical Notes & Warnings

*   **Backup Required:** Always maintain a functional backup of your original boot/vendor_boot images before applying changes.
*   **AVB Footer:** This app handles `vendor_boot` repacking while maintaining ramdisk integrity. Ensure your device has verification disabled (`avbctl disable-verification` or similar) if AVB footer adjustments are needed.
*   **Stability:** Direct modification of hardware configuration files carries inherent risks. This tool is intended for diagnostic and advanced tuning purposes only.
*   **Compatibility:** Optimized and verified for MT6789 platforms.

## Technical Credits

*   **Icons:** Powered by Material Symbols (Rounded) by Google.

---
*Built with Jetpack Compose & Kotlin.*
