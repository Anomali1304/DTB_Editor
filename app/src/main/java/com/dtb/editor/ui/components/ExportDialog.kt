package com.dtb.editor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dtb.editor.ui.theme.*
import com.dtb.editor.viewmodel.ExportTargetType

@Composable
fun ExportDialog(
    hasDtTableContainer: Boolean,
    onDismiss: () -> Unit,
    onSelectExportType: (ExportTargetType, String, String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.IosShare, contentDescription = null, tint = IdePrimaryCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export Device Tree File", color = TextPrimary, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (hasDtTableContainer) {
                    ExportOptionItem(
                        icon = Icons.Default.Layers,
                        iconTint = IdeSecondaryGreen,
                        title = "Rebuilt Android DT Table (.dtb)",
                        description = "Rebuilds 0xd7b7ab1e container image with updated entry payload & recalculated total_size",
                        onClick = {
                            onSelectExportType(ExportTargetType.REBUILT_DT_TABLE, "application/octet-stream", "rebuilt_dt_table.dtb")
                            onDismiss()
                        }
                    )
                }

                ExportOptionItem(
                    icon = Icons.Default.Save,
                    iconTint = IdePrimaryCyan,
                    title = "Raw Compiled FDT (.dtb)",
                    description = "Exports standalone 0xd00dfeed binary DTB payload for current entry",
                    onClick = {
                        onSelectExportType(ExportTargetType.RAW_COMPILED_DTB, "application/octet-stream", "compiled_entry.dtb")
                        onDismiss()
                    }
                )

                ExportOptionItem(
                    icon = Icons.Default.Code,
                    iconTint = IdePrimaryCyan,
                    title = "Edited DTS Source (.dts)",
                    description = "Save raw source code file as UTF-8 text",
                    onClick = {
                        onSelectExportType(ExportTargetType.EDITED_DTS, "text/plain", "device_tree.dts")
                        onDismiss()
                    }
                )

                ExportOptionItem(
                    icon = Icons.Default.History,
                    iconTint = IdeWarningAmber,
                    title = "Original Backup Image (.dtb)",
                    description = "Export untouched original imported binary file",
                    onClick = {
                        onSelectExportType(ExportTargetType.ORIGINAL_DTB, "application/octet-stream", "original_backup.dtb")
                        onDismiss()
                    }
                )

                ExportOptionItem(
                    icon = Icons.AutoMirrored.Filled.Article,
                    iconTint = TextSecondary,
                    title = "Diff Summary Report (.txt)",
                    description = "Export text summary of added/removed/modified nodes",
                    onClick = {
                        onSelectExportType(ExportTargetType.DIFF_REPORT, "text/plain", "dtb_diff_report.txt")
                        onDismiss()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary, fontSize = 12.sp)
            }
        },
        containerColor = IdeSurface
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
        color = IdeBackground,
        shape = RoundedCornerShape(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 11.sp
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}
