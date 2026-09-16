package com.monatlich.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monatlich.domain.model.Category
import com.monatlich.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Manages the category list (order, archive state) and the create/edit sheet.
 *
 * The repository is the source of truth for the list; the sheet draft lives only here. Reorders
 * are applied optimistically so the list does not snap back between drop and the Room emission.
 */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repository: CategoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAll().collect { all ->
                _uiState.update { state ->
                    state.copy(
                        active = all.filter { !it.archived },
                        archived = all.filter { it.archived },
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun onEvent(event: CategoriesEvent) {
        when (event) {
            CategoriesEvent.NewCategoryClicked -> openEditor(CategoryEditorState())
            is CategoriesEvent.CategoryClicked -> openEditorFor(event.id)
            CategoriesEvent.EditorDismissed -> _uiState.update { it.copy(editor = null) }
            is CategoriesEvent.NameChanged -> updateEditor { it.copy(name = event.name, nameError = null) }
            is CategoriesEvent.IconSelected -> updateEditor { it.copy(icon = event.icon) }
            is CategoriesEvent.ColorSelected -> updateEditor { it.copy(color = event.color) }
            is CategoriesEvent.RolloverToggled -> updateEditor { it.copy(rolloverEnabled = event.enabled) }
            CategoriesEvent.SaveClicked -> save()
            is CategoriesEvent.Archive -> setArchived(event.id, archived = true)
            is CategoriesEvent.Restore -> setArchived(event.id, archived = false)
            is CategoriesEvent.Reordered -> reorder(event.orderedIds)
            CategoriesEvent.ToggleArchived ->
                _uiState.update { it.copy(isArchivedExpanded = !it.isArchivedExpanded) }
        }
    }

    private fun openEditor(editor: CategoryEditorState) = _uiState.update { it.copy(editor = editor) }

    private fun openEditorFor(id: Long) {
        val state = _uiState.value
        val category = (state.active + state.archived).firstOrNull { it.id == id } ?: return
        openEditor(
            CategoryEditorState(
                id = category.id,
                name = category.name,
                icon = category.icon,
                color = category.color,
                rolloverEnabled = category.rolloverEnabled,
            ),
        )
    }

    private inline fun updateEditor(transform: (CategoryEditorState) -> CategoryEditorState) =
        _uiState.update { state -> state.copy(editor = state.editor?.let(transform)) }

    private fun save() {
        val state = _uiState.value
        val editor = state.editor ?: return
        val name = editor.name.trim()
        val error = when {
            name.isEmpty() -> NameError.Blank
            (state.active + state.archived).any { it.id != editor.id && it.name.equals(name, ignoreCase = true) } ->
                NameError.Duplicate
            else -> null
        }
        if (error != null) {
            updateEditor { it.copy(nameError = error) }
            return
        }
        viewModelScope.launch {
            if (editor.id == null) {
                repository.add(
                    Category(name = name, icon = editor.icon, color = editor.color, rolloverEnabled = editor.rolloverEnabled),
                )
            } else {
                val existing = repository.get(editor.id) ?: return@launch
                repository.update(
                    existing.copy(name = name, icon = editor.icon, color = editor.color, rolloverEnabled = editor.rolloverEnabled),
                )
            }
            _uiState.update { it.copy(editor = null) }
        }
    }

    private fun setArchived(id: Long, archived: Boolean) {
        viewModelScope.launch { repository.setArchived(id, archived) }
    }

    private fun reorder(orderedIds: List<Long>) {
        val state = _uiState.value
        val byId = state.active.associateBy { it.id }
        val reordered = orderedIds.mapNotNull { byId[it] }
        if (reordered.size != state.active.size || reordered.map { it.id } == state.active.map { it.id }) return

        _uiState.update { it.copy(active = reordered) }
        val ids = reordered.map { it.id } + state.archived.map { it.id }
        viewModelScope.launch { repository.reorder(ids) }
    }
}
