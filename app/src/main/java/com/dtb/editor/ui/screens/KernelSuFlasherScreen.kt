package com.dtb.editor.ui.screens

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dtb.editor.root.FlashSlotInfo
import com.dtb.editor.viewmodel.DtbViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KernelSuFlasherScreen(
    viewModel: DtbViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isRootChecked by remember { mutableStateOf(false) }
    var isRootGranted by remember { mutableStateOf(false) }
    var showRootDeniedDialog by remember { mutableStateOf(false) }

    var slotInfo by remember { mutableStateOf<FlashSlotInfo?>(null) }
    var selectedTarget by remember { mutableStateOf("active") }

    var isFlashing by remember { mutableStateOf(false) }
    var flashSuccess by remember { mutableStateOf<Boolean?>(null) }
    val logs = remember { mutableStateListOf<String>() }

    var showConfirmFlashDialog by remember { mutableStateOf(false) }
    var showConfirmRebootDialog by remember { mutableStateOf(false) }

    val logListState = rememberLazyListState()

    // Auto-scroll logs
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            logListState.animateScrollToItem(logs.size - 1)
        }
    }

    // Check Root & Detect Slot on launch
    LaunchedEffect(Unit) {
        val rooted = viewModel.checkRootAccess()
        isRootGranted = rooted
        isRootChecked = true
        if (rooted) {
            slotInfo = viewModel.detectSlotInfo()
            logs.add("[+] Root access granted (uid=0)")
            logs.add("- Target device model: MediaTek MT6789")
            logs.add("- Active slot suffix: ${slotInfo?.activeSlotSuffix ?: "N/A"}")
        } else {
            showRootDeniedDialog = true
        }
    }

    // Root Permission Dialog
    if (showRootDeniedDialog) {
        AlertDialog(
            onDismissRequest = {
                showRootDeniedDialog = false
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
                    "Membutuhkan Akses Root",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Aplikasi memerlukan akses Root untuk membaca dan menulis partisi sistem (vendor_boot).\n\nSilakan izinkan permintaan Root pada Root Manager Anda (Magisk / KernelSU / APatch) lalu tekan Coba Lagi.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val rooted = viewModel.checkRootAccess()
                            isRootGranted = rooted
                            if (rooted) {
                                showRootDeniedDialog = false
                                slotInfo = viewModel.detectSlotInfo()
                                logs.clear()
                                logs.add("[+] Root access granted (uid=0)")
                                logs.add("- Active slot suffix: ${slotInfo?.activeSlotSuffix ?: "N/A"}")
                            }
                        }
                    }
                ) {
                    Text("Coba Lagi")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRootDeniedDialog = false
                        onClose()
                    }
                ) {
                    Text("Batal")
                }
            }
        )
    }

    // Confirmation Flash Dialog
    if (showConfirmFlashDialog) {
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
                    "Konfirmasi Flashing",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Apakah Anda yakin ingin melakukan flash gambar DTB/vendor_boot ke partisi [$targetName]?\n\nTindakan ini menimpa partisi sistem secara langsung.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmFlashDialog = false
                        isFlashing = true
                        flashSuccess = null
                        logs.add("[*] Starting flash sequence to $targetName...")

                        viewModel.flashToPartitionWithLogs(
                            targetPartition = targetName,
                            context = context,
                            onLogLine = { line ->
                                logs.add(line)
                            },
                            onFinished = { success, message ->
                                isFlashing = false
                                flashSuccess = success
                                if (success) {
                                    logs.add("[+] $message")
                                } else {
                                    logs.add("[-] $message")
                                }
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ya, Lanjutkan Flash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmFlashDialog = false }) {
                    Text("Batal")
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
                    "Konfirmasi Reboot",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Apakah Anda yakin ingin melakukan reboot sistem sekarang untuk menerapkan hasil flashing?",
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
                    Text("Reboot Sekarang")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmRebootDialog = false }) {
                    Text("Nanti")
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
                        "Root Partition Flasher",
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
            // Header Status Badge (KernelSU Style)
            Surface(
                color = when (flashSuccess) {
                    true -> Color(0x222E7D32)
                    false -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    null -> if (isFlashing) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant
                },
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
                                null -> if (isFlashing) Icons.Default.FlashOn else Icons.Default.Security
                            },
                            contentDescription = null,
                            tint = when (flashSuccess) {
                                true -> Color(0xFF4CAF50)
                                false -> MaterialTheme.colorScheme.error
                                null -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = when (flashSuccess) {
                                    true -> "Flash Selesai & Berhasil!"
                                    false -> "Flashing Gagal"
                                    null -> if (isFlashing) "Sedang Melakukan Flash..." else "Root Flasher Ready"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Target: MediaTek MT6789 vendor_boot ($activeSlotLabel)",
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

            // Target Slot Selection Card
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Pilih Slot Target Partisi:",
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
                                    "Slot Aktif ($activeDefaultPart)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Rekomendasi - Flash langsung ke slot yang sedang dipakai",
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
                            Text("Kedua Slot (vendor_boot_a & _b)", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // KernelSU Style Terminal Output Box
            Surface(
                color = Color(0xFF0D1117), // Dark terminal background
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "TERMINAL CONSOLE LOG",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8B949E)
                        )
                        Text(
                            "${logs.size} lines",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF8B949E)
                        )
                    }
                    HorizontalDivider(
                        color = Color(0xFF21262D),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    LazyColumn(
                        state = logListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs) { line ->
                            val textColor = when {
                                line.startsWith("[+]") -> Color(0xFF4ADE80) // Light green success
                                line.startsWith("[-]") -> Color(0xFFF87171) // Light red error
                                line.startsWith("[*]") -> Color(0xFF60A5FA) // Light blue info
                                else -> Color(0xFFC9D1D9) // Off-white console text
                            }
                            Text(
                                text = line,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = textColor
                            )
                        }
                    }
                }
            }

            // Action Bar (Start Flash / Reboot Device)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { showConfirmFlashDialog = true },
                    enabled = isRootGranted && !isFlashing,
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mulai Flash", style = MaterialTheme.typography.titleMedium)
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
