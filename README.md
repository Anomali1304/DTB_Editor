# DTB Editor

A powerful, native Android application designed for advanced users to inspect, modify, flash, and optimize CPU cluster topologies, CSRAM performance controller slots, and Energy-Aware Scheduling (EAS) core capacities within MediaTek MT6789 `vendor_boot` images.

## Overview

DTB Editor provides a focused, on-device workflow to perform low-level CPU topology modifications and direct DTS editing without needing a desktop environment. Designed and verified for MediaTek MT6789 platforms, it keeps everything streamlined around two core start menu actions: **Import Vendor Boot Image** and **Vendor Boot Flasher**.

## Key Features

*   **Vendor Boot Image Workflow:** Open, parse, edit, and safely repack `vendor_boot.img` (v3 / v4) while verifying ramdisk, ramdisk table, and bootconfig SHA-256 integrity.
*   **CPU Topology & Cluster Mapping:** Visual interface to inspect core microarchitectures and configure cpufreq controller slot mappings.
*   **CSRAM Slot Frequency Ranges:** Clear indicators and frequency range mappings (`0x11bc10` : 500–2000 MHz, `0x11bd30` : 725–2200 MHz, `0x11be50` : 450–1540 MHz).
*   **EAS Capacity Auto-Tune & Stepper:** Adjust individual core `capacity-dmips-mhz` ratings with interactive steppers or auto-calculate ideal capacities based on slot frequencies.
*   **Quick Presets:** Easily apply common cluster configurations (`6+2`, `4+4`, `4+2+2`, `1+2+5`) for rapid tuning and testing.
*   **Preset-Based Export Naming:** Saved images are automatically named after your active cluster preset (e.g., `6+2.img`, `1+2+5.img`).
*   **Fast Full-Document DTS Code Editor:** Virtualized zero-lag DTS editor supporting free multi-line drag selection, native Cut/Copy/Paste handles, syntax highlighting, instant full-file search with match counter & case sensitivity, smart Enter auto-indentation, **Align & Format Syntax**, Undo/Redo, Word Wrap, and Go to Line.
*   **Built-In Vendor Boot Flasher:** Root-powered flasher with active A/B slot detection, live flash progress & logs, direct image flashing to `vendor_boot` partitions, and graceful device reboot.

## Supported Format

*   `vendor_boot.img` (v3 / v4) for MediaTek MT6789

## How to Use

1.  **Import Vendor Boot Image:** Select your `vendor_boot.img` file from device storage (or launch **Vendor Boot Flasher** directly to flash an existing image).
2.  **Edit:** Use the **CPU** tab to reconfigure cluster layouts, tune CSRAM slots, and adjust core capacities, or switch to the **DTS** tab for direct full-document code editing and search.
3.  **Preview & Verify:** Review topology changes in real time with automatic validation before applying to DTS.
4.  **Save or Flash:** Save the repacked `vendor_boot` image (named automatically after your active preset) or flash it directly using **Vendor Boot Flasher** with root access.

## Critical Notes & Warnings

*   **Backup Required:** Always maintain a functional backup of your original `vendor_boot` image before applying changes or flashing.
*   **AVB Footer:** This app handles `vendor_boot` repacking while preserving ramdisk integrity. Ensure your device has verification disabled (`avbctl disable-verification` or `vbmeta` verification disabled) if AVB footer removal is required.
*   **Stability:** Direct modification of hardware configuration files and flashing system partitions carries inherent risks. Use at your own discretion.
*   **Compatibility:** Exclusively built and verified for MediaTek MT6789 devices.

## Technical Credits

*   **Icons:** Powered by Material Symbols by Google.

---
*Built with Jetpack Compose & Kotlin.*
