package com.example.custommusicplayer.ui.device

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel

/** The screens the player can show. Opening one pushes it on a stack; MENU goes back. */
sealed interface Page {
    data object MainMenu : Page
    data object Music : Page
    data object Songs : Page
    data object NowPlaying : Page
}

/**
 * Where you are in the menus, and which row is highlighted on each page.
 *
 * It lives in a ViewModel so it survives screen rotation. mutableStateListOf and
 * mutableStateMapOf are a List and a Map that Compose watches, just like mutableStateOf
 * for a single value: changing them redraws whatever reads them.
 */
class DeviceNavigationViewModel : ViewModel() {

    private val stack = mutableStateListOf<Page>(Page.MainMenu)
    private val selections = mutableStateMapOf<Page, Int>()

    val currentPage: Page get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun open(page: Page) {
        stack.remove(page)  // never have the same page twice in the stack
        stack.add(page)
    }

    /** Back one level (the MENU button). Returns false if already at the main menu. */
    fun back(): Boolean {
        if (!canGoBack) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** The highlighted row on [page] (remembered when you leave and come back, like the iPod). */
    fun selectedIndex(page: Page): Int = selections[page] ?: 0

    fun select(page: Page, index: Int) {
        selections[page] = index
    }

    /** Moves the highlight by [steps], staying inside a list of [itemCount] rows. */
    fun moveSelection(page: Page, steps: Int, itemCount: Int) {
        if (itemCount == 0) return
        selections[page] = (selectedIndex(page) + steps).coerceIn(0, itemCount - 1)
    }
}
