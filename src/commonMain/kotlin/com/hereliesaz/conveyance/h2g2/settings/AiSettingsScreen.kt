package com.hereliesaz.conveyance.h2g2.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.hereliesaz.conveyance.h2g2.H2g2
import com.hereliesaz.conveyance.h2g2.H2g2Chip
import com.hereliesaz.conveyance.h2g2.H2g2Surface
import com.hereliesaz.conveyance.h2g2.StatusKind
import com.hereliesaz.conveyance.h2g2.Text
import com.hereliesaz.conveyance.h2g2.components.H2g2ConfirmDialog
import com.hereliesaz.conveyance.h2g2.liveRegionPolite
import com.hereliesaz.conveyance.h2g2.minTouchSize
import com.hereliesaz.conveyance.h2g2.resources.Res
import com.hereliesaz.conveyance.h2g2.resources.aisettings_discard_cancel
import com.hereliesaz.conveyance.h2g2.resources.aisettings_discard_confirm
import com.hereliesaz.conveyance.h2g2.resources.aisettings_discard_message
import com.hereliesaz.conveyance.h2g2.resources.aisettings_discard_title
import com.hereliesaz.conveyance.h2g2.resources.aisettings_endpoint_description
import com.hereliesaz.conveyance.h2g2.resources.aisettings_endpoint_placeholder
import com.hereliesaz.conveyance.h2g2.resources.aisettings_endpoint_title
import com.hereliesaz.conveyance.h2g2.resources.aisettings_field_description
import com.hereliesaz.conveyance.h2g2.resources.aisettings_field_empty
import com.hereliesaz.conveyance.h2g2.resources.aisettings_field_masked
import com.hereliesaz.conveyance.h2g2.resources.aisettings_field_placeholder
import com.hereliesaz.conveyance.h2g2.resources.aisettings_hide_key
import com.hereliesaz.conveyance.h2g2.resources.aisettings_key_description
import com.hereliesaz.conveyance.h2g2.resources.aisettings_key_title
import com.hereliesaz.conveyance.h2g2.resources.aisettings_model_description
import com.hereliesaz.conveyance.h2g2.resources.aisettings_model_title
import com.hereliesaz.conveyance.h2g2.resources.aisettings_provider_description
import com.hereliesaz.conveyance.h2g2.resources.aisettings_provider_title
import com.hereliesaz.conveyance.h2g2.resources.aisettings_save
import com.hereliesaz.conveyance.h2g2.resources.aisettings_save_failed
import com.hereliesaz.conveyance.h2g2.resources.aisettings_show_key
import com.hereliesaz.conveyance.h2g2.resources.aisettings_title
import com.hereliesaz.conveyance.h2g2.statusColor
import org.jetbrains.compose.resources.stringResource

/** The text to store for [draft]: trimmed (a pasted key often carries a trailing newline), or null when blank. */
internal fun normalizedApiKey(draft: String): String? = draft.trim().ifEmpty { null }

/** True when [draft] would change what is [saved], ignoring surrounding whitespace. */
internal fun isApiKeyDraftDirty(saved: String?, draft: String): Boolean = normalizedApiKey(draft) != saved?.trim()?.ifEmpty { null }

/**
 * True when [draft] differs from what is [saved] in provider, model, endpoint or key. [savedKey] is
 * the stored key of the DRAFT's provider (each provider keeps its own).
 */
internal fun isAiSettingsDirty(saved: AiConfig, savedKey: String?, draft: AiConfig, draftKey: String): Boolean =
    saved.provider != draft.provider ||
        saved.model.trim() != draft.model.trim() ||
        saved.baseUrl.trim() != draft.baseUrl.trim() ||
        isApiKeyDraftDirty(savedKey, draftKey)

/**
 * What is saved, as [H2g2AiSettingsScreen] needs to know it. The host reads it from wherever it
 * keeps the choice and the keys (HG2Gui: encrypted preferences) and passes it in; the library
 * never stores, logs or persists any of it. [toString] leaves the keys out for the same reason.
 *
 * @property config the saved provider, model and endpoint.
 * @property apiKeys every provider's stored key (absent or null = none set).
 * @property saveFailed true when the last save could not be stored; shown as an error so the
 *   screen never reads as "saved" for a key that is lost on restart.
 * @property providers the picker's options, in order.
 */
data class AiSettingsState(
    val config: AiConfig = AiConfig(),
    val apiKeys: Map<AiProviderOption, String?> = emptyMap(),
    val saveFailed: Boolean = false,
    val providers: List<AiProviderOption> = defaultAiProviders
) {
    override fun toString(): String =
        "AiSettingsState(config=$config, apiKeys=<${apiKeys.count { !it.value.isNullOrBlank() }} set>, " +
            "saveFailed=$saveFailed, providers=${providers.map { it.id }})"
}

