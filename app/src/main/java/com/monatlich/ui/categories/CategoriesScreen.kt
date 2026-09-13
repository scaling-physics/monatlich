package com.monatlich.ui.categories

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monatlich.R
import com.monatlich.domain.model.Category
import com.monatlich.ui.common.CategoryBadge
import com.monatlich.ui.common.categoryIcon
import com.monatlich.ui.common.LocalMotion
import com.monatlich.ui.common.Motion
import com.monatlich.ui.theme.MonatlichTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

const val CATEGORIES_SCREEN_TAG = "categories_screen"
const val CATEGORIES_LIST_TAG = "categories_list"
const val NEW_CATEGORY_FAB_TAG = "new_category_fab"
const val CATEGORY_SHEET_TAG = "category_sheet"
const val CATEGORY_NAME_FIELD_TAG = "category_name_field"
const val CATEGORY_SAVE_TAG = "category_save"

/** Test tag of the row for the category with [id]. */
fun categoryRowTag(id: Long): String = "category_row_$id"

/** Hilt entry point for the category manager (drill-down from Settings). */
@Composable
fun CategoriesRoute(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CategoriesScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    state: CategoriesUiState,
    onEvent: (CategoriesEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.testTag(CATEGORIES_SCREEN_TAG),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.categories_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEvent(CategoriesEvent.NewCategoryClicked) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag(NEW_CATEGORY_FAB_TAG),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.categories_new))
            }
        },
    ) { innerPadding ->
        CategoryList(state = state, onEvent = onEvent, contentPadding = innerPadding)
    }

    state.editor?.let { editor ->
        CategoryEditorSheet(editor = editor, onEvent = onEvent)
    }
}

// --- List ----------------------------------------------------------------------------------------

@Composable
private fun CategoryList(
    state: CategoriesUiState,
    onEvent: (CategoriesEvent) -> Unit,
    contentPadding: PaddingValues,
) {
    val motion = LocalMotion.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reorder = remember(listState, scope) {
        ReorderState(listState, scope, motion) { ids -> onEvent(CategoriesEvent.Reordered(ids)) }
    }
    LaunchedEffect(state.active) { reorder.sync(state.active) }

    if (!state.isLoading && state.active.isEmpty() && state.archived.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.categories_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            // Leave room for the FAB above the last row.
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        modifier = Modifier
            .fillMaxSize()
            .testTag(CATEGORIES_LIST_TAG),
    ) {
        items(reorder.items, key = { it.id }) { category ->
            val dragging = reorder.draggingId == category.id
            ActiveCategoryRow(
                category = category,
                dragging = dragging,
                dragOffset = if (dragging) reorder.dragOffset else 0f,
                onClick = { onEvent(CategoriesEvent.CategoryClicked(category.id)) },
                onArchive = { onEvent(CategoriesEvent.Archive(category.id)) },
                reorder = reorder,
                modifier = Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .animateItem(placementSpec = if (dragging) null else motion.layout()),
            )
        }

        if (state.archived.isNotEmpty()) {
            item(key = "archived_header") {
                ArchivedHeader(
                    count = state.archived.size,
                    expanded = state.isArchivedExpanded,
                    onToggle = { onEvent(CategoriesEvent.ToggleArchived) },
                    modifier = Modifier.animateItem(),
                )
            }
            items(state.archived, key = { "archived_${it.id}" }) { category ->
                AnimatedVisibility(
                    visible = state.isArchivedExpanded,
                    enter = fadeIn(motion.layout()) + expandVertically(motion.layout()),
                    exit = fadeOut(motion.feedback()) + shrinkVertically(motion.layout()),
                    modifier = Modifier.animateItem(),
                ) {
                    ArchivedCategoryRow(
                        category = category,
                        onClick = { onEvent(CategoriesEvent.CategoryClicked(category.id)) },
                        onRestore = { onEvent(CategoriesEvent.Restore(category.id)) },
                    )
                }
            }
        }
    }
}

/**
 * Long-press-drag reorder bookkeeping for the active list. Keeps its own copy of the rows so the
 * order can change under the finger; the repository is only told on drop.
 */
