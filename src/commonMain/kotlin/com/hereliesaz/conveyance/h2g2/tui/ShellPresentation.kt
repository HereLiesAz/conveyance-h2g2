package com.hereliesaz.conveyance.h2g2.tui

/** The shell a session is running, when known. Its lower-cased name is the first status tag. */
enum class ShellFamily {
    BASH,
    ZSH,
    FISH,
    SH,
    UNKNOWN
}

/**
 * What [H2g2StructuredShellStatus] shows: the status that shell prompt frameworks normally encode
 * into decorated prompt text, as plain fields. Every field is advisory; one that could not be
 * inferred confidently stays null and its tag is simply not drawn. The host derives this from its
 * own shell session (prompt parsing, shell integration) and passes it in.
 */
data class ShellPresentation(
    val shell: ShellFamily = ShellFamily.UNKNOWN,
    val cwd: String? = null,
    val user: String? = null,
    val host: String? = null,
    val gitBranch: String? = null,
    val gitDirty: Boolean = false,
    val lastExitCode: Int? = null
) {
    /** Whether there is anything beyond the shell's name to show. */
    val hasStructuredStatus: Boolean
        get() = cwd != null || gitBranch != null || user != null || host != null || lastExitCode != null
}
