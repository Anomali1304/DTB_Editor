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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dtb.editor.ui.theme.*
import com.dtb.editor.viewmodel.DtbViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: DtbViewModel
) {
    val project by viewModel.project.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val compilerOutput by viewModel.compilerOutput.collectAsState()

    var showFindReplace by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var showCompilerSheet by remember { mutableStateOf(false) }

    if (project == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Code, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("No Device Tree Loaded", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Import a file or load sample to edit DTS code.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        return
    }

    val currentDts = project!!.currentDts
    var textFieldValue by remember(currentDts) {
        mutableStateOf(TextFieldValue(currentDts))
    }

    // Auto-open compiler sheet when compilation completes
    LaunchedEffect(compilerOutput) {
        if (compilerOutput != null) {
            showCompilerSheet = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Toolbar
        Surface(
            color = DarkSurface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.undo() }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = TextPrimary)
                        }
                        IconButton(onClick = { viewModel.redo() }) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = TextPrimary)
                        }
                        IconButton(onClick = { showFindReplace = !showFindReplace }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Find & Replace",
                                tint = if (showFindReplace) TerminalCyan else TextSecondary
                            )
                        }
                        IconButton(onClick = { viewModel.resetToOriginal() }) {
                            Icon(Icons.Default.Restore, contentDescription = "Restore Original", tint = TerminalOrange)
                        }
                    }

                    // Compile Button
                    Button(
                        onClick = { viewModel.compile() },
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = TerminalGreen),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = DarkBackground)
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text("Compile DTB", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Find & Replace expandable bar
                if (showFindReplace) {
                    HorizontalDivider(color = DarkSurfaceVariant)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = findText,
                                onValueChange = { findText = it },
                                placeholder = { Text("Find...", color = TextSecondary, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = TextPrimary)
                            )
                            OutlinedTextField(
                                value = replaceText,
                                onValueChange = { replaceText = it },
                                placeholder = { Text("Replace with...", color = TextSecondary, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = TextPrimary)
                            )
                            Button(
                                onClick = {
                                    if (findText.isNotEmpty()) {
                                        val newText = textFieldValue.text.replace(findText, replaceText)
                                        textFieldValue = TextFieldValue(newText)
                                        viewModel.updateDtsText(newText)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Replace All", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Code Editor with Line Numbers
        val lines = remember(textFieldValue.text) { textFieldValue.text.lines() }
        val lineCount = lines.size
        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(DarkBackground)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Line Numbers Gutter
                Column(
                    modifier = Modifier
                        .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    for (i in 1..lineCount) {
                        Text(
                            text = "$i",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = TextTertiary,
                            lineHeight = 18.sp
                        )
                    }
                }

                VerticalDivider(color = DarkSurfaceVariant, modifier = Modifier.width(1.dp))

                // Editable Code Field
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = {
                            textFieldValue = it
                            viewModel.updateDtsText(it.text)
                        },
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = TextPrimary
                        ),
                        cursorBrush = SolidColor(TerminalCyan),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // Compiler Console Bottom Sheet
    if (showCompilerSheet && compilerOutput != null) {
        val out = compilerOutput!!
        ModalBottomSheet(
            onDismissRequest = { showCompilerSheet = false },
            containerColor = DarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (out.success) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (out.success) TerminalGreen else TerminalRed
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (out.success) "COMPILATION SUCCESSFUL" else "COMPILATION FAILED",
                            fontWeight = FontWeight.Bold,
                            color = if (out.success) TerminalGreen else TerminalRed
                        )
                    }

                    if (out.success) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = TerminalCyan.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${out.outputSizeBytes} bytes DTB",
                                color = TerminalCyan,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DarkBackground,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = out.log,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { showCompilerSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TerminalCyan)
                ) {
                    Text("Close Log", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
