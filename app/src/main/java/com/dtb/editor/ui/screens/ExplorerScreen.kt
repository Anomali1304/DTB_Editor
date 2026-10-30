package com.dtb.editor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dtb.editor.fdt.model.FdtNode
import com.dtb.editor.fdt.model.FdtProperty
import com.dtb.editor.ui.theme.*
import com.dtb.editor.viewmodel.DtbViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    viewModel: DtbViewModel
) {
    val project by viewModel.project.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var expandedPaths by remember { mutableStateOf(setOf("/")) }
    var selectedProperty by remember { mutableStateOf<Pair<String, FdtProperty>?>(null) }

    if (project == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AccountTree, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("No Device Tree Loaded", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Import a DTB or load sample to explore nodes.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        return
    }

    val rootNode = project!!.rootNode

    // Collect flattened visible items
    fun flattenTree(node: FdtNode, depth: Int): List<Pair<FdtNode, Int>> {
        val list = mutableListOf<Pair<FdtNode, Int>>()
        list.add(Pair(node, depth))
        val isExpanded = expandedPaths.contains(node.fullPath) || searchQuery.isNotEmpty()
        if (isExpanded) {
            for (child in node.children) {
                list.addAll(flattenTree(child, depth + 1))
            }
        }
        return list
    }

    val flattenedNodes = remember(rootNode, expandedPaths, searchQuery) {
        val all = flattenTree(rootNode, 0)
        if (searchQuery.isBlank()) {
            all
        } else {
            all.filter {
                it.first.name.contains(searchQuery, ignoreCase = true) ||
                        it.first.properties.any { p -> p.name.contains(searchQuery, ignoreCase = true) }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Search & Controls bar
        Surface(
            color = DarkSurface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter nodes (e.g. cpus, thermal, clock)...", color = TextSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TerminalCyan) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TerminalCyan,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total: ${rootNode.totalNodesCount} nodes • ${rootNode.totalPropertiesCount} props",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )

                    Row {
                        TextButton(
                            onClick = {
                                // Expand all top-level paths
                                val all = mutableSetOf<String>()
                                fun addPaths(n: FdtNode) {
                                    all.add(n.fullPath)
                                    n.children.forEach { addPaths(it) }
                                }
                                addPaths(rootNode)
                                expandedPaths = all
                            }
                        ) {
                            Text("Expand All", style = MaterialTheme.typography.labelSmall, color = TerminalCyan)
                        }
                        TextButton(onClick = { expandedPaths = setOf("/") }) {
                            Text("Collapse", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                }
            }
        }

        // Tree content
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(flattenedNodes) { (node, depth) ->
                val isExpanded = expandedPaths.contains(node.fullPath)
                val hasChildren = node.children.isNotEmpty()

                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (depth * 14).coerceAtMost(140).dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = hasChildren) {
                                    expandedPaths = if (isExpanded) {
                                        expandedPaths - node.fullPath
                                    } else {
                                        expandedPaths + node.fullPath
                                    }
                                }
                        ) {
                            if (hasChildren) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = TerminalCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Spacer(modifier = Modifier.width(20.dp))
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = node.name.ifEmpty { "/" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (node.name.startsWith("cpu")) TerminalGreen else TextPrimary,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )

                            // Node category badge
                            NodeCategoryBadge(node.name)
                        }

                        // Display properties summary if expanded
                        if (isExpanded && node.properties.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 26.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (prop in node.properties) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                                            .clickable { selectedProperty = Pair(node.name, prop) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = prop.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TerminalCyan,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = prop.formattedValue().take(30) + if (prop.formattedValue().length > 30) "…" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Property Detail Modal
    if (selectedProperty != null) {
        val (nodeName, prop) = selectedProperty!!
        AlertDialog(
            onDismissRequest = { selectedProperty = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = TerminalCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(prop.name, fontFamily = FontFamily.Monospace, color = TextPrimary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Node: $nodeName • ${prop.size} bytes",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )

                    HorizontalDivider(color = DarkSurfaceVariant)

                    Text(
                        text = "FORMATTED VALUE",
                        style = MaterialTheme.typography.labelSmall,
                        color = TerminalGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = DarkBackground,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = prop.formattedValue(),
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Text(
                        text = "RAW HEX DUMP",
                        style = MaterialTheme.typography.labelSmall,
                        color = TerminalOrange,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = DarkBackground,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = prop.asHexDump().ifEmpty { "(empty)" },
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedProperty = null }) {
                    Text("Close", color = TerminalCyan)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
private fun NodeCategoryBadge(nodeName: String) {
    val (label, color) = when {
        nodeName.startsWith("cpu") -> Pair("CPU", TerminalGreen)
        nodeName.contains("thermal") -> Pair("THERMAL", TerminalOrange)
        nodeName.contains("regulator") -> Pair("REGULATOR", TerminalPurple)
        nodeName.contains("clock") || nodeName == "clocks" -> Pair("CLOCK", TerminalCyan)
        nodeName.startsWith("memory") -> Pair("MEMORY", TerminalYellow)
        nodeName.contains("opp") || nodeName.contains("operating-points") -> Pair("DVFS", TerminalOrange)
        nodeName.contains("interrupt") -> Pair("INTR", TerminalRed)
        else -> return
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
