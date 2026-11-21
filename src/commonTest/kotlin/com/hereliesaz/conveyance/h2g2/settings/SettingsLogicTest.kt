package com.hereliesaz.conveyance.h2g2.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Ported from HG2Gui's `SettingsLogicTest`, plus the pure rules the port had to add. */
class SettingsLogicTest {

    @Test
    fun valuesBelowTheFloorAreLiftedToIt() {
        assertEquals(FONT_SCALE_MIN_PERCENT, clampFontScalePercent(50))
        assertEquals(FONT_SCALE_MIN_PERCENT, clampFontScalePercent(FONT_SCALE_MIN_PERCENT - 1))
    }

    @Test
    fun valuesInsideTheRangeAreUntouched() {
        assertEquals(100, clampFontScalePercent(100))
        assertEquals(FONT_SCALE_MIN_PERCENT, clampFontScalePercent(FONT_SCALE_MIN_PERCENT))
        assertEquals(FONT_SCALE_MAX_PERCENT, clampFontScalePercent(FONT_SCALE_MAX_PERCENT))
    }

    @Test
    fun valuesAboveTheCeilingAreCapped() {
        assertEquals(FONT_SCALE_MAX_PERCENT, clampFontScalePercent(500))
    }

    @Test
    fun steppingMovesByTenAndStaysInRange() {
        assertEquals(110, steppedFontScalePercent(100, +1))
        assertEquals(90, steppedFontScalePercent(100, -1))
        assertEquals(FONT_SCALE_MIN_PERCENT, steppedFontScalePercent(FONT_SCALE_MIN_PERCENT, -1))
        assertEquals(FONT_SCALE_MAX_PERCENT, steppedFontScalePercent(FONT_SCALE_MAX_PERCENT, +1))
    }

    @Test
    fun steppingUpFromALegacyValueBelowTheFloorLandsInsideTheRange() {
        // 50% was a legal stored value before the floor; "+" must not walk it through 60, 70, 80.
        assertEquals(FONT_SCALE_MIN_PERCENT, steppedFontScalePercent(50, +1))
    }

    @Test
    fun theDefaultSitsInsideTheAllowedRange() {
        assertTrue(FONT_SCALE_DEFAULT_PERCENT in FONT_SCALE_MIN_PERCENT..FONT_SCALE_MAX_PERCENT)
    }

    @Test
    fun uiScaleFollowsTheSettingAndIsClamped() {
        assertEquals(1f, uiScaleFromPercent(100))
        assertEquals(1.5f, uiScaleFromPercent(150))
        assertEquals(0.85f, uiScaleFromPercent(10))
        assertEquals(2f, uiScaleFromPercent(900))
    }

    @Test
    fun apiKeyDraftIsTrimmedAndBlankBecomesNull() {
        assertEquals("sk-ant-abc", normalizedApiKey("  sk-ant-abc\n"))
        assertNull(normalizedApiKey("   "))
        assertNull(normalizedApiKey(""))
    }

    @Test
    fun unchangedDraftIsNotDirtyEvenWithTrailingWhitespace() {
        assertFalse(isApiKeyDraftDirty("sk-ant-abc", "sk-ant-abc "))
        assertFalse(isApiKeyDraftDirty(null, ""))
        assertFalse(isApiKeyDraftDirty(null, "   "))
    }

    @Test
    fun editedAddedOrClearedKeyIsDirty() {
        assertTrue(isApiKeyDraftDirty("sk-ant-abc", "sk-ant-abd"))
        assertTrue(isApiKeyDraftDirty(null, "sk-ant-abc"))
        assertTrue(isApiKeyDraftDirty("sk-ant-abc", ""))
    }

    @Test
    fun aiSettingsAreCleanWhenNothingChanged_ignoringWhitespace() {
        val saved = AiConfig(AiProviders.OpenAi, model = "gpt-5", baseUrl = "")
        assertFalse(isAiSettingsDirty(saved, "k", AiConfig(AiProviders.OpenAi, " gpt-5 ", "  "), "k "))
        assertFalse(isAiSettingsDirty(AiConfig(), null, AiConfig(), ""))
    }

    @Test
    fun changingProviderModelEndpointOrKeyMakesAiSettingsDirty() {
        val saved = AiConfig(AiProviders.OpenAi, model = "gpt-5", baseUrl = "")
        assertTrue(isAiSettingsDirty(saved, "k", AiConfig(AiProviders.Gemini, "gpt-5", ""), "k"))
        assertTrue(isAiSettingsDirty(saved, "k", AiConfig(AiProviders.OpenAi, "other", ""), "k"))
        assertTrue(isAiSettingsDirty(saved, "k", AiConfig(AiProviders.OpenAi, "gpt-5", "https://x.test/v1"), "k"))
        assertTrue(isAiSettingsDirty(saved, "k", AiConfig(AiProviders.OpenAi, "gpt-5", ""), "k2"))
    }

