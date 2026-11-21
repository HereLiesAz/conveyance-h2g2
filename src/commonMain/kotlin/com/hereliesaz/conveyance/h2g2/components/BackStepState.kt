package com.hereliesaz.conveyance.h2g2.components


import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Lets a screen that keeps its own internal navigation state (Files' Storage/Search/select-mode/
 * drill depth, the Guide's index/entry drill-down, the path picker's folder depth) report whether
 * it has one internal step left to unwind before the host activity's own system-back handling
 * treats the gesture as "close this screen entirely."
 *
 * The screen updates [canStepBack]/[stepBack] every recomposition (typically via a top-level
 * `SideEffect`) to reflect its current internal state. The host owns the one back handler, so two
 * never compete, and checks [canStepBack] first:
 *
 * ~~~kotlin
 * val backStep = remember { BackStepState() }
 * BackHandler { if (backStep.canStepBack) backStep.stepBack() else closeScreen() }
 * ~~~
 */
class BackStepState {
    var canStepBack: Boolean by mutableStateOf(false)
    var stepBack: () -> Unit by mutableStateOf({})

    /** Called when the owning screen leaves composition, so a stale "I can step back" from a
     *  screen that is gone can't swallow the next system-back press. */
    fun reset() {
        canStepBack = false
        stepBack = {}
    }
}
