package com.dtb.editor.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dtb.editor.fdt.parser.VendorBoot
import com.dtb.editor.root.FlashSlotInfo
import com.dtb.editor.viewmodel.DtbViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SelectedImageInfo(
    val name: String,
    val sizeBytes: Long,
    val bytes: ByteArray,
    val isStandardVendorBoot: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorBootFlasherScreen(
    viewModel: DtbViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isPermissionChecked by remember { mutableStateOf(false) }
    var isPermissionGranted by remember { mutableStateOf(false) }
    var showPermissionDeniedDialog by remember { mutableStateOf(false) }

    var slotInfo by remember { mutableStateOf<FlashSlotInfo?>(null) }
    var selectedTarget by remember { mutableStateOf("active") }

    var selectedImage by remember { mutableStateOf<SelectedImageInfo?>(null) }
    var isFlashing by remember { mutableStateOf(false) }
    var flashSuccess by remember { mutableStateOf<Boolean?>(null) }
    val stepLogs = remember { mutableStateListOf<String>() }

    var showConfirmFlashDialog by remember { mutableStateOf(false) }
    var showConfirmRebootDialog by remember { mutableStateOf(false) }

    val logListState = rememberLazyListState()

    // Auto-scroll operation steps
    LaunchedEffect(stepLogs.size) {
        if (stepLogs.isNotEmpty()) {
            logListState.animateScrollToItem(stepLogs.size - 1)
        }
    }

    // Check Superuser Permission & Detect Slot on launch
    LaunchedEffect(Unit) {
        val granted = viewModel.checkRootAccess()
        isPermissionGranted = granted
        isPermissionChecked = true
        if (granted) {
            slotInfo = viewModel.detectSlotInfo()
            stepLogs.add("Root access verified")
            stepLogs.add("Device model: MediaTek MT6789")
            stepLogs.add("Active boot slot: ${slotInfo?.activeSlotSuffix?.ifEmpty { "N/A" }}")
        } else {
            showPermissionDeniedDialog = true
        }
    }

    // File picker to import vendor_boot.img from storage
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    var fileName = "vendor_boot.img"
                    var fileSize = 0L

                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (nameIdx != -1) fileName = cursor.getString(nameIdx) ?: fileName
                            if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
                        }
                    }

                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }

                    if (bytes != null && bytes.isNotEmpty()) {
                        val isVb = VendorBoot.isVendorBoot(bytes)
                        selectedImage = SelectedImageInfo(
                            name = fileName,
                            sizeBytes = if (fileSize > 0) fileSize else bytes.size.toLong(),
                            bytes = bytes,
                            isStandardVendorBoot = isVb
                        )
                        stepLogs.add("Loaded image: $fileName (${formatFileSize(bytes.size.toLong())})")
                        if (isVb) {
                            stepLogs.add("Image header verified: Android vendor_boot format")
                        } else {
                            stepLogs.add("Notice: Raw partition image loaded")
                        }
                    }
                } catch (e: Exception) {
                    stepLogs.add("Error loading image: ${e.message}")
                }
            }
        }
    }

    // Root Access Dialog - Only Close button
    if (showPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = {
                showPermissionDeniedDialog = false
                onClose()
            },
            icon = {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    "Root Access Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Vendor Boot Flasher requires root access to read and write to device partitions.\n\nPlease grant root access in your root manager and reopen the flasher.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDeniedDialog = false
                        onClose()
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }

    // Confirmation Flash Dialog
    if (showConfirmFlashDialog && selectedImage != null) {
        val targetName = if (selectedTarget == "active") (slotInfo?.defaultPartitionName ?: "vendor_boot") else selectedTarget
        AlertDialog(
            onDismissRequest = { showConfirmFlashDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    "Confirm Flashing",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Are you sure you want to flash '${selectedImage!!.name}' to partition [$targetName]?\n\nThis will directly overwrite the target partition on your device.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmFlashDialog = false
                        isFlashing = true
                        flashSuccess = null
                        val imgBytes = selectedImage!!.bytes
                        stepLogs.add("Starting flash operation to $targetName...")

                        coroutineScope.launch {
                            val result = viewModel.rootFlashManager.flashVendorBootImageWithLogs(
                                context = context,
                                imageBytes = imgBytes,
                                targetPartitionName = targetName,
                                onLog = { line ->
                                    stepLogs.add(line)
                                }
                            )

                            isFlashing = false
                            if (result.isSuccess) {
                                flashSuccess = true
                                stepLogs.add("Flashing completed successfully!")
                            } else {
                                flashSuccess = false
                                stepLogs.add("Flashing failed: ${result.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Confirm Flash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmFlashDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation Reboot Dialog
    if (showConfirmRebootDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmRebootDialog = false },
            icon = {
                Icon(
                    Icons.Default.RestartAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    "Confirm Reboot",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Are you sure you want to reboot the device now to apply changes?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmRebootDialog = false
                        coroutineScope.launch {
                            viewModel.rebootDevice()
                        }
                    }
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reboot Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmRebootDialog = false }) {
                    Text("Later")
                }
            }
        )
    }

    val activeSlotLabel = remember(slotInfo) {
        val suffix = slotInfo?.activeSlotSuffix ?: ""
        if (suffix.equals("_a", ignoreCase = true)) "Slot A (_a)"
        else if (suffix.equals("_b", ignoreCase = true)) "Slot B (_b)"
        else if (suffix.isNotEmpty()) "Slot ($suffix)"
        else "Partition Non-A/B (vendor_boot)"
    }
    val activeDefaultPart = slotInfo?.defaultPartitionName ?: "vendor_boot"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Vendor Boot Flasher",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Status Card (Material You)
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = when (flashSuccess) {
                        true -> MaterialTheme.colorScheme.primaryContainer
                        false -> MaterialTheme.colorScheme.errorContainer
                        null -> if (isFlashing) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = when (flashSuccess) {
                                true -> Icons.Default.CheckCircle
                                false -> Icons.Default.Error
                                null -> if (isFlashing) Icons.Default.FlashOn else Icons.Default.Verified
                            },
                            contentDescription = null,
                            tint = when (flashSuccess) {
                                true -> MaterialTheme.colorScheme.primary
                                false -> MaterialTheme.colorScheme.error
                                null -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = when (flashSuccess) {
                                    true -> "Flashing Completed Successfully"
                                    false -> "Flashing Failed"
                                    null -> if (isFlashing) "Flashing in Progress..." else "Flasher Ready"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Device: MediaTek MT6789 • Active: $activeSlotLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isFlashing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    }
                }
            }

            // Image Selection Card (Material You)
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Image Source",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (selectedImage != null) {
                            TextButton(
                                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                enabled = !isFlashing
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Change")
                            }
                        }
                    }

                    if (selectedImage == null) {
                        FilledTonalButton(
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isFlashing
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select vendor_boot.img from Storage")
                        }
                        Text(
                            "Choose an Android vendor_boot image from your device storage to flash.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val img = selectedImage!!
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        img.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${formatFileSize(img.sizeBytes)} • ${if (img.isStandardVendorBoot) "Standard vendor_boot" else "Raw partition image"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Target Partition Slot Selection Card
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Target Partition Slot",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isFlashing) { selectedTarget = "active" },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedTarget == "active"),
                                onClick = { selectedTarget = "active" },
                                enabled = !isFlashing
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Active Slot ($activeDefaultPart)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Recommended - Flash to the currently active boot slot",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isFlashing) { selectedTarget = "vendor_boot_a" },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedTarget == "vendor_boot_a"),
                                onClick = { selectedTarget = "vendor_boot_a" },
                                enabled = !isFlashing
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Slot A (vendor_boot_a)", style = MaterialTheme.typography.bodyMedium)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isFlashing) { selectedTarget = "vendor_boot_b" },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedTarget == "vendor_boot_b"),
                                onClick = { selectedTarget = "vendor_boot_b" },
                                enabled = !isFlashing
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Slot B (vendor_boot_b)", style = MaterialTheme.typography.bodyMedium)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isFlashing) { selectedTarget = "both" },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedTarget == "both"),
                                onClick = { selectedTarget = "both" },
                                enabled = !isFlashing
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Both Slots (vendor_boot_a & vendor_boot_b)", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Operation Steps & Progress (Material You Card)
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Operation Steps",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${stepLogs.size} events",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isFlashing) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        state = logListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(stepLogs) { line ->
                            val isSuccess = line.startsWith("[+]") || line.contains("success", ignoreCase = true)
                            val isError = line.startsWith("[-]") || line.contains("failed", ignoreCase = true) || line.contains("error", ignoreCase = true)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = when {
                                        isSuccess -> Icons.Default.CheckCircle
                                        isError -> Icons.Default.Cancel
                                        else -> Icons.Default.Info
                                    },
                                    contentDescription = null,
                                    tint = when {
                                        isSuccess -> Color(0xFF2E7D32)
                                        isError -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = line.removePrefix("[+]").removePrefix("[-]").removePrefix("[*]").trim(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        isError -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { showConfirmFlashDialog = true },
                    enabled = isPermissionGranted && selectedImage != null && !isFlashing,
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Flash Image", style = MaterialTheme.typography.titleMedium)
                }

                if (flashSuccess == true) {
                    Button(
                        onClick = { showConfirmRebootDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reboot Device", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val mb = bytes.toDouble() / (1024 * 1024)
    return String.format("%.1f MB", mb)
}
