package com.hereliesaz.conveyance.h2g2.settings

/**
 * One choice in [H2g2AiSettingsScreen]'s provider picker: who answers the AI chat. [defaultModel]
 * and [defaultBaseUrl] are only defaults: the user can change both in AI settings, so a retired
 * model id never needs an app update.
 *
 * The list is the host's to supply; [defaultAiProviders] is HG2Gui's own set.
 *
 * @property id the stable key the host stores the choice under.
 * @property label what the picker chip says.
 * @property defaultModel the model asked when the model field is left blank; "" when the provider
 *   has no sensible default (a custom endpoint).
 * @property defaultBaseUrl the endpoint used when the endpoint field is left blank, or null.
 * @property needsKey false for a local server that takes no key (Ollama): the key row is hidden.
 * @property editableEndpoint true when the endpoint URL is the user's to set (a local or
 *   self-hosted server): the endpoint row is shown.
 */
data class AiProviderOption(
    val id: String,
    val label: String,
    val defaultModel: String,
    val defaultBaseUrl: String?,
    val needsKey: Boolean,
    val editableEndpoint: Boolean
)

/** HG2Gui's own providers, by name, for hosts that want the same set (or one of them). */
object AiProviders {
    /** The original backend, and the default choice. */
    val Anthropic = AiProviderOption("anthropic", "Anthropic", "claude-opus-5-5", null, needsKey = true, editableEndpoint = false)

    /** OpenAI's hosted API. */
    val OpenAi = AiProviderOption("openai", "OpenAI", "gpt-5", "https://api.openai.com/v1", needsKey = true, editableEndpoint = false)

    /** Google's Gemini API. */
    val Gemini = AiProviderOption(
        "gemini", "Gemini", "gemini-2.5-pro",
        "https://generativelanguage.googleapis.com/v1beta", needsKey = true, editableEndpoint = false
    )

    /** A local Ollama server: no key, and the endpoint is the user's to set. */
    val Ollama = AiProviderOption("ollama", "Ollama (local)", "llama3.2", "http://127.0.0.1:11434/v1", needsKey = false, editableEndpoint = true)

    /** Any OpenAI-compatible server: no default model or endpoint. */
    val Custom = AiProviderOption("custom", "Custom (OpenAI-compatible)", "", null, needsKey = true, editableEndpoint = true)

    /** All five, in picker order. */
    val all: List<AiProviderOption> = listOf(Anthropic, OpenAi, Gemini, Ollama, Custom)

    /** The provider in [options] stored as [id]; anything unknown (or null) is the first option. */
    fun fromId(id: String?, options: List<AiProviderOption> = all): AiProviderOption =
        options.firstOrNull { it.id == id } ?: options.first()
}

/** HG2Gui's provider set, the default for [AiSettingsState.providers]. */
val defaultAiProviders: List<AiProviderOption> get() = AiProviders.all

/** The provider, model and endpoint a request goes to. Blank [model]/[baseUrl] mean "the provider's default". */
data class AiConfig(
    val provider: AiProviderOption = AiProviders.Anthropic,
    val model: String = "",
    val baseUrl: String = ""
) {
    /** [model], or the provider's default when it is blank. */
    val effectiveModel: String get() = model.trim().ifEmpty { provider.defaultModel }

    /** [baseUrl], or the provider's default when it is blank; null when neither is set. */
    val effectiveBaseUrl: String?
        get() = baseUrl.trim().ifEmpty { provider.defaultBaseUrl.orEmpty() }.ifEmpty { null }
}
