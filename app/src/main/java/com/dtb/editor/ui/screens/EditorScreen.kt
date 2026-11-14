package com.dtb.editor.ui.screens

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dtb.editor.R
import com.dtb.editor.fdt.parser.DtsSyntaxAligner
import com.dtb.editor.ui.editor.DtsCodeEditorView
import com.dtb.editor.viewmodel.DtbViewModel
import com.dtb.editor.viewmodel.OpenFileState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EditorScreen(
    fileState: OpenFileState,
    viewModel: DtbViewModel
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var editorViewRef by remember { mutableStateOf<DtsCodeEditorView?>(null) }
    var canUndo by remember(fileState.bufferUndoManager) { mutableStateOf(fileState.bufferUndoManager.canUndo) }
    var canRedo by remember(fileState.bufferUndoManager) { mutableStateOf(fileState.bufferUndoManager.canRedo) }
    var currentCursorLine by remember { mutableIntStateOf(1) }
    var totalLines by remember(fileState.dtsRevision, fileState.dtsLines.size) {
        mutableIntStateOf(fileState.dtsLines.size.coerceAtLeast(1))
    }
    var hasEditorFocus by remember { mutableStateOf(false) }
    var textMutationTick by remember { mutableIntStateOf(0) }

    // Cached snapshot for zero-allocation incremental search while typing in search bar
    var cachedSearchSnapshot by remember(fileState.dtsRevision) { mutableStateOf<String?>(fileState.fullDtsText()) }
    var cachedSnapshotTick by remember(fileState.dtsRevision) { mutableIntStateOf(0) }

    var wordWrap by remember { mutableStateOf(false) }

    // Search state
    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var searchMatches by remember { mutableStateOf<List<DtsOffsetMatch>>(emptyList()) }
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

    val gutterBgColor = colorScheme.surfaceContainerLowest.toArgb()
    val gutterDividerColor = colorScheme.outlineVariant.copy(alpha = 0.4f).toArgb()
    val gutterTextColor = colorScheme.outline.toArgb()
    val gutterActiveTextColor = colorScheme.primary.toArgb()
    val activeLineHighlightColor = colorScheme.primary.copy(alpha = 0.08f).toArgb()

    // Connect ViewModel to the live virtualized buffer lazily on save / flash / tab switch
    DisposableEffect(viewModel) {
        viewModel.liveDtsTextProvider = {
            editorViewRef?.buffer?.getFullText() ?: viewModel.openFileState.value?.fullDtsText().orEmpty()
        }
        onDispose {
            editorViewRef?.buffer?.getFullText()?.let { latest ->
                viewModel.commitLiveDtsText(latest)
            }
            viewModel.liveDtsTextProvider = null
        }
    }

    // Async background search without blocking UI thread
    LaunchedEffect(isSearchVisible, searchQuery, matchCase, textMutationTick, fileState.dtsRevision) {
        if (!isSearchVisible || searchQuery.isEmpty()) {
            searchMatches = emptyList()
            activeMatchIndex = 0
            lastScrolledQueryKey = null
            withContext(Dispatchers.Main.immediate) {
                editorViewRef?.setSearchHighlights(emptyList(), -1)
            }
            return@LaunchedEffect
        }

        delay(45)
        val liveText = withContext(Dispatchers.Main.immediate) {
            if (cachedSearchSnapshot != null && cachedSnapshotTick == textMutationTick) {
                cachedSearchSnapshot!!
            } else {
                val fresh = editorViewRef?.buffer?.getFullText() ?: fileState.fullDtsText()
                cachedSearchSnapshot = fresh
                cachedSnapshotTick = textMutationTick
                fresh
            }
        }
        val found = withContext(Dispatchers.Default) {
            DtsOffsetSearchEngine.findAllMatches(liveText, searchQuery, matchCase)
        }

        withContext(Dispatchers.Main.immediate) {
            searchMatches = found
            val currentQueryKey = searchQuery to matchCase
            val shouldJumpToMatch = lastScrolledQueryKey != currentQueryKey
            if (found.isNotEmpty()) {
                val nextIdx = if (shouldJumpToMatch) {
                    lastScrolledQueryKey = currentQueryKey
                    val editor = editorViewRef
                    val currentTopLine = if (editor != null && editor.lineCountCompat > 0) {
                        editor.firstVisibleLogicalLine()
                    } else {
                        0
                    }
                    found.indexOfFirst { it.lineIndex >= currentTopLine }.let { if (it >= 0) it else 0 }
                } else {
                    activeMatchIndex.coerceIn(0, found.lastIndex)
                }
                activeMatchIndex = nextIdx
                editorViewRef?.setSearchHighlights(found, nextIdx)
                if (shouldJumpToMatch) {
                    editorViewRef?.scrollToMatch(found[nextIdx])
                }
            } else {
                activeMatchIndex = 0
                editorViewRef?.setSearchHighlights(emptyList(), -1)
            }
        }
    }

    LaunchedEffect(isSearchVisible) {
        if (isSearchVisible) {
            delay(30)
            try {
                searchFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    if (showGoToLineDialog) {
        GoToLineDialog(
            totalLines = totalLines,
            onDismiss = { showGoToLineDialog = false },
            onJump = { lineOneBased ->
                showGoToLineDialog = false
                editorViewRef?.jumpToLogicalLine(lineOneBased)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        // Material 3 Editor Toolbar
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
                    onClick = { editorViewRef?.performUndo() },
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
                    onClick = { editorViewRef?.performRedo() },
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
                            editorViewRef?.setSearchHighlights(emptyList(), -1)
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
                    onCheckedChange = { wrap ->
                        wordWrap = wrap
                        editorViewRef?.setWordWrapEnabled(wrap)
                    },
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

                IconButton(
                    onClick = {
                        val editor = editorViewRef ?: return@IconButton
                        val currentSnapshot = editor.buffer.getFullText()
                        coroutineScope.launch {
                            val aligned = withContext(Dispatchers.Default) {
                                DtsSyntaxAligner.align(currentSnapshot)
                            }
                            withContext(Dispatchers.Main.immediate) {
                                val changed = editor.applyAlignedText(aligned)
                                viewModel.showMessage(
                                    context.getString(
                                        if (changed) R.string.dts_syntax_formatted
                                        else R.string.dts_syntax_already_aligned
                                    )
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("dts_format_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.FormatIndentIncrease,
                        contentDescription = stringResource(R.string.dts_format_syntax),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        val labelText = if (hasEditorFocus) {
                            "Ln $currentCursorLine / $totalLines"
                        } else {
                            stringResource(R.string.editor_info, totalLines)
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

        // Expandable Fast Search Bar (0ms editor resize overhead)
        AnimatedVisibility(
            visible = isSearchVisible,
            enter = expandVertically(tween(120)) + fadeIn(tween(120)),
            exit = shrinkVertically(tween(100)) + fadeOut(tween(100))
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
                        val prev = if (activeMatchIndex - 1 < 0) searchMatches.lastIndex else activeMatchIndex - 1
                        activeMatchIndex = prev
                        editorViewRef?.setSearchHighlights(searchMatches, prev)
                        editorViewRef?.scrollToMatch(searchMatches[prev])
                    }
                },
                onNextMatch = {
                    if (searchMatches.isNotEmpty()) {
                        val next = (activeMatchIndex + 1) % searchMatches.size
                        activeMatchIndex = next
                        editorViewRef?.setSearchHighlights(searchMatches, next)
                        editorViewRef?.scrollToMatch(searchMatches[next])
                    }
                },
                onClose = {
                    isSearchVisible = false
                    searchQuery = ""
                    editorViewRef?.setSearchHighlights(emptyList(), -1)
                    focusManager.clearFocus()
                }
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Virtualized O(visible lines) Full-Document Code Editor
        AndroidView(
            factory = { viewCtx ->
                DtsCodeEditorView(viewCtx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setOnFocusChangeListener { _, focused ->
                        hasEditorFocus = focused
                    }
                    onDirtyCallback = { viewModel.markDtsModified() }
                    onUndoRedoChanged = { u, r ->
                        canUndo = u
                        canRedo = r
                    }
                    onCursorPositionChanged = { cur, tot ->
                        currentCursorLine = cur
                        totalLines = tot
                    }
                    onTextMutated = { textMutationTick++ }
                    applyThemeColors(
                        palette = syntaxPalette,
                        gutterBgColor = gutterBgColor,
                        gutterDividerColor = gutterDividerColor,
                        gutterTextColor = gutterTextColor,
                        gutterActiveTextColor = gutterActiveTextColor,
                        activeLineHighlightColor = activeLineHighlightColor
                    )
                    setDocumentText(
                        fullText = fileState.fullDtsText(),
                        revision = fileState.dtsRevision,
                        undoManager = fileState.bufferUndoManager
                    )
                    setWordWrapEnabled(wordWrap)
                    editorViewRef = this
                }
            },
            update = { view ->
                editorViewRef = view
                if (view.appliedRevision != fileState.dtsRevision ||
                    view.undoManager !== fileState.bufferUndoManager
                ) {
                    view.setDocumentText(
                        fullText = fileState.fullDtsText(),
                        revision = fileState.dtsRevision,
                        undoManager = fileState.bufferUndoManager
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("dts_native_editor")
        )
    }
}
