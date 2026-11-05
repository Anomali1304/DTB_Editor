package com.dtb.editor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dtb.editor.fdt.parser.DtsChunker
import com.dtb.editor.viewmodel.DtbViewModel
import com.dtb.editor.viewmodel.OpenFileState
import androidx.compose.ui.res.stringResource
import com.dtb.editor.R

@Composable
fun EditorScreen(
    fileState: OpenFileState,
    viewModel: DtbViewModel
) {
    val chunks = fileState.chunks
    val undoManager = fileState.undoManager

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.undo() },
                    enabled = undoManager.canUndo,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { viewModel.redo() },
                    enabled = undoManager.canRedo,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.editor_info, chunks.size, chunks.sumOf { it.lines().size }),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider()

        val horizontalScrollState = rememberScrollState()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalScrollState),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(chunks, key = { index, _ -> index }) { index, chunkText ->
                ChunkEditorItem(
                    chunkIndex = index,
                    chunkText = chunkText,
                    onTextChanged = { newText ->
                        viewModel.updateDtsChunk(index, newText)
                    }
                )
            }
        }
    }
}

@Composable
private fun ChunkEditorItem(
    chunkIndex: Int,
    chunkText: String,
    onTextChanged: (String) -> Unit
) {
    val lineCount = remember(chunkText) { chunkText.lines().size.coerceAtLeast(1) }
    val startLine = chunkIndex * DtsChunker.LINES_PER_CHUNK + 1
    val lineNumbersText = remember(startLine, lineCount) {
        (startLine until startLine + lineCount).joinToString("\n")
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = lineNumbersText,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .width(50.dp)
                .padding(top = 12.dp),
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.width(8.dp))

        OutlinedTextField(
            value = chunkText,
            onValueChange = onTextChanged,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onBackground
            ),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        )
    }
}
