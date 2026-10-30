package com.dtb.editor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dtb.editor.ui.theme.*
import com.dtb.editor.viewmodel.ExportType

@Composable
fun ExportDialog(
    onDismiss: () -> Unit,
    onSelectExportType: (ExportType, String, String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.IosShare, contentDescription = null, tint = TerminalCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Device Tree File", color = TextPrimary)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Select output format for export via Storage Access Framework:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(4.dp))

                ExportOptionItem(
                    icon = Icons.Default.Save,
                    iconTint = TerminalGreen,
                    title = "Modified DTB Binary (.dtb)",
                    description = "Compiled binary with all current modifications",
                    onClick = {
                        onSelectExportType(ExportType.MODIFIED_DTB, "application/octet-stream", "modified_device_tree.dtb")
                        onDismiss()
                    }
                )

                ExportOptionItem(
                    icon = Icons.Default.Code,
                    iconTint = TerminalCyan,
                    title = "Modified DTS Source (.dts)",
                    description = "Human-readable Device Tree Source file",
                    onClick = {
                        onSelectExportType(ExportType.MODIFIED_DTS, "text/plain", "modified_device_tree.dts")
                        onDismiss()
                    }
                )

                ExportOptionItem(
                    icon = Icons.Default.History,
                    iconTint = TerminalOrange,
                    title = "Original Backup DTB (.dtb)",
                    description = "Untouched binary as imported",
                    onClick = {
                        onSelectExportType(ExportType.ORIGINAL_DTB, "application/octet-stream", "original_backup.dtb")
                        onDismiss()
                    }
                )

                ExportOptionItem(
                    icon = Icons.AutoMirrored.Filled.Article,
                    iconTint = TerminalPurple,
                    title = "Diff Comparison Report (.txt)",
                    description = "Summary list of added, removed, and modified nodes",
                    onClick = {
                        onSelectExportType(ExportType.DIFF_REPORT, "text/plain", "dtb_diff_report.txt")
                        onDismiss()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = DarkSurface
    )
}

@Composable
private fun ExportOptionItem(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        color = DarkBackground,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = iconTint.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}
