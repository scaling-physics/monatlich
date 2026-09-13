package com.monatlich.ui.categories

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.monatlich.domain.model.Category
import com.monatlich.ui.theme.MonatlichTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Renders the stateless [CategoriesScreen] with fixed state; no ViewModel or Hilt involved. */
class CategoriesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val categories = listOf(
        Category(id = 1, name = "Groceries", icon = "ShoppingCart", color = 0xFF2E7D32, sortOrder = 0),
        Category(id = 2, name = "Rent", icon = "Home", color = 0xFF6B1F2A, sortOrder = 1),
        Category(id = 3, name = "Transport", icon = "DirectionsBus", color = 0xFF1565C0, sortOrder = 2),
    )
    private val archived = listOf(
        Category(id = 4, name = "Old hobby", icon = "MusicNote", color = 0xFF7B1FA2, sortOrder = 3, archived = true),
    )

    @Test
    fun rendersActiveRowsAndCollapsedArchivedSection() {
        composeRule.setContent {
            MonatlichTheme {
                CategoriesScreen(
                    state = CategoriesUiState(active = categories, archived = archived, isLoading = false),
                    onEvent = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(CATEGORIES_SCREEN_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Categories").assertIsDisplayed()
        categories.forEach { category ->
            composeRule.onNodeWithTag(categoryRowTag(category.id)).assertIsDisplayed()
            composeRule.onNodeWithText(category.name).assertIsDisplayed()
        }
        composeRule.onNodeWithText("Archived").assertIsDisplayed()
        composeRule.onNodeWithText("1 archived").assertIsDisplayed()
        composeRule.onNodeWithText("Old hobby").assertDoesNotExist()
    }

    @Test
    fun fabTapOpensNewCategorySheet() {
        val events = mutableListOf<CategoriesEvent>()
        composeRule.setContent {
            MonatlichTheme {
                // Drive the sheet from the events, as the ViewModel would.
                var state by remember {
                    mutableStateOf(CategoriesUiState(active = categories, isLoading = false))
                }
                CategoriesScreen(
                    state = state,
                    onEvent = { event ->
                        events += event
                        if (event == CategoriesEvent.NewCategoryClicked) {
                            state = state.copy(editor = CategoryEditorState())
                        }
                    },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(CATEGORY_SHEET_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(NEW_CATEGORY_FAB_TAG).performClick()
        composeRule.waitForIdle()

        assertTrue(events.contains(CategoriesEvent.NewCategoryClicked))
        composeRule.onNodeWithTag(CATEGORY_SHEET_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("New category").assertIsDisplayed()
        composeRule.onNodeWithTag(CATEGORY_NAME_FIELD_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(CATEGORY_SAVE_TAG).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("ShoppingCart").assertIsDisplayed()
    }

    @Test
    fun typingANameEmitsNameChanged() {
        val events = mutableListOf<CategoriesEvent>()
        composeRule.setContent {
            MonatlichTheme {
                CategoriesScreen(
                    state = CategoriesUiState(active = categories, isLoading = false, editor = CategoryEditorState()),
                    onEvent = { events += it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(CATEGORY_NAME_FIELD_TAG).performTextInput("Coffee")
        composeRule.waitForIdle()

        assertTrue(events.any { it is CategoriesEvent.NameChanged && it.name == "Coffee" })
    }
}
