package com.monatlich.ui.categories

import androidx.compose.runtime.Immutable
import com.monatlich.domain.model.Category

/** Single immutable UI state for the category manager. */
@Immutable
data class CategoriesUiState(
    /** Non-archived categories in display order. */
    val active: List<Category> = emptyList(),
    /** Archived categories in display order. */
    val archived: List<Category> = emptyList(),
    val isArchivedExpanded: Boolean = false,
    /** Non-null while the edit sheet is open. */
    val editor: CategoryEditorState? = null,
    val isLoading: Boolean = true,
)

/** Draft of the category being created or edited in the bottom sheet. */
@Immutable
data class CategoryEditorState(
    /** `null` while creating a new category. */
    val id: Long? = null,
    val name: String = "",
    val icon: String = CategoryIconNames.DEFAULT,
    val color: Long = CategoryColors.DEFAULT,
    val nameError: NameError? = null,
) {
    val isNew: Boolean get() = id == null
    val canSave: Boolean get() = name.isNotBlank() && nameError == null
}

enum class NameError { Blank, Duplicate }

sealed interface CategoriesEvent {
    data object NewCategoryClicked : CategoriesEvent
    data class CategoryClicked(val id: Long) : CategoriesEvent
    data object EditorDismissed : CategoriesEvent
    data class NameChanged(val name: String) : CategoriesEvent
    data class IconSelected(val icon: String) : CategoriesEvent
    data class ColorSelected(val color: Long) : CategoriesEvent
    data object SaveClicked : CategoriesEvent
    data class Archive(val id: Long) : CategoriesEvent
    data class Restore(val id: Long) : CategoriesEvent
    /** Active categories in their new order (ids). Archived ones keep trailing positions. */
    data class Reordered(val orderedIds: List<Long>) : CategoriesEvent
    data object ToggleArchived : CategoriesEvent
}
