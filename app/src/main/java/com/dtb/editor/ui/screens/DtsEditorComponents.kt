package com.dtb.editor.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.dtb.editor.R

@Composable
internal fun DtsCodeLineRow(
    lineIndex: Int,
    lineText: String,
    dtsRevision: Int,
    gutterWidth: Dp,
    viewportWidth: Dp,
    wordWrap: Boolean,
    horizontalScrollValueProvider: () -> Int,
    gutterBg: Color,
    rowHighlightBg: Color,
    dividerColor: Color,
    isEditing: Boolean,
    isMatchLine: Boolean,
    palette: DtsSyntaxPalette,
    lineMatches: List<DtsSearchMatch>?,
    activeMatch: DtsSearchMatch?,
    pendingCursorColumn: Int?,
    focusTrigger: Int,
    onTapLine: () -> Unit,
    onLineChanged: (String) -> Unit,
    onBackspaceAtStart: () -> Unit
) {
    val rowBaseModifier = if (wordWrap) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.widthIn(min = viewportWidth)
    }

    Row(
        modifier = rowBaseModifier
            .background(rowHighlightBg)
            .clickable(enabled = !isEditing, onClick = onTapLine),
        verticalAlignment = Alignment.Top
    ) {
        // Pinned Line Number Gutter (zero recomposition horizontal translation)
        Box(
            modifier = Modifier
                .zIndex(1f)
                .graphicsLayer {
                    if (!wordWrap) {
                        translationX = horizontalScrollValueProvider().toFloat()
                    }
                }
                .width(gutterWidth)
                .background(gutterBg)
                .padding(top = 2.dp, bottom = 2.dp, end = 8.dp, start = 4.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = (lineIndex + 1).toString(),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.End,
                fontWeight = if (isEditing || isMatchLine) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isEditing -> MaterialTheme.colorScheme.primary
                    isMatchLine -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.outline
                },
                maxLines = 1
            )
        }

        // Hairline gutter separator pinned with gutter
        Box(
            modifier = Modifier
                .zIndex(1f)
                .graphicsLayer {
                    if (!wordWrap) {
                        translationX = horizontalScrollValueProvider().toFloat()
                    }
                }
                .width(1.dp)
                .height(24.dp)
                .background(dividerColor)
        )

        Spacer(modifier = Modifier.width(8.dp))

        if (isEditing) {
            val focusRequester = remember { FocusRequester() }
            var fieldValue by remember(lineIndex, dtsRevision) {
                val safeCursor = (pendingCursorColumn ?: lineText.length).coerceIn(0, lineText.length)
                mutableStateOf(
                    TextFieldValue(
                        text = lineText,
                        selection = TextRange(safeCursor)
                    )
                )
            }

            LaunchedEffect(lineText, dtsRevision) {
                if (fieldValue.text != lineText) {
                    val safeCursor = fieldValue.selection.start.coerceIn(0, lineText.length)
                    fieldValue = TextFieldValue(text = lineText, selection = TextRange(safeCursor))
                }
            }

            LaunchedEffect(focusTrigger) {
                if (focusTrigger > 0) {
                    val targetCol = pendingCursorColumn
                    if (targetCol != null) {
                        val safeCol = targetCol.coerceIn(0, fieldValue.text.length)
                        fieldValue = fieldValue.copy(selection = TextRange(safeCol))
                    }
                    try {
                        focusRequester.requestFocus()
                    } catch (_: Exception) {}
                }
            }

            val codeModifier = if (wordWrap) {
                Modifier.weight(1f)
            } else {
                Modifier.widthIn(min = (viewportWidth - gutterWidth - 16.dp).coerceAtLeast(200.dp))
            }

            BasicTextField(
                value = fieldValue,
                onValueChange = { updated ->
                    fieldValue = updated
                    if (updated.text != lineText) {
                        onLineChanged(updated.text)
                    }
                },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = codeModifier
                    .padding(top = 2.dp, bottom = 2.dp, end = 24.dp)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown &&
                            keyEvent.key == Key.Backspace &&
                            fieldValue.selection.collapsed &&
                            fieldValue.selection.start == 0 &&
                            lineIndex > 0
                        ) {
                            onBackspaceAtStart()
                            true
                        } else {
                            false
                        }
                    }
            )
        } else {
            val highlighted = remember(lineText, palette, lineMatches, activeMatch) {
                DtsSyntaxHighlighter.buildHighlightedLine(
                    line = lineText,
                    palette = palette,
                    lineMatches = lineMatches,
                    activeMatch = activeMatch
                )
            }
            val textModifier = if (wordWrap) {
                Modifier
                    .weight(1f)
                    .padding(top = 2.dp, bottom = 2.dp, end = 16.dp)
            } else {
                Modifier
                    .padding(top = 2.dp, bottom = 2.dp, end = 24.dp)
            }
            Text(
                text = highlighted,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                softWrap = wordWrap,
                maxLines = if (wordWrap) Int.MAX_VALUE else 1,
                modifier = textModifier
            )
        }
    }
}

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
                    onGo = { if (isValid && parsedLine != null) onJump(parsedLine) }
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
