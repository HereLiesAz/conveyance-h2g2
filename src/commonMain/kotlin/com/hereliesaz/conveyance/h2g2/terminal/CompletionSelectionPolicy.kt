package com.hereliesaz.conveyance.h2g2.terminal

/**
 * Decides when HG2Gui can replace an open text operand with a finite native choice surface.
 * A candidate must come from a provider that explicitly guarantees its enumeration is complete.
 * Paths stay with the graphical file picker; arbitrary values and advisory candidates remain
 * editable text.
 */
object CompletionSelectionPolicy {
    /** The candidates whose provider guarantees a complete enumeration. */
    fun enumerableCandidates(candidates: List<CompletionCandidate>): List<CompletionCandidate> =
        candidates.filter(CompletionCandidate::enumerationComplete)

    /** True when nothing is typed yet and a complete enumeration exists, so the UI shows choices only. */
    fun shouldReplaceFreeForm(
        inputText: String,
        candidates: List<CompletionCandidate>
    ): Boolean = inputText.isBlank() && enumerableCandidates(candidates).isNotEmpty()
}
