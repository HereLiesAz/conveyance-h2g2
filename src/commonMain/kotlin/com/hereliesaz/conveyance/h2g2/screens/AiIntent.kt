package com.hereliesaz.conveyance.h2g2.screens

/**
 * What kind of thing a suggested command is, as the host's own deterministic analysis read it.
 * [H2g2AiChatScreen] shows the name (underscores as spaces) in a bubble's authority line.
 */
enum class AiIntentKind { COMMAND, PACKAGE_MUTATION, PACKAGE_POLICY, AUTHORITY_REQUEST, REMOTE_COMMAND }

/**
 * A typed reading of one suggested [command]: its [kind], the [authority] it would run with
 * ("app", "adb", "root", "explicit" in HG2Gui) and the plain-language [consequences] of running it.
 *
 * This is only the model. In HG2Gui an `AiIntentAnalyzer` derived it from the command text ("the
 * model never decides its own authority"); that analysis is app logic and stays with the host,
 * which either sets [AiMessage.intent] itself or passes an `analyzeIntent` function to
 * [H2g2AiChatScreen].
 */
data class AiIntent(
    val kind: AiIntentKind,
    val command: String,
    val authority: String = "app",
    val consequences: List<String> = emptyList()
)
