package com.monatlich.ui.categories

import com.monatlich.domain.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private val seed = listOf(
        Category(id = 1, name = "Groceries", icon = "ShoppingCart", color = 0xFF2E7D32, sortOrder = 0),
        Category(id = 2, name = "Rent", icon = "Home", color = 0xFF6B1F2A, sortOrder = 1),
        Category(id = 3, name = "Transport", icon = "DirectionsBus", color = 0xFF1565C0, sortOrder = 2),
        Category(id = 4, name = "Old", icon = "MoreHoriz", color = 0xFF546E7A, sortOrder = 3, archived = true),
    )

    private lateinit var repository: InMemoryCategoryRepository
    private lateinit var viewModel: CategoriesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = InMemoryCategoryRepository(seed)
        viewModel = CategoriesViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.uiState.value

    @Test
    fun `initial state splits active and archived in sort order`() = runTest {
        assertFalse(state.isLoading)
        assertEquals(listOf("Groceries", "Rent", "Transport"), state.active.map { it.name })
        assertEquals(listOf("Old"), state.archived.map { it.name })
        assertNull(state.editor)
    }

    @Test
    fun `save new category adds it at the end and closes the sheet`() = runTest {
        viewModel.onEvent(CategoriesEvent.NewCategoryClicked)
        val editor = checkNotNull(state.editor)
        assertTrue(editor.isNew)
        assertFalse(editor.canSave)

        viewModel.onEvent(CategoriesEvent.NameChanged("  Coffee "))
        viewModel.onEvent(CategoriesEvent.IconSelected("LocalCafe"))
        viewModel.onEvent(CategoriesEvent.ColorSelected(0xFF6D4C41))
        assertTrue(state.editor!!.canSave)

        viewModel.onEvent(CategoriesEvent.SaveClicked)

        assertNull(state.editor)
        val added = repository.sorted.last()
        assertEquals("Coffee", added.name)
        assertEquals("LocalCafe", added.icon)
        assertEquals(0xFF6D4C41, added.color)
        assertFalse(added.archived)
        assertEquals(listOf("Groceries", "Rent", "Transport", "Coffee"), state.active.map { it.name })
    }

    @Test
    fun `rename keeps id and order`() = runTest {
        viewModel.onEvent(CategoriesEvent.CategoryClicked(2))
        val editor = checkNotNull(state.editor)
        assertEquals(2L, editor.id)
        assertEquals("Rent", editor.name)
        assertEquals("Home", editor.icon)

        viewModel.onEvent(CategoriesEvent.NameChanged("Housing"))
        viewModel.onEvent(CategoriesEvent.SaveClicked)

        assertNull(state.editor)
        val renamed = repository.get(2)!!
        assertEquals("Housing", renamed.name)
        assertEquals(1, renamed.sortOrder)
        assertEquals(listOf("Groceries", "Housing", "Transport"), state.active.map { it.name })
    }

    @Test
    fun `blank name is rejected`() = runTest {
        viewModel.onEvent(CategoriesEvent.NewCategoryClicked)
        viewModel.onEvent(CategoriesEvent.NameChanged("   "))
        viewModel.onEvent(CategoriesEvent.SaveClicked)

        assertEquals(NameError.Blank, state.editor?.nameError)
        assertEquals(4, repository.state.value.size)
    }

    @Test
    fun `duplicate name is rejected case-insensitively, including archived`() = runTest {
        viewModel.onEvent(CategoriesEvent.NewCategoryClicked)
        viewModel.onEvent(CategoriesEvent.NameChanged("groceries"))
        viewModel.onEvent(CategoriesEvent.SaveClicked)
        assertEquals(NameError.Duplicate, state.editor?.nameError)
        assertFalse(state.editor!!.canSave)

        // Typing clears the error; an archived name is still a duplicate.
        viewModel.onEvent(CategoriesEvent.NameChanged("old"))
        assertNull(state.editor?.nameError)
        viewModel.onEvent(CategoriesEvent.SaveClicked)
        assertEquals(NameError.Duplicate, state.editor?.nameError)

        assertEquals(4, repository.state.value.size)
    }

    @Test
    fun `renaming to own name is not a duplicate`() = runTest {
        viewModel.onEvent(CategoriesEvent.CategoryClicked(1))
        viewModel.onEvent(CategoriesEvent.NameChanged("GROCERIES"))
        viewModel.onEvent(CategoriesEvent.SaveClicked)

        assertNull(state.editor)
        assertEquals("GROCERIES", repository.get(1)!!.name)
    }

    @Test
    fun `archive and restore move between sections`() = runTest {
        viewModel.onEvent(CategoriesEvent.Archive(3))
        assertEquals(listOf("Groceries", "Rent"), state.active.map { it.name })
        assertEquals(listOf("Transport", "Old"), state.archived.map { it.name })

        viewModel.onEvent(CategoriesEvent.Restore(4))
        assertEquals(listOf("Groceries", "Rent", "Old"), state.active.map { it.name })
        assertEquals(listOf("Transport"), state.archived.map { it.name })

        viewModel.onEvent(CategoriesEvent.ToggleArchived)
        assertTrue(state.isArchivedExpanded)
    }

    @Test
    fun `reorder persists order with archived trailing`() = runTest {
        viewModel.onEvent(CategoriesEvent.Reordered(listOf(3L, 1L, 2L)))

        assertEquals(listOf("Transport", "Groceries", "Rent"), state.active.map { it.name })
        assertEquals(listOf(listOf(3L, 1L, 2L, 4L)), repository.reorderCalls)
        assertEquals(listOf(3L, 1L, 2L, 4L), repository.sorted.map { it.id })

        // A fresh ViewModel sees the persisted order.
        val fresh = CategoriesViewModel(repository)
        assertEquals(listOf("Transport", "Groceries", "Rent"), fresh.uiState.value.active.map { it.name })
    }

    @Test
    fun `reorder with an incomplete id list is ignored`() = runTest {
        viewModel.onEvent(CategoriesEvent.Reordered(listOf(2L, 1L)))
        assertEquals(listOf("Groceries", "Rent", "Transport"), state.active.map { it.name })
        assertTrue(repository.reorderCalls.isEmpty())
    }

    @Test
    fun `rollover toggle is off by default and persists when saved on`() = runTest {
        viewModel.onEvent(CategoriesEvent.NewCategoryClicked)
        assertFalse(checkNotNull(state.editor).rolloverEnabled)

        viewModel.onEvent(CategoriesEvent.NameChanged("Travel"))
        viewModel.onEvent(CategoriesEvent.RolloverToggled(true))
        assertTrue(state.editor!!.rolloverEnabled)
        viewModel.onEvent(CategoriesEvent.SaveClicked)

        assertTrue(repository.sorted.last().rolloverEnabled)
    }

    @Test
    fun `editing an existing category loads its current rollover setting`() = runTest {
        repository.update(repository.get(2)!!.copy(rolloverEnabled = true))

        viewModel.onEvent(CategoriesEvent.CategoryClicked(2))
        assertTrue(checkNotNull(state.editor).rolloverEnabled)

        viewModel.onEvent(CategoriesEvent.RolloverToggled(false))
        viewModel.onEvent(CategoriesEvent.SaveClicked)

        assertFalse(repository.get(2)!!.rolloverEnabled)
    }

    @Test
    fun `dismissing the sheet discards the draft`() = runTest {
        viewModel.onEvent(CategoriesEvent.NewCategoryClicked)
        viewModel.onEvent(CategoriesEvent.NameChanged("Draft"))
        viewModel.onEvent(CategoriesEvent.EditorDismissed)

        assertNull(state.editor)
        assertEquals(4, repository.state.value.size)
    }
}
