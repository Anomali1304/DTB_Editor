package com.dtb.editor.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dtb.editor.R
import com.dtb.editor.viewmodel.DtbViewModel
import com.dtb.editor.viewmodel.OpenFileState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun EditorScreen(
    fileState: OpenFileState,
    viewModel: DtbViewModel
) {
    val lines = fileState.dtsLines
    val dtsRevision = fileState.dtsRevision
    val canUndo = fileState.lineUndoManager.canUndo || fileState.undoManager.canUndo
    val canRedo = fileState.lineUndoManager.canRedo || fileState.undoManager.canRedo

    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()
    val density = LocalDensity.current

    var wordWrap by remember { mutableStateOf(false) }
    var editingLineIndex by remember { mutableStateOf<Int?>(null) }
    var pendingCursorColumn by remember { mutableStateOf<Int?>(null) }
    var focusTrigger by remember { mutableIntStateOf(0) }

    // Search state
    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var searchMatches by remember { mutableStateOf<List<DtsSearchMatch>>(emptyList()) }
    var matchesByLine by remember { mutableStateOf<Map<Int, List<DtsSearchMatch>>>(emptyMap()) }
    var activeMatchIndex by remember { mutableIntStateOf(0) }
    var lastScrolledQueryKey by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val searchFocusRequester = remember { FocusRequester() }

    // Go to line dialog state
    var showGoToLineDialog by remember { mutableStateOf(false) }

    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val syntaxPalette = remember(colorScheme, isDark) {
        DtsSyntaxPalette(
            text = colorScheme.onBackground,
            comment = if (isDark) Color(0xFF7CA872) else Color(0xFF4A7840),
            stringLiteral = if (isDark) Color(0xFFE09F7D) else Color(0xFFA64B2A),
            propertyKey = colorScheme.primary,
            nodeTitle = colorScheme.secondary,
            hexOrNumber = if (isDark) Color(0xFFB5CEA8) else Color(0xFF196E58),
            directive = colorScheme.tertiary,
            punctuation = colorScheme.onSurfaceVariant,
            matchHighlightBg = colorScheme.secondaryContainer,
            activeMatchHighlightBg = colorScheme.tertiary,
            activeMatchHighlightText = colorScheme.onTertiary
        )
    }

    suspend fun scrollToMatch(match: DtsSearchMatch) = withContext(Dispatchers.Main.immediate) {
        val targetItem = (match.lineIndex - 5).coerceAtLeast(0)
        if (abs(listState.firstVisibleItemIndex - targetItem) > 35) {
            listState.scrollToItem(targetItem)
        } else {
            listState.animateScrollToItem(targetItem)
        }
        if (!wordWrap) {
            val charWidthPx = with(density) { 7.8.dp.toPx() }
            val desiredX = ((match.startCol - 12).coerceAtLeast(0) * charWidthPx).toInt()
            if (desiredX > horizontalScrollState.value || desiredX < horizontalScrollState.value - 200) {
                horizontalScrollState.animateScrollTo(desiredX.coerceAtMost(horizontalScrollState.maxValue))
            }
        }
    }

    // Async background search across full DTS (zero UI blocking)
    LaunchedEffect(isSearchVisible, searchQuery, matchCase, lines) {
        if (!isSearchVisible || searchQuery.isEmpty()) {
            searchMatches = emptyList()
            matchesByLine = emptyMap()
            activeMatchIndex = 0
            lastScrolledQueryKey = null
            return@LaunchedEffect
        }
        delay(60)
        val (found, grouped) = withContext(Dispatchers.Default) {
            val results = DtsSearchEngine.findAllMatches(lines, searchQuery, matchCase)
            results to results.groupBy { it.lineIndex }
        }
        searchMatches = found
        matchesByLine = grouped

        val currentQueryKey = searchQuery to matchCase
        if (found.isNotEmpty()) {
            if (lastScrolledQueryKey != currentQueryKey) {
                lastScrolledQueryKey = currentQueryKey
                val currentTop = listState.firstVisibleItemIndex
                val nearestIdx = found.indexOfFirst { it.lineIndex >= currentTop }.let {
                    if (it >= 0) it else 0
                }
                activeMatchIndex = nearestIdx
                scrollToMatch(found[nearestIdx])
            } else {
                activeMatchIndex = activeMatchIndex.coerceIn(0, found.lastIndex)
            }
        } else {
            activeMatchIndex = 0
        }
    }

    LaunchedEffect(isSearchVisible) {
        if (isSearchVisible) {
            delay(80)
            try {
                searchFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Ensure editingLineIndex stays valid if lines shrink
    LaunchedEffect(lines.size) {
        val idx = editingLineIndex
        if (idx != null && idx >= lines.size) {
            editingLineIndex = (lines.size - 1).coerceAtLeast(0)
        }
    }

    if (showGoToLineDialog) {
        GoToLineDialog(
            totalLines = lines.size,
            onDismiss = { showGoToLineDialog = false },
            onJump = { lineOneBased ->
                showGoToLineDialog = false
                val targetIdx = (lineOneBased - 1).coerceIn(0, (lines.size - 1).coerceAtLeast(0))
                coroutineScope.launch {
                    val scrollTarget = (targetIdx - 4).coerceAtLeast(0)
                    if (abs(listState.firstVisibleItemIndex - scrollTarget) > 35) {
                        listState.scrollToItem(scrollTarget)
                    } else {
                        listState.animateScrollToItem(scrollTarget)
                    }
                    editingLineIndex = targetIdx
                    pendingCursorColumn = 0
                    focusTrigger++
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        // Primary Editor Toolbar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val targetLine = viewModel.undo()
                        if (targetLine != null) {
                            coroutineScope.launch {
                                listState.scrollToItem((targetLine - 4).coerceAtLeast(0))
                            }
                            if (editingLineIndex != null) {
                                editingLineIndex = targetLine
                                focusTrigger++
                            }
                        }
                    },
                    enabled = canUndo,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("dts_undo_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = {
                        val targetLine = viewModel.redo()
                        if (targetLine != null) {
                            coroutineScope.launch {
                                listState.scrollToItem((targetLine - 4).coerceAtLeast(0))
                            }
                            if (editingLineIndex != null) {
                                editingLineIndex = targetLine
                                focusTrigger++
                            }
                        }
                    },
                    enabled = canRedo,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("dts_redo_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconToggleButton(
                    checked = isSearchVisible,
                    onCheckedChange = { open ->
                        isSearchVisible = open
                        if (!open) {
                            searchQuery = ""
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("dts_search_toggle")
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.dts_search_action),
                        tint = if (isSearchVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconToggleButton(
                    checked = wordWrap,
                    onCheckedChange = { wordWrap = it },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("dts_wrap_toggle")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.WrapText,
                        contentDescription = stringResource(R.string.dts_word_wrap),
                        tint = if (wordWrap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .clickable { showGoToLineDialog = true }
                        .testTag("dts_line_count_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val activeLineDisplay = editingLineIndex
                        val labelText = if (activeLineDisplay != null) {
                            "Ln ${activeLineDisplay + 1} / ${lines.size}"
                        } else {
                            stringResource(R.string.editor_info, lines.size)
                        }
                        Text(
                            text = labelText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Expandable Fast Search Bar
        AnimatedVisibility(
            visible = isSearchVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            DtsSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                matchCase = matchCase,
                onToggleMatchCase = { matchCase = !matchCase },
                matchCount = searchMatches.size,
                activeIndex = activeMatchIndex,
                focusRequester = searchFocusRequester,
                onPrevMatch = {
                    if (searchMatches.isNotEmpty()) {
                        val next = if (activeMatchIndex - 1 < 0) searchMatches.lastIndex else activeMatchIndex - 1
                        activeMatchIndex = next
                        coroutineScope.launch { scrollToMatch(searchMatches[next]) }
                    }
                },
                onNextMatch = {
                    if (searchMatches.isNotEmpty()) {
                        val next = (activeMatchIndex + 1) % searchMatches.size
                        activeMatchIndex = next
                        coroutineScope.launch { scrollToMatch(searchMatches[next]) }
                    }
                },
                onClose = {
                    isSearchVisible = false
                    searchQuery = ""
                    focusManager.clearFocus()
                }
            )
        }

        // Contextual Quick Actions Strip when editing a line
        AnimatedVisibility(
            visible = editingLineIndex != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            val activeIdx = editingLineIndex
            if (activeIdx != null && activeIdx in lines.indices) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = {
                                val insertPos = activeIdx + 1
                                viewModel.replaceDtsLines(
                                    startLine = insertPos,
                                    removeCount = 0,
                                    newLines = listOf(""),
                                    focusLineAfterUndo = activeIdx,
                                    focusLineAfterRedo = insertPos
                                )
                                editingLineIndex = insertPos
                                pendingCursorColumn = 0
                                focusTrigger++
                            },
                            label = { Text(stringResource(R.string.dts_insert_line), style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.testTag("dts_insert_line_btn")
                        )
                        AssistChip(
                            onClick = {
                                if (lines.size > 1) {
                                    val nextFocus = activeIdx.coerceAtMost(lines.size - 2)
                                    viewModel.replaceDtsLines(
                                        startLine = activeIdx,
                                        removeCount = 1,
                                        newLines = emptyList(),
                                        focusLineAfterUndo = activeIdx,
                                        focusLineAfterRedo = nextFocus
                                    )
                                    editingLineIndex = nextFocus
                                    pendingCursorColumn = 0
                                    focusTrigger++
                                } else {
                                    viewModel.updateDtsLine(0, "")
                                    pendingCursorColumn = 0
                                    focusTrigger++
                                }
                            },
                            label = { Text(stringResource(R.string.dts_delete_line), style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.testTag("dts_delete_line_btn")
                        )
                        AssistChip(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(lines[activeIdx]))
                            },
                            label = { Text(stringResource(R.string.dts_copy_line), style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.testTag("dts_copy_line_btn")
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                editingLineIndex = null
                                focusManager.clearFocus()
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("dts_done_editing_btn")
                        ) {
                            Text(stringResource(R.string.dts_done_editing), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        val gutterWidth: Dp = remember(lines.size) {
            val digits = lines.size.coerceAtLeast(1).toString().length
            (digits * 8 + 18).coerceAtLeast(44).dp
        }
        val activeMatch = searchMatches.getOrNull(activeMatchIndex)
        val gutterBg = MaterialTheme.colorScheme.surfaceContainerLowest
        val activeLineBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        val matchLineBg = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
        val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

        // Seamless Full-Document Virtualized Code Area
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            val viewportWidth = maxWidth
            val listModifier = if (wordWrap) {
                Modifier
                    .fillMaxSize()
                    .testTag("dts_editor_list")
            } else {
                Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScrollState)
                    .testTag("dts_editor_list")
            }

            LazyColumn(
                state = listState,
                modifier = listModifier,
                contentPadding = PaddingValues(top = 6.dp, bottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(
                    items = lines,
                    key = { index, _ -> index }
                ) { index, lineText ->
                    val isEditing = editingLineIndex == index
                    val lineMatches = matchesByLine[index]
                    val isActiveMatchLine = activeMatch?.lineIndex == index

                    DtsCodeLineRow(
                        lineIndex = index,
                        lineText = lineText,
                        dtsRevision = dtsRevision,
                        gutterWidth = gutterWidth,
                        viewportWidth = viewportWidth,
                        wordWrap = wordWrap,
                        horizontalScrollValueProvider = { horizontalScrollState.value },
                        gutterBg = gutterBg,
                        rowHighlightBg = when {
                            isEditing -> activeLineBg
                            isActiveMatchLine -> matchLineBg
                            else -> Color.Transparent
                        },
                        dividerColor = dividerColor,
                        isEditing = isEditing,
                        isMatchLine = isActiveMatchLine || !lineMatches.isNullOrEmpty(),
                        palette = syntaxPalette,
                        lineMatches = lineMatches,
                        activeMatch = if (isActiveMatchLine) activeMatch else null,
                        pendingCursorColumn = if (isEditing) pendingCursorColumn else null,
                        focusTrigger = if (isEditing) focusTrigger else 0,
                        onTapLine = {
                            editingLineIndex = index
                            pendingCursorColumn = lineText.length
                            focusTrigger++
                        },
                        onLineChanged = { newText ->
                            if (newText.contains('\n')) {
                                val normalized = newText.replace("\r\n", "\n").replace("\r", "\n")
                                val splitLines = normalized.split("\n")
                                val insertIndex = index + splitLines.size - 1
                                viewModel.replaceDtsLines(
                                    startLine = index,
                                    removeCount = 1,
                                    newLines = splitLines,
                                    focusLineAfterUndo = index,
                                    focusLineAfterRedo = insertIndex
                                )
                                editingLineIndex = insertIndex
                                pendingCursorColumn = if (splitLines.size == 2 && splitLines[1].isEmpty()) {
                                    0
                                } else {
                                    splitLines.last().length
                                }
                                focusTrigger++
                            } else {
                                viewModel.updateDtsLine(index, newText)
                            }
                        },
                        onBackspaceAtStart = {
                            if (index > 0) {
                                val prevText = lines[index - 1]
                                val merged = prevText + lineText
                                viewModel.replaceDtsLines(
                                    startLine = index - 1,
                                    removeCount = 2,
                                    newLines = listOf(merged),
                                    focusLineAfterUndo = index,
                                    focusLineAfterRedo = index - 1
                                )
                                editingLineIndex = index - 1
                                pendingCursorColumn = prevText.length
                                focusTrigger++
                            }
                        }
                    )
                }
            }
        }
    }
}
