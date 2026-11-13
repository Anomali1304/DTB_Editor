package com.dtb.editor.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dtb.editor.R

@Composable
internal fun DtsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCase: Boolean,
    onToggleMatchCase: () -> Unit,
    matchCount: Int,
    activeIndex: Int,
    focusRequester: FocusRequester,
    onPrevMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.dts_search_placeholder),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onNextMatch() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .testTag("dts_search_input")
                        )
                    }

                    if (query.isNotEmpty()) {
                        Text(
                            text = if (matchCount > 0) {
                                stringResource(R.string.dts_search_results_count, activeIndex + 1, matchCount)
                            } else {
                                stringResource(R.string.dts_search_no_results)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (matchCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("dts_search_counter")
                        )

                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onQueryChange("") }
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (matchCase) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                modifier = Modifier
                    .clickable(onClick = onToggleMatchCase)
                    .padding(2.dp)
                    .testTag("dts_search_match_case")
            ) {
                Text(
                    text = "Aa",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (matchCase) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }

            IconButton(
                onClick = onPrevMatch,
                enabled = matchCount > 0,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("dts_search_prev")
            ) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.dts_search_previous),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onNextMatch,
                enabled = matchCount > 0,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("dts_search_next")
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.dts_search_next),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("dts_search_close")
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.dts_search_close),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
internal fun GoToLineDialog(
    totalLines: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    var lineInput by remember { mutableStateOf("") }
    val parsedLine = lineInput.toIntOrNull()
    val isValid = parsedLine != null && parsedLine in 1..totalLines.coerceAtLeast(1)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.dts_go_to_line),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            OutlinedTextField(
                value = lineInput,
                onValueChange = { input ->
                    if (input.all { it.isDigit() }) {
                        lineInput = input
                    }
                },
                singleLine = true,
                label = { Text(stringResource(R.string.dts_go_to_line_hint, totalLines.coerceAtLeast(1))) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(
                    onGo = { if (isValid) onJump(parsedLine!!) }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dts_go_to_line_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { if (parsedLine != null) onJump(parsedLine) },
                enabled = isValid
            ) {
                Text(stringResource(R.string.dts_jump_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dts_cancel_action))
            }
        }
    )
}
