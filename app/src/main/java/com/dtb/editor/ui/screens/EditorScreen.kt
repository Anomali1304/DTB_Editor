package com.dtb.editor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dtb.editor.viewmodel.DtbViewModel
import com.dtb.editor.viewmodel.OpenFileState

@Composable
fun EditorScreen(
    fileState: OpenFileState,
    viewModel: DtbViewModel
) {
    var textState by remember(fileState.fileName, fileState.selectedEntryIndex) {
        mutableStateOf(fileState.currentDtsText)
    }

    LaunchedEffect(fileState.currentDtsText) {
        if (textState != fileState.currentDtsText) {
            textState = fileState.currentDtsText
        }
    }

    val verticalScrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = textState,
            onValueChange = { newText ->
                textState = newText
                viewModel.updateDtsText(newText)
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground
            ),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScrollState)
        )
    }
}
