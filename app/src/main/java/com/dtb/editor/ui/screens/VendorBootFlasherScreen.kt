package com.dtb.editor.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
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
    var isLoadingImage by remember { mutableStateOf(false) }
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

    // File picker to import vendor_boot.img from storage (instant header check without RAM spike)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isLoadingImage = true
                try {
                    val info = withContext(Dispatchers.IO) {
                        var fileName = "vendor_boot.img"
                        var fileSize = 0L

                        // Safely query display name and size on IO dispatcher
                        try {
                            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                                    if (nameIdx != -1) fileName = cursor.getString(nameIdx) ?: fileName
                                    if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
                                }
                            }
                        } catch (_: Exception) {}

                        // Read ONLY the header (first 64 bytes) to instantly verify format without loading 64-128MB into RAM!
                        var isVb = false
                        try {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                val headerBuffer = ByteArray(64)
                                val readCount = stream.read(headerBuffer)
                                if (readCount >= 8) {
                                    isVb = VendorBoot.isVendorBoot(headerBuffer)
                                }
                            }
                        } catch (_: Exception) {}

                        if (fileSize <= 0) {
                            try {
                                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                                    fileSize = pfd.statSize
                                }
                            } catch (_: Exception) {}
                        }

                        SelectedImageInfo(
                            uri = uri,
                            name = fileName,
                            sizeBytes = fileSize,
                            isStandardVendorBoot = isVb
                        )
                    }

                    selectedImage = info
                    stepLogs.add("Selected: ${info.name} (${formatFileSize(info.sizeBytes)})")
                    if (info.isStandardVendorBoot) {
                        stepLogs.add("Header verified: Android vendor_boot format")
                    } else {
                        stepLogs.add("Notice: Raw partition image loaded")
                    }
                } catch (e: Exception) {
                    stepLogs.add("Error inspecting image: ${e.message}")
                } finally {
                    isLoadingImage = false
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
                        val imgUri = selectedImage!!.uri
                        stepLogs.add("Starting flash operation to $targetName...")

                        coroutineScope.launch {
                            val result = viewModel.rootFlashManager.flashVendorBootFromUriWithLogs(
                                context = context,
                                imageUri = imgUri,
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
        var screenEntered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            screenEntered = true
        }

        AnimatedVisibility(
            visible = screenEntered,
            enter = fadeIn(
                animationSpec = tween(durationMillis = 320, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f))
            ) + slideInVertically(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                initialOffsetY = { 40 }
            ) + scaleIn(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                initialScale = 0.96f
            ),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Status Card (Material You)
                val targetHeaderBg = when (flashSuccess) {
                    true -> MaterialTheme.colorScheme.primaryContainer
                    false -> MaterialTheme.colorScheme.errorContainer
                    null -> if (isFlashing) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                }
                val animatedHeaderBg by animateColorAsState(
                    targetValue = targetHeaderBg,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
                    label = "headerBgAnim"
                )

                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = animatedHeaderBg
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioNoBouncy
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AnimatedContent(
                            targetState = Triple(flashSuccess, isFlashing, activeSlotLabel),
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.96f))
                                    .togetherWith(fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 0.98f))
                            },
                            modifier = Modifier.weight(1f),
                            label = "headerStatusAnim"
                        ) { (success, flashing, slotLabel) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = when (success) {
                                        true -> Icons.Default.CheckCircle
                                        false -> Icons.Default.Error
                                        null -> if (flashing) Icons.Default.FlashOn else Icons.Default.Verified
                                    },
                                    contentDescription = null,
                                    tint = when (success) {
                                        true -> MaterialTheme.colorScheme.primary
                                        false -> MaterialTheme.colorScheme.error
                                        null -> MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(28.dp)
                                )
                                Column {
                                    Text(
                                        text = when (success) {
                                            true -> "Flashing Completed Successfully"
                                            false -> "Flashing Failed"
                                            null -> if (flashing) "Flashing in Progress..." else "Flasher Ready"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Device: MediaTek MT6789 • Active: $slotLabel",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioNoBouncy
                            )
                        )
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
                                    enabled = !isFlashing && !isLoadingImage
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Change")
                                }
                            }
                        }

                        AnimatedContent(
                            targetState = Pair(isLoadingImage, selectedImage),
                            transitionSpec = {
                                (fadeIn(tween(220)) + scaleIn(initialScale = 0.97f))
                                    .togetherWith(fadeOut(tween(160)) + scaleOut(targetScale = 0.98f))
                            },
                            label = "imageSourceAnim"
                        ) { (loading, imageInfo) ->
                            if (loading) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                                        Text(
                                            "Verifying vendor_boot image header...",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            } else if (imageInfo == null) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = !isFlashing && !isLoadingImage
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
                                }
                            } else {
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
                                                imageInfo.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "${formatFileSize(imageInfo.sizeBytes)} • ${if (imageInfo.isStandardVendorBoot) "Standard vendor_boot" else "Raw partition image"}",
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
                }

                // Target Partition Slot Selection Card
                OutlinedCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioNoBouncy
                            )
                        )
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
                        .animateContentSize(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioNoBouncy
                            )
                        )
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
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem()
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioNoBouncy
                            )
                        ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { showConfirmFlashDialog = true },
                        enabled = isPermissionGranted && selectedImage != null && !isFlashing && !isLoadingImage,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Flash Image", style = MaterialTheme.typography.titleMedium)
                    }

                    if (flashSuccess == true) {
                        Button(
                            onClick = { showConfirmRebootDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
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
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val mb = bytes.toDouble() / (1024 * 1024)
    return String.format("%.1f MB", mb)
}
