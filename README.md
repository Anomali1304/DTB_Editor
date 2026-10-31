# DTB Editor

Edit the DTB in vendor_boot.img or a standalone dtb, mainly CPU cluster topology.

## Supported Inputs and Outputs
- vendor_boot.img in -> vendor_boot.img out
- DT table in -> DT table out
- raw dtb in -> raw dtb out

## How to Use
1. Open a file.
2. Change topology on the CPU tab or edit text on the DTS tab.
3. Save the file.
4. Flash the modified file to the device.

## Notes and Limits
- AVB footer is removed and not re-signed, so verification must already be disabled.
- Ramdisk is copied byte for byte.
- Always keep a backup of the stock vendor_boot.
- Frequency tables live in firmware, so a valid dtb is not a guarantee of a stable device.
- Tested only on MT6789 / Helio G99.

## How to Build
1. Ensure Android SDK and JDK are configured.
2. Run: `gradle :app:assembleDebug`

## Credits
Launcher icon uses custom vector art.