@Stable
private class ReorderState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val motion: Motion,
    private val onDrop: (List<Long>) -> Unit,
) {
    var items by mutableStateOf<List<Category>>(emptyList())
        private set
    var draggingId by mutableStateOf<Long?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set

    fun sync(source: List<Category>) {
        if (draggingId == null) items = source
    }

    fun onDragStart(id: Long) {
        draggingId = id
        dragOffset = 0f
    }

    fun onDrag(deltaY: Float) {
        val id = draggingId ?: return
        dragOffset += deltaY
        val visible = listState.layoutInfo.visibleItemsInfo
        val dragged = visible.firstOrNull { it.key == id } ?: return
        val centre = dragged.offset + dragOffset + dragged.size / 2f
        val target = visible.firstOrNull { info ->
            info.key is Long && info.key != id && centre >= info.offset && centre < info.offset + info.size
        } ?: return
        val from = items.indexOfFirst { it.id == id }
        val to = items.indexOfFirst { it.id == target.key }
        if (from < 0 || to < 0 || from == to) return
        items = items.toMutableList().apply { add(to, removeAt(from)) }
        // The dragged row's layout slot moves by one row; compensate so it stays under the finger.
        dragOffset += if (to > from) -target.size.toFloat() else target.size.toFloat()
    }

    fun onDragEnd() {
        if (draggingId == null) return
        onDrop(items.map { it.id })
        settle()
    }

    fun onDragCancel() = settle()

    private fun settle() {
        val start = dragOffset
        scope.launch {
            Animatable(start).animateTo(0f, motion.feedback()) { dragOffset = value }
            dragOffset = 0f
            draggingId = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveCategoryRow(
    category: Category,
    dragging: Boolean,
    dragOffset: Float,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    reorder: ReorderState,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotion.current
    val elevation by animateDpAsState(if (dragging) 6.dp else 0.dp, motion.feedback(), label = "rowElevation")
    val scale by animateFloatAsState(if (dragging) 1.02f else 1f, motion.feedback(), label = "rowScale")
    val archiveLabel = stringResource(R.string.categories_archive)
    val dismissState = rememberSwipeToDismissBoxState()

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        gesturesEnabled = !dragging,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onArchive() },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = archiveLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Filled.Archive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = dragOffset
                scaleX = scale
                scaleY = scale
            },
    ) {
        Surface(
            tonalElevation = elevation,
            shadowElevation = elevation,
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(categoryRowTag(category.id)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClick = onClick)
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            ) {
                CategoryBadge(icon = category.icon, color = category.color)
                Spacer(Modifier.width(16.dp))
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = stringResource(R.string.categories_drag_handle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(48.dp)
                        .pointerInput(category.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { reorder.onDragStart(category.id) },
                                onDrag = { change, delta ->
                                    change.consume()
                                    reorder.onDrag(delta.y)
                                },
                                onDragEnd = { reorder.onDragEnd() },
                                onDragCancel = { reorder.onDragCancel() },
                            )
                        }
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun ArchivedHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalMotion.current
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, motion.layout(), label = "chevron")
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.categories_archived_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = pluralStringResource(R.plurals.categories_archived_count, count, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}

@Composable
private fun ArchivedCategoryRow(
    category: Category,
    onClick: () -> Unit,
    onRestore: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(categoryRowTag(category.id))
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
    ) {
        CategoryBadge(icon = category.icon, color = category.color, muted = true)
        Spacer(Modifier.width(16.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRestore) {
            Icon(Icons.Filled.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.categories_restore))
        }
    }
}

// --- Editor sheet --------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryEditorSheet(
    editor: CategoryEditorState,
    onEvent: (CategoriesEvent) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboard = LocalSoftwareKeyboardController.current
    ModalBottomSheet(
        onDismissRequest = { onEvent(CategoriesEvent.EditorDismissed) },
        sheetState = sheetState,
        modifier = Modifier.testTag(CATEGORY_SHEET_TAG),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(icon = editor.icon, color = editor.color, size = 48.dp)
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(if (editor.isNew) R.string.categories_new else R.string.categories_edit),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Spacer(Modifier.height(20.dp))

            val nameFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                nameFocus.requestFocus()
                keyboard?.show()
            }
            OutlinedTextField(
                value = editor.name,
                onValueChange = { onEvent(CategoriesEvent.NameChanged(it)) },
                label = { Text(stringResource(R.string.categories_name_label)) },
                singleLine = true,
                isError = editor.nameError != null,
                supportingText = editor.nameError?.let { error ->
                    {
                        Text(
                            stringResource(
                                when (error) {
                                    NameError.Blank -> R.string.categories_error_blank
                                    NameError.Duplicate -> R.string.categories_error_duplicate
                                },
                            ),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    keyboard?.hide()
                    onEvent(CategoriesEvent.SaveClicked)
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFocus)
                    .testTag(CATEGORY_NAME_FIELD_TAG),
            )
            Spacer(Modifier.height(20.dp))

            SectionLabel(stringResource(R.string.categories_icon_label))
            IconPicker(selected = editor.icon, onSelect = { onEvent(CategoriesEvent.IconSelected(it)) })
            Spacer(Modifier.height(20.dp))

            SectionLabel(stringResource(R.string.categories_color_label))
            ColorPicker(selected = editor.color, onSelect = { onEvent(CategoriesEvent.ColorSelected(it)) })
            Spacer(Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onEvent(CategoriesEvent.EditorDismissed) }) {
                    Text(stringResource(R.string.categories_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        keyboard?.hide()
                        onEvent(CategoriesEvent.SaveClicked)
                    },
                    enabled = editor.canSave,
                    modifier = Modifier.testTag(CATEGORY_SAVE_TAG),
                ) {
                    Text(stringResource(R.string.categories_save))
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

private const val PICKER_COLUMNS = 6

@Composable
private fun IconPicker(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val motion = LocalMotion.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CategoryIconNames.all.chunked(PICKER_COLUMNS).forEach { rowNames ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                rowNames.forEach { name ->
                    val isSelected = name == selected
                    val background by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        motion.feedback(),
                        label = "iconCellBg",
                    )
                    val tint by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        motion.feedback(),
                        label = "iconCellTint",
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(background)
                            .clickable { onSelect(name) }
                            .semantics { contentDescription = name },
                    ) {
                        Icon(
                            imageVector = categoryIcon(name),
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                // Keep a short last row left-aligned with the grid above.
                repeat(PICKER_COLUMNS - rowNames.size) { Spacer(Modifier.size(44.dp)) }
            }
        }
    }
}

@Composable
private fun ColorPicker(
    selected: Long,
    onSelect: (Long) -> Unit,
) {
    val motion = LocalMotion.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CategoryColors.all.chunked(PICKER_COLUMNS).forEach { rowColors ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                rowColors.forEach { argb ->
                    val isSelected = argb == selected
                    val ring by animateDpAsState(if (isSelected) 3.dp else 0.dp, motion.feedback(), label = "swatchRing")
                    val checkAlpha by animateFloatAsState(if (isSelected) 1f else 0f, motion.feedback(), label = "swatchCheck")
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .border(ring, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .padding(5.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .clickable { onSelect(argb) }
                            .semantics { contentDescription = "color_${argb.toString(16)}" },
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer { alpha = checkAlpha },
                        )
                    }
                }
                repeat(PICKER_COLUMNS - rowColors.size) { Spacer(Modifier.size(44.dp)) }
            }
        }
    }
}

// --- Previews ------------------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun CategoriesScreenPreview() {
    MonatlichTheme {
        CategoriesScreen(
            state = CategoriesUiState(
                active = listOf(
                    Category(1, "Groceries", "ShoppingCart", 0xFF2E7D32, 0),
                    Category(2, "Rent", "Home", 0xFF6B1F2A, 1),
                    Category(3, "Transport", "DirectionsBus", 0xFF1565C0, 2),
                ),
                archived = listOf(Category(4, "Old hobby", "MusicNote", 0xFF7B1FA2, 3, archived = true)),
                isArchivedExpanded = true,
                isLoading = false,
            ),
            onEvent = {},
            onBack = {},
        )
    }
}
