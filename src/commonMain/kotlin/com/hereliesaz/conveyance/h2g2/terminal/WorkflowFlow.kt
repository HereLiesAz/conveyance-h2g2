package com.hereliesaz.conveyance.h2g2.terminal

/** A saved, named command template; `{placeholder}` parts are asked for each time it runs. */
data class Workflow(val name: String, val template: String)

/**
 * Drives the Workflows pill: saving and running named command templates. `{placeholder}`
 * substrings in the template are the only thing that makes a workflow parameterized - no
 * separate arg-declaration step. Reuses SessionUiState's awaitPromptAnswer machinery exactly
 * like SshFlow does; a run always ends by writing the assembled command into the session's
 * input field for the user to review and press Run themselves, never by executing it.
 */
object WorkflowFlow {
    private val placeholderRegex = Regex("\\{([a-zA-Z0-9_]+)\\}")

    /** The distinct `{placeholder}` names in [template], in order of first appearance. */
    fun placeholders(template: String): List<String> =
        placeholderRegex.findAll(template).map { it.groupValues[1] }.distinct().toList()

    /** Substitutes each `{placeholder}` with its value single-quoted as one shell word, so a
     *  value like `x; rm -rf ~` or `$(id)` stays inert literal text. Placeholders therefore
     *  belong bare in templates (`git commit -m {message}`), not inside quotes of their own. */
    fun render(template: String, values: Map<String, String>): String =
        placeholderRegex.replace(template) { match ->
            values[match.groupValues[1]]?.let(::shellQuote) ?: match.value
        }

    internal fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    /** Asks (through [session]'s prompt line) for a name and a template, then hands the result to [save]. */
    suspend fun runNewWorkflowWizard(session: SessionUiState, save: suspend (Workflow) -> Unit) {
        val name = session.awaitPromptAnswer("Workflow name?").trim()
        if (name.isEmpty()) return

        val template = session.awaitPromptAnswer(
            "Command template? Use {name} for a value you'll fill in each time, " +
                "e.g. git commit -m {message}"
        ).trim()
        if (template.isEmpty()) return

        save(Workflow(name, template))
    }

    /** Asks for each placeholder of [workflow], then loads the assembled command into [session]'s input. */
    suspend fun runWorkflowWizard(session: SessionUiState, workflow: Workflow) {
        val values = mutableMapOf<String, String>()
        for (placeholder in placeholders(workflow.template)) {
            values[placeholder] = session.awaitPromptAnswer("$placeholder?").trim()
        }
        session.tokens = emptyList()
        session.inputText = render(workflow.template, values)
    }
}
