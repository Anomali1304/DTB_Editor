package com.dtb.editor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val workspace by viewModel.workspace.collectAsState()
    val diffSummary by viewModel.diffSummary.collectAsState()
    var selectedFilter by remember { mutableStateOf<DiffType?>(null) }

    if (workspace == null || diffSummary == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(IdeBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("No Device Tree Open", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
        }
        return
    }

    val diff = diffSummary!!
    val filteredItems = remember(diff, selectedFilter) {
        if (selectedFilter == null) diff.items else diff.items.filter { it.type == selectedFilter }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IdeBackground)
    ) {
        // Compare Header Bar
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, tint = IdePrimaryCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AST DIFF INSPECTOR (+${diff.addedCount} / -${diff.removedCount} / ~${diff.modifiedCount})",
                        style = MaterialTheme.typography.labelSmall,
                        color = IdePrimaryCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        HorizontalDivider(color = IdeBorder)

        // Filter Bar
        Surface(
            color = IdeSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChipItem("All (${diff.items.size})", selectedFilter == null) { selectedFilter = null }
                FilterChipItem("Mod (${diff.modifiedCount})", selectedFilter == DiffType.MODIFIED) { selectedFilter = DiffType.MODIFIED }
                FilterChipItem("Add (${diff.addedCount})", selectedFilter == DiffType.ADDED) { selectedFilter = DiffType.ADDED }
                FilterChipItem("Del (${diff.removedCount})", selectedFilter == DiffType.REMOVED) { selectedFilter = DiffType.REMOVED }
            }
        }

        HorizontalDivider(color = IdeBorder)

        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (diff.items.isEmpty()) "Source is identical to original imported file." else "No items match current filter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredItems) { item ->
                    DiffCard(item = item)
                }
            }
        }
    }
}

@Composable
private fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = if (selected) IdePrimaryCyan else IdeBorder.copy(alpha = 0.5f),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (selected) IdeBackground else TextPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun DiffCard(item: NodeDiffItem) {
    val (badgeText, badgeColor) = when (item.type) {
        DiffType.ADDED -> Pair("+ ADD", IdeSecondaryGreen)
        DiffType.REMOVED -> Pair("- DEL", IdeErrorRed)
        DiffType.MODIFIED -> Pair("~ MOD", IdeWarningAmber)
        DiffType.UNCHANGED -> Pair("=", TextMuted)
    }

    Surface(
        color = IdeSurface,
        shape = RoundedCornerShape(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = badgeColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item.path,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(text = item.description, style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 10.sp)

            if (item.details.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = IdeBackground,
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.details,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    }
}
