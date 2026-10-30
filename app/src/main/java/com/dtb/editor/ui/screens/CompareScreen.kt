package com.dtb.editor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dtb.editor.fdt.diff.DiffType
import com.dtb.editor.fdt.diff.NodeDiffItem
import com.dtb.editor.ui.theme.*
import com.dtb.editor.viewmodel.DtbViewModel

@Composable
fun CompareScreen(
    viewModel: DtbViewModel
) {
    val project by viewModel.project.collectAsState()
    val diffSummary by viewModel.diffSummary.collectAsState()
    var selectedFilter by remember { mutableStateOf<DiffType?>(null) }

    if (project == null || diffSummary == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("No Device Tree Loaded", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Import a file or load sample to compare changes.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        return
    }

    val diff = diffSummary!!
    val filteredItems = remember(diff, selectedFilter) {
        if (selectedFilter == null) diff.items else diff.items.filter { it.type == selectedFilter }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Summary Header Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Device Tree Diff Inspector",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TerminalCyan
                    )
                    Text(
                        text = "Comparing original imported file vs current workspace DTS",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = DarkSurfaceVariant)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        DiffStatBadge(label = "Added", count = diff.addedCount, color = TerminalGreen)
                        DiffStatBadge(label = "Removed", count = diff.removedCount, color = TerminalRed)
                        DiffStatBadge(label = "Modified", count = diff.modifiedCount, color = TerminalOrange)
                    }
                }
            }
        }

        // 2. Filter Tabs
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null },
                    label = { Text("All (${diff.items.size})") }
                )
                FilterChip(
                    selected = selectedFilter == DiffType.MODIFIED,
                    onClick = { selectedFilter = DiffType.MODIFIED },
                    label = { Text("Modified (${diff.modifiedCount})") }
                )
                FilterChip(
                    selected = selectedFilter == DiffType.ADDED,
                    onClick = { selectedFilter = DiffType.ADDED },
                    label = { Text("Added (${diff.addedCount})") }
                )
                FilterChip(
                    selected = selectedFilter == DiffType.REMOVED,
                    onClick = { selectedFilter = DiffType.REMOVED },
                    label = { Text("Removed (${diff.removedCount})") }
                )
            }
        }

        // 3. Diff Items List
        if (filteredItems.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CheckCircleOutline, contentDescription = null, tint = TerminalGreen, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (diff.items.isEmpty()) "Identical to Original" else "No matching items for filter",
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "No differences found between current DTS and original.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } else {
            items(filteredItems) { item ->
                DiffItemCard(item = item)
            }
        }
    }
}

@Composable
private fun DiffStatBadge(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun DiffItemCard(item: NodeDiffItem) {
    val (badgeText, badgeColor) = when (item.type) {
        DiffType.ADDED -> Pair("+ ADD", TerminalGreen)
        DiffType.REMOVED -> Pair("- DEL", TerminalRed)
        DiffType.MODIFIED -> Pair("~ MOD", TerminalOrange)
        DiffType.UNCHANGED -> Pair("=", TextSecondary)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.path,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            if (item.details.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = DarkBackground,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.details,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}
