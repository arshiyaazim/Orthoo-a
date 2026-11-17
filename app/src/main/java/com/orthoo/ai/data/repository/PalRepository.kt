package com.orthoo.ai.data.repository

import com.orthoo.ai.data.db.PalDao
import com.orthoo.ai.data.db.PalEntity
import com.orthoo.ai.data.model.CompletionParams
import com.orthoo.ai.data.model.PalParameterSchema
import com.orthoo.ai.data.model.PalType
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class PalRepository(private val palDao: PalDao) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val pals: Flow<List<PalEntity>> = palDao.observeAll()

    suspend fun getById(id: String): PalEntity? = palDao.getById(id)

    suspend fun savePal(
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
    ): PalEntity {
        val now = System.currentTimeMillis()
        val existing = id?.let { palDao.getById(it) }
        val pal = PalEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            description = description.trim(),
            palType = palType.name,
            systemPrompt = systemPrompt,
            originalSystemPrompt = existing?.originalSystemPrompt ?: systemPrompt,
            isSystemPromptChanged = existing?.let { it.originalSystemPrompt != systemPrompt } ?: false,
            defaultModelId = defaultModelId,
            colorPrimaryHex = colorPrimaryHex,
            colorSecondaryHex = colorSecondaryHex,
            greetingText = greetingText.trim(),
            suggestedPromptsJson = json.encodeToString(suggestedPrompts.filter { it.isNotBlank() }),
            parametersJson = json.encodeToString(parameters),
            parameterSchemaJson = json.encodeToString(parameterSchema),
            talentsJson = json.encodeToString(talents),
            completionSettingsJson = completionParams?.let { json.encodeToString(it) },
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        palDao.upsert(pal)
        return pal
    }

    suspend fun duplicatePal(id: String): PalEntity? {
        val orig = palDao.getById(id) ?: return null
        val now = System.currentTimeMillis()
        val copy = orig.copy(
            id = UUID.randomUUID().toString(),
            name = "${orig.name} (Copy)",
            createdAt = now,
            updatedAt = now
        )
        palDao.upsert(copy)
        return copy
    }

    suspend fun deletePal(id: String) = palDao.deleteById(id)

    fun decodeTalents(pal: PalEntity): List<String> =
        runCatching { json.decodeFromString<List<String>>(pal.talentsJson) }.getOrDefault(emptyList())

    fun decodeSuggestedPrompts(pal: PalEntity): List<String> =
        runCatching { json.decodeFromString<List<String>>(pal.suggestedPromptsJson) }.getOrDefault(emptyList())

    fun decodeParameters(pal: PalEntity): Map<String, String> =
        runCatching { json.decodeFromString<Map<String, String>>(pal.parametersJson) }.getOrDefault(emptyMap())

    fun decodeParameterSchema(pal: PalEntity): List<PalParameterSchema> =
        runCatching { json.decodeFromString<List<PalParameterSchema>>(pal.parameterSchemaJson) }.getOrDefault(emptyList())

    fun decodeCompletionParams(pal: PalEntity): CompletionParams? =
        pal.completionSettingsJson?.let { runCatching { json.decodeFromString<CompletionParams>(it) }.getOrNull() }

    /**
     * Renders mustache-style `{{key}}` placeholders inside the Pal's system prompt
     * using the Pal's configured parameters (e.g., {{world}}, {{location}}, {{aiRole}}).
     */
    fun renderSystemPrompt(pal: PalEntity): String {
        val params = decodeParameters(pal)
        var result = pal.systemPrompt
        for ((key, value) in params) {
            result = result.replace("{{$key}}", value)
        }
        return result
    }
}
