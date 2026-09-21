package de.workflow42.meinenoten.ui

import androidx.navigation3.runtime.NavKey

/**
 * Handles navigation events (forward and back) by updating the navigation state.
 */
class Navigator(val state: NavigationState) {
    fun navigate(route: NavKey) {
        if (route in state.backStacks.keys) {
            // Top level route: always show its root screen. Keeping the previous detail
            // open would make the menu look like it had not reacted at all.
            state.backStacks[route]?.let { stack ->
                while (stack.size > 1) {
                    stack.removeLastOrNull()
                }
            }
            state.topLevelRoute = route
        } else {
            state.backStacks[state.topLevelRoute]?.add(route)
        }
    }

    /**
     * Swaps the topmost entry for [route] instead of stacking on top of it.
     *
     * Used when paging from one song to the next inside a setlist: stacking would make
     * the back gesture walk through every song visited, when it should return to the
     * setlist in one step.
     */
    fun replace(route: NavKey) {
        val currentStack = state.backStacks[state.topLevelRoute] ?: return
        if (currentStack.isEmpty()) {
            currentStack.add(route)
        } else {
            currentStack[currentStack.lastIndex] = route
        }
    }

    fun goBack() {
        val currentStack = state.backStacks[state.topLevelRoute]
            ?: error("Stack for ${state.topLevelRoute} not found")
        
        // If we're at the base of the current route, go back to the start route stack.
        if (currentStack.size <= 1) {
            if (state.topLevelRoute != state.startRoute) {
                state.topLevelRoute = state.startRoute
            }
        } else {
            currentStack.removeLastOrNull()
        }
    }
}