/**
 * The unsaved edit [H2g2AiSettingsScreen] is showing, hoisted so the host decides how long it
 * lives. Start one with [AiSettingsDraft.of], keep it in plain `remember { mutableStateOf(...) }`
 * (not a saveable: [apiKey] is a secret and must not be written to saved instance state) and
 * replace it with whatever the screen's `onDraftChange` hands back. [toString] masks the key.
 *
 * @property provider the provider chip that is selected.
 * @property model the model field's text; blank means the provider's default.
 * @property baseUrl the endpoint field's text; blank means the provider's default.
 * @property apiKey the key field's text for [provider].
 * @property editingKey true once the masked key row has been tapped and become a text field.
 * @property showKey true while the user has chosen SHOW KEY.
 * @property confirmDiscard true while the "discard changes?" dialog is up.
 */
data class AiSettingsDraft(
    val provider: AiProviderOption = AiProviders.Anthropic,
    val model: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val editingKey: Boolean = false,
    val showKey: Boolean = false,
    val confirmDiscard: Boolean = false
) {
    /** The provider, model and endpoint this draft would save. */
    val config: AiConfig get() = AiConfig(provider, model, baseUrl)

    override fun toString(): String =
        "AiSettingsDraft(provider=${provider.id}, model=$model, baseUrl=$baseUrl, " +
            "apiKey=${if (apiKey.isBlank()) "<empty>" else "<hidden>"}, editingKey=$editingKey, " +
            "showKey=$showKey, confirmDiscard=$confirmDiscard)"

    companion object {
        /** The draft a freshly opened screen starts from: exactly what is saved in [state]. */
        fun of(state: AiSettingsState): AiSettingsDraft = AiSettingsDraft(
            provider = state.config.provider,
            model = state.config.model,
            baseUrl = state.config.baseUrl,
            apiKey = state.apiKeys[state.config.provider].orEmpty()
        )
    }
}

/**
 * This draft after the user taps [provider]'s chip. Each provider has its own key, model and
 * endpoint; show the saved ones when going back to the saved provider, blanks (= defaults)
 * otherwise. Tapping the provider already selected changes nothing.
 */
internal fun AiSettingsDraft.selecting(provider: AiProviderOption, saved: AiSettingsState): AiSettingsDraft {
    if (provider == this.provider) return this
    val isSaved = provider == saved.config.provider
    return copy(
        provider = provider,
        model = if (isSaved) saved.config.model else "",
        baseUrl = if (isSaved) saved.config.baseUrl else "",
        apiKey = saved.apiKeys[provider].orEmpty(),
        editingKey = false,
        showKey = false
    )
}

