package com.dtb.editor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dtb.editor.ui.theme.*
import com.dtb.editor.viewmodel.ActiveWorkspace

@Composable
fun BinaryView(
    workspace: ActiveWorkspace?
) {
    if (workspace == null || workspace.originalBytes == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(IdeBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Hexagon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("No Original Binary DTB Data", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Import a .dtb or .dtbo file to inspect raw hex blocks.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        return
    }

    val bytes = workspace.originalBytes
    val chunkSize = 16
    val rowCount = (bytes.size + chunkSize - 1) / chunkSize

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IdeBackground)
    ) {
        // Hex Header Bar
        Surface(
            color = IdeSurfaceHeader,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RAW DTB HEX INSPECTOR • ${bytes.size} BYTES",
                    style = MaterialTheme.typography.labelSmall,
                    color = IdePrimaryCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "MAGIC: 0x${String.format("%02x%02x%02x%02x", bytes[0], bytes[1], bytes[2], bytes[3])}",
                    style = MaterialTheme.typography.labelSmall,
                    color = IdeSecondaryGreen,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        HorizontalDivider(color = IdeBorder)

        // Hex Table
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            items(rowCount.coerceAtMost(2048)) { rowIndex ->
                val offset = rowIndex * chunkSize
                val end = (offset + chunkSize).coerceAtMost(bytes.size)
                val rowBytes = bytes.copyOfRange(offset, end)

                val hexString = rowBytes.joinToString(" ") { String.format("%02x", it) }
                val asciiString = rowBytes.map { b ->
                    val c = b.toInt() and 0xFF
                    if (c in 32..126) c.toChar() else '.'
                }.joinToString("")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = String.format("%08x", offset),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = IdePrimaryCyan,
                        fontSize = 11.sp
                    )

                    Text(
                        text = hexString.padEnd(47, ' '),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary,
                        fontSize = 11.sp
                    )

                    Text(
                        text = asciiString,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
