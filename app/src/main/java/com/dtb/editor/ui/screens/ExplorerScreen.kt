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
    val workspace by viewModel.workspace.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var expandedPaths by remember { mutableStateOf(setOf("/")) }
    var selectedProperty by remember { mutableStateOf<Pair<String, FdtProperty>?>(null) }

    if (workspace == null || workspace?.parsedAst == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(IdeBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AccountTree, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("No Parsed Device Tree", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Import a file or open DTS source to inspect the tree.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        return
    }

    val rootNode = workspace!!.parsedAst!!

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
            .background(IdeBackground)
    ) {
        // Quick Navigation Shortcuts Bar for Key Nodes
        Surface(
            color = IdeSurfaceHeader,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search nodes or properties...", color = TextMuted, fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = IdePrimaryCyan, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IdePrimaryCyan,
                        unfocusedBorderColor = IdeBorder,
                        focusedContainerColor = IdeSurface,
                        unfocusedContainerColor = IdeSurface
                    ),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, color = TextPrimary)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Navigation Target Shortcut Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val keyTargets = listOf("cpus", "thermal-zones", "regulators", "clocks", "memory")
                    for (target in keyTargets) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = IdeBorder.copy(alpha = 0.6f),
                            modifier = Modifier.clickable { searchQuery = target }
                        ) {
                            Text(
                                text = target,
                                color = IdePrimaryCyan,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = IdeBorder)

        // Tree List Area
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(flattenedNodes) { (node, depth) ->
                val isExpanded = expandedPaths.contains(node.fullPath)
                val hasChildren = node.children.isNotEmpty()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (depth * 12).coerceAtMost(120).dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(2.dp))
                            .clickable(enabled = hasChildren) {
                                expandedPaths = if (isExpanded) {
                                    expandedPaths - node.fullPath
                                } else {
                                    expandedPaths + node.fullPath
                                }
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                    ) {
                        if (hasChildren) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = IdePrimaryCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.width(16.dp))
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = node.name.ifEmpty { "/" },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (node.name.startsWith("cpu")) IdeSecondaryGreen else TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )

                        if (node.properties.isNotEmpty()) {
                            Text(
                                text = "${node.properties.size} props",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Properties
                    if (isExpanded && node.properties.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, top = 2.dp, bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            for (prop in node.properties) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(IdeSurface)
                                        .clickable { selectedProperty = Pair(node.name, prop) }
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = prop.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SyntaxPropertyName,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = prop.formattedValue().take(28) + if (prop.formattedValue().length > 28) "…" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Property Detail Sheet
    if (selectedProperty != null) {
        val (nodeName, prop) = selectedProperty!!
        AlertDialog(
            onDismissRequest = { selectedProperty = null },
            title = {
                Text("${prop.name} ($nodeName)", fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("FORMATTED VALUE", style = MaterialTheme.typography.labelSmall, color = IdeSecondaryGreen)
                    Surface(
                        color = IdeBackground,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = prop.formattedValue(),
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }

                    Text("HEX DUMP (${prop.size} B)", style = MaterialTheme.typography.labelSmall, color = IdePrimaryCyan)
                    Surface(
                        color = IdeBackground,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = prop.asHexDump().ifEmpty { "(empty)" },
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedProperty = null }) {
                    Text("Close", color = IdePrimaryCyan)
                }
            },
            containerColor = IdeSurface
        )
    }
}