/**
 * The provider picker, model and endpoint fields, and a masked API-key field + Save. Tapping the field unmasks it for editing and focuses it
 * (the keyboard comes up on that one tap); the key stays dotted out unless the user chooses
 * SHOW KEY to check what they pasted. This is the safer of the two secret fields in the app, not
 * a shared idiom with [H2g2McpServerScreen]'s pairing token. Storing the key is the host's job
 * (HG2Gui keeps it encrypted, never in plain text); this screen only edits [draft] and reports it.
 *
 * Leaving with an unsaved edit asks first (the Back chip and the system Back gesture both do);
 * [AiSettingsState.saveFailed] is set by the host when the last [onSave] could not be persisted,
 * so the screen never reads as "saved" for a key that is lost on restart.
 *
 * - [state] is what is saved; [draft] is the edit in progress and [onDraftChange] replaces it.
 * - [onSave] saves the whole choice at once (model and endpoint trimmed, key trimmed or null when
 *   blank), so a key can never land under a different provider than the one shown. After a
 *   successful save, pass back the new [state]; the draft then reads as clean.
 * - [onBack] is called when leaving is settled: straight away with nothing to lose, or after the
 *   discard dialog is confirmed.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun H2g2AiSettingsScreen(
    fullscreen: Boolean,
    state: AiSettingsState,
    draft: AiSettingsDraft,
    onDraftChange: (AiSettingsDraft) -> Unit,
    onSave: (AiConfig, String?) -> Unit,
    onBack: () -> Unit
) {
    val config = state.config
    val apiKeys = state.apiKeys
    val selected = draft.provider
    val editing = draft.editingKey
    val showKey = draft.showKey
    val focusRequester = remember { FocusRequester() }
    val dirty = isAiSettingsDirty(config, apiKeys[selected], draft.config, draft.apiKey)
    val save = { onSave(AiConfig(selected, draft.model.trim(), draft.baseUrl.trim()), normalizedApiKey(draft.apiKey)) }

    // One tap on the masked row both reveals the field and puts the caret in it.
    LaunchedEffect(editing) {
        if (editing) focusRequester.requestFocus()
    }
    // Only intercept the system Back gesture while there is something to lose.
    BackHandler(enabled = dirty) { onDraftChange(draft.copy(confirmDiscard = true)) }

    SettingsPage(
        title = stringResource(Res.string.aisettings_title),
        fullscreen = fullscreen,
        onBack = { if (dirty) onDraftChange(draft.copy(confirmDiscard = true)) else onBack() }
    ) {
        SettingRow(
            title = stringResource(Res.string.aisettings_provider_title),
            description = stringResource(Res.string.aisettings_provider_description)
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.providers.forEach { provider ->
                    H2g2Chip(
                        label = provider.label,
                        selected = provider == selected,
                        onClick = {
                            if (provider != selected) onDraftChange(draft.selecting(provider, state))
                        }
                    )
                }
            }
        }

        SettingRow(
            title = stringResource(Res.string.aisettings_model_title),
            description = stringResource(Res.string.aisettings_model_description, selected.defaultModel.ifEmpty { "-" })
        ) {
            SettingTextField(
                value = draft.model,
                onValueChange = { onDraftChange(draft.copy(model = it)) },
                placeholder = selected.defaultModel.ifEmpty { stringResource(Res.string.aisettings_model_title) },
                label = stringResource(Res.string.aisettings_model_title),
                onDone = save
            )
        }

        if (selected.editableEndpoint) {
            SettingRow(
                title = stringResource(Res.string.aisettings_endpoint_title),
                description = stringResource(Res.string.aisettings_endpoint_description)
            ) {
                SettingTextField(
                    value = draft.baseUrl,
                    onValueChange = { onDraftChange(draft.copy(baseUrl = it)) },
                    placeholder = selected.defaultBaseUrl ?: stringResource(Res.string.aisettings_endpoint_placeholder),
                    label = stringResource(Res.string.aisettings_endpoint_title),
                    keyboardType = KeyboardType.Uri,
                    onDone = save
                )
            }
        }

        if (selected.needsKey) SettingRow(
            title = stringResource(Res.string.aisettings_key_title),
            description = stringResource(Res.string.aisettings_key_description)
        ) {
            val fieldLabel = stringResource(Res.string.aisettings_field_description)
            val empty = draft.apiKey.isBlank()
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = minTouchSize())
                    .clip(H2g2Surface.capsule)
                    .background(H2g2.ink)
                    .clickable(enabled = !editing, role = Role.Button) { onDraftChange(draft.copy(editingKey = true)) }
                    .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val fieldStyle = settingDescriptionStyle().copy(color = H2g2.yellow, fontFamily = FontFamily.Monospace)
                if (editing) {
                    BasicTextField(
                        value = draft.apiKey,
                        onValueChange = { onDraftChange(draft.copy(apiKey = it)) },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .semantics { contentDescription = fieldLabel },
                        textStyle = fieldStyle,
                        cursorBrush = SolidColor(H2g2.yellow),
                        singleLine = true,
                        visualTransformation = if (showKey || empty) VisualTransformation.None else PasswordVisualTransformation(),
                        // A key is neither prose nor a word: no autocorrect, no capitalisation, and
                        // the password keyboard so it is not learned into the suggestion dictionary.
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (empty) {
                                    Text(
                                        stringResource(Res.string.aisettings_field_placeholder),
                                        color = H2g2.yellow.copy(alpha = PLACEHOLDER_ALPHA),
                                        style = fieldStyle
                                    )
                                }
                                inner()
                            }
                        }
                    )
                } else {
                    Text(
                        when {
                            empty -> stringResource(Res.string.aisettings_field_empty)
                            showKey -> draft.apiKey
                            else -> stringResource(Res.string.aisettings_field_masked)
                        },
                        color = H2g2.yellow,
                        style = fieldStyle
                    )
                }
            }

            if (!empty) {
                Spacer(Modifier.height(8.dp))
                H2g2Chip(
                    label = stringResource(if (showKey) Res.string.aisettings_hide_key else Res.string.aisettings_show_key),
                    onClick = { onDraftChange(draft.copy(showKey = !showKey)) }
                )
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            H2g2Chip(
                label = stringResource(Res.string.aisettings_save),
                onClick = { save() },
                tint = H2g2.hues[6]
            )
            if (state.saveFailed) {
                Text(
                    stringResource(Res.string.aisettings_save_failed),
                    color = statusColor(StatusKind.Error),
                    style = settingDescriptionStyle(),
                    modifier = Modifier.padding(top = 8.dp).liveRegionPolite()
                )
            }
        }
    }

    if (draft.confirmDiscard) {
        H2g2ConfirmDialog(
            title = stringResource(Res.string.aisettings_discard_title),
            message = stringResource(Res.string.aisettings_discard_message),
            confirmLabel = stringResource(Res.string.aisettings_discard_confirm),
            cancelLabel = stringResource(Res.string.aisettings_discard_cancel),
            onConfirm = {
                onDraftChange(draft.copy(confirmDiscard = false))
                onBack()
            },
            onDismiss = { onDraftChange(draft.copy(confirmDiscard = false)) }
        )
    }
}

/** A plain (unmasked) single-line field in the same capsule as the key field, for model and endpoint. */
@Composable
private fun SettingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String,
    onDone: () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val fieldStyle = settingDescriptionStyle().copy(color = H2g2.yellow, fontFamily = FontFamily.Monospace)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = minTouchSize())
            .clip(H2g2Surface.capsule)
            .background(H2g2.ink)
            .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
            textStyle = fieldStyle,
            cursorBrush = SolidColor(H2g2.yellow),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = keyboardType,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = H2g2.yellow.copy(alpha = PLACEHOLDER_ALPHA), style = fieldStyle)
                    }
                    inner()
                }
            }
        )
    }
}

private const val PLACEHOLDER_ALPHA = 0.7f