    // ----- added with the port ---------------------------------------------------------------

    private val savedAi = AiSettingsState(
        config = AiConfig(AiProviders.OpenAi, model = "gpt-5-mini", baseUrl = ""),
        apiKeys = mapOf(AiProviders.OpenAi to "sk-open", AiProviders.Custom to "sk-custom")
    )

    @Test
    fun aFreshDraftIsExactlyWhatIsSaved() {
        val draft = AiSettingsDraft.of(savedAi)
        assertEquals(AiProviders.OpenAi, draft.provider)
        assertEquals("gpt-5-mini", draft.model)
        assertEquals("sk-open", draft.apiKey)
        assertFalse(isAiSettingsDirty(savedAi.config, savedAi.apiKeys[draft.provider], draft.config, draft.apiKey))
    }

    @Test
    fun switchingProviderShowsThatProvidersKeyAndBlankDefaults() {
        val start = AiSettingsDraft.of(savedAi).copy(editingKey = true, showKey = true)
        val custom = start.selecting(AiProviders.Custom, savedAi)
        assertEquals(AiProviders.Custom, custom.provider)
        assertEquals("", custom.model)
        assertEquals("", custom.baseUrl)
        assertEquals("sk-custom", custom.apiKey)
        assertFalse(custom.editingKey)
        assertFalse(custom.showKey)
        assertEquals("", start.selecting(AiProviders.Gemini, savedAi).apiKey)
    }

    @Test
    fun goingBackToTheSavedProviderRestoresItsSavedModel() {
        val away = AiSettingsDraft.of(savedAi).selecting(AiProviders.Gemini, savedAi)
        val back = away.selecting(AiProviders.OpenAi, savedAi)
        assertEquals("gpt-5-mini", back.model)
        assertEquals("sk-open", back.apiKey)
    }

    @Test
    fun selectingTheCurrentProviderChangesNothing() {
        val draft = AiSettingsDraft.of(savedAi).copy(model = "edited", editingKey = true)
        assertSame(draft, draft.selecting(AiProviders.OpenAi, savedAi))
    }

    @Test
    fun aKeyNeverAppearsInAToString() {
        val draft = AiSettingsDraft.of(savedAi)
        assertFalse(draft.toString().contains("sk-open"))
        assertFalse(savedAi.toString().contains("sk-open"))
        assertFalse(savedAi.toString().contains("sk-custom"))
        assertFalse(McpServerState(running = true, port = 8765, token = "tok-secret").toString().contains("tok-secret"))
    }

    @Test
    fun theDefaultProviderSetIsHg2guisFiveWithUniqueIds() {
        assertEquals(listOf("anthropic", "openai", "gemini", "ollama", "custom"), defaultAiProviders.map { it.id })
        assertFalse(AiProviders.Ollama.needsKey)
        assertTrue(AiProviders.Ollama.editableEndpoint)
        assertTrue(AiProviders.Custom.editableEndpoint)
        assertEquals(AiProviders.Anthropic, AiProviders.fromId("nonsense"))
        assertEquals(AiProviders.Gemini, AiProviders.fromId("gemini"))
    }

    @Test
    fun effectiveModelAndEndpointFallBackToTheProvidersDefaults() {
        assertEquals("gpt-5", AiConfig(AiProviders.OpenAi).effectiveModel)
        assertEquals("mine", AiConfig(AiProviders.OpenAi, model = " mine ").effectiveModel)
        assertEquals("https://api.openai.com/v1", AiConfig(AiProviders.OpenAi).effectiveBaseUrl)
        assertNull(AiConfig(AiProviders.Custom).effectiveBaseUrl)
    }

    @Test
    fun theShellPickerShowsAnInstalledShellOrNone() {
        assertEquals(listOf("bash", "fish"), ShellPreference.availableFrom(bash = true, zsh = false, fish = true))
        assertEquals(emptyList(), ShellPreference.availableFrom(bash = false, zsh = false, fish = false))
        assertEquals("zsh", ShellPreference.effectiveSelection("zsh", listOf("bash", "zsh")))
        assertEquals("bash", ShellPreference.effectiveSelection("zsh", listOf("bash")))
        assertNull(ShellPreference.effectiveSelection("bash", emptyList()))
    }

    @Test
    fun relativeTimeReadsAsInHg2gui() {
        assertEquals("just now", relativeTime(-5_000))
        assertEquals("just now", relativeTime(59_000))
        assertEquals("5m ago", relativeTime(5 * 60_000L))
        assertEquals("3h ago", relativeTime(3 * 3_600_000L))
        assertEquals("2d ago", relativeTime(2 * 86_400_000L))
    }
}
