package com.dtb.editor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dtb.editor.ui.theme.*
import com.dtb.editor.viewmodel.DtbViewModel

@Composable
fun EditorScreen(
    viewModel: DtbViewModel
) {
    val workspace by viewModel.workspace.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    var showFindReplace by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }

    if (workspace == null) return

    val currentWs = workspace!!

    // Local editor text state - sync with workspace when document instance changes
    var textFieldValue by remember(currentWs.fileName) {
        mutableStateOf(TextFieldValue(currentWs.sourceText, selection = TextRange(0)))
    }

    // Keep textFieldValue in sync if external actions (undo/redo/revert/sample) update sourceText
    LaunchedEffect(currentWs.sourceText) {
        if (textFieldValue.text != currentWs.sourceText) {
            val cursor = textFieldValue.selection.start.coerceAtMost(currentWs.sourceText.length)
            textFieldValue = TextFieldValue(currentWs.sourceText, selection = TextRange(cursor))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IdeBackground)
    ) {
        // Compact Tool Header
        Surface(
            color = IdeSurfaceHeader,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.undo() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = TextPrimary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { viewModel.redo() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = TextPrimary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { showFindReplace = !showFindReplace }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Find & Replace",
                                tint = if (showFindReplace) IdePrimaryCyan else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = { viewModel.updateSourceText(currentWs.originalSourceText) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Restore, contentDescription = "Revert to Original", tint = IdeWarningAmber, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Compile Action Button
                    Button(
                        onClick = { viewModel.compile() },
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = IdeSecondaryGreen),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = IdeBackground)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = IdeBackground, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Compile", color = IdeBackground, style = MaterialTheme.typography.labelMedium, fontSize = 12.sp)
                        }
                    }
                }

                // Find & Replace Bar
                if (showFindReplace) {
                    HorizontalDivider(color = IdeBorder)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = findText,
                            onValueChange = { findText = it },
                            placeholder = { Text("Find...", color = TextMuted, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f).height(36.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(4.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, color = TextPrimary)
                        )
                        OutlinedTextField(
                            value = replaceText,
                            onValueChange = { replaceText = it },
                            placeholder = { Text("Replace...", color = TextMuted, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f).height(36.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(4.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, color = TextPrimary)
                        )
                        Button(
                            onClick = {
                                if (findText.isNotEmpty()) {
                                    val newText = textFieldValue.text.replace(findText, replaceText)
                                    textFieldValue = TextFieldValue(newText, selection = TextRange(0))
                                    viewModel.updateSourceText(newText)
                                }
                            },
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("All", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = IdeBorder)

        // Code Editor Area with Synchronized Line Numbers
        val lineCount = remember(textFieldValue.text) {
            textFieldValue.text.count { it == '\n' } + 1
        }
        val verticalScrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(IdeBackground)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState)
            ) {
                // Line Numbers Column
                Column(
                    modifier = Modifier
                        .background(IdeSurfaceHeader.copy(alpha = 0.4f))
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    for (i in 1..lineCount) {
                        Text(
                            text = "$i",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextMuted,
                            lineHeight = 16.sp
                        )
                    }
                }

                VerticalDivider(color = IdeBorder, modifier = Modifier.width(1.dp))

                // Editable DTS Code Field
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = {
                            textFieldValue = it
                            viewModel.updateSourceText(it.text)
                        },
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = TextPrimary
                        ),
                        cursorBrush = SolidColor(IdePrimaryCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 400.dp)
                    )
                }
            }
        }
    }
}
