package com.dtb.editor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.IosShare
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
    onDismiss: () -> Unit,
    onSelectExportType: (ExportTargetType, String, String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.IosShare, contentDescription = null, tint = IdePrimaryCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export File", color = TextPrimary, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ExportOptionItem(
                    icon = Icons.Default.Save,
                    iconTint = IdeSecondaryGreen,
                    title = "Compiled Binary DTB (.dtb)",
                    description = "Compile current DTS source to FDT binary",
                    onClick = {
                        onSelectExportType(ExportTargetType.COMPILED_DTB, "application/octet-stream", "compiled_device_tree.dtb")
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
                    title = "Original Backup Binary (.dtb)",
                    description = "Export untouched original imported binary",
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
