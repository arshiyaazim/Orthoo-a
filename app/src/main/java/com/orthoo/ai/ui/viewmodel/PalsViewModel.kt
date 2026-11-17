package com.orthoo.ai.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orthoo.ai.data.db.ModelEntity
import com.orthoo.ai.data.db.PalEntity
import com.orthoo.ai.data.model.CompletionParams
import com.orthoo.ai.data.model.PalParameterSchema
import com.orthoo.ai.data.model.PalType
import com.orthoo.ai.di.AppContainer
import com.orthoo.ai.services.talents.TalentEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class PalsViewModel(private val container: AppContainer) : ViewModel() {

    val pals: StateFlow<List<PalEntity>> = container.palRepository.pals
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val models: StateFlow<List<ModelEntity>> = container.modelRepository.models
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val availableTalents: List<TalentEngine> = container.talentRegistry.allTalents()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun savePal(
        id: String?,
        name: String,
        description: String,
        palType: PalType,
        systemPrompt: String,
        defaultModelId: String?,
        colorPrimaryHex: Long,
        colorSecondaryHex: Long,
        greetingText: String,
        suggestedPrompts: List<String>,
        talents: List<String>,
        parameters: Map<String, String>,
        parameterSchema: List<PalParameterSchema>,
        completionParams: CompletionParams?
    ) {
        viewModelScope.launch {
            container.palRepository.savePal(
                id = id,
                name = name,
                description = description,
                palType = palType,
                systemPrompt = systemPrompt,
                defaultModelId = defaultModelId,
                colorPrimaryHex = colorPrimaryHex,
                colorSecondaryHex = colorSecondaryHex,
                greetingText = greetingText,
                suggestedPrompts = suggestedPrompts,
                talents = talents,
                parameters = parameters,
                parameterSchema = parameterSchema,
                completionParams = completionParams
            )
            _statusMessage.value = "Saved Pal \"$name\""
        }
    }

    fun duplicatePal(id: String) {
        viewModelScope.launch {
            val copy = container.palRepository.duplicatePal(id)
            if (copy != null) {
                _statusMessage.value = "Duplicated \"${copy.name}\""
            }
        }
    }

    fun deletePal(id: String) {
        viewModelScope.launch {
            container.palRepository.deletePal(id)
            _statusMessage.value = "Pal deleted"
        }
    }

    fun decodeTalents(pal: PalEntity): List<String> = container.palRepository.decodeTalents(pal)

    fun decodeSuggestedPrompts(pal: PalEntity): List<String> = container.palRepository.decodeSuggestedPrompts(pal)

    fun decodeParameters(pal: PalEntity): Map<String, String> = container.palRepository.decodeParameters(pal)

    fun decodeParameterSchema(pal: PalEntity): List<PalParameterSchema> = container.palRepository.decodeParameterSchema(pal)

    fun decodeCompletionParams(pal: PalEntity): CompletionParams? = container.palRepository.decodeCompletionParams(pal)

    fun exportPalJson(pal: PalEntity): String {
        return JSONObject().apply {
            put("name", pal.name)
            put("description", pal.description)
            put("palType", pal.palType)
            put("systemPrompt", pal.systemPrompt)
            put("greetingText", pal.greetingText)
            put("suggestedPrompts", JSONArray(decodeSuggestedPrompts(pal)))
            put("talents", JSONArray(decodeTalents(pal)))
            put("parameters", JSONObject(decodeParameters(pal)))
            put("colorPrimaryHex", String.format("#%06X", 0xFFFFFF and pal.colorPrimaryHex.toInt()))
            put("colorSecondaryHex", String.format("#%06X", 0xFFFFFF and pal.colorSecondaryHex.toInt()))
        }.toString(2)
    }
}
