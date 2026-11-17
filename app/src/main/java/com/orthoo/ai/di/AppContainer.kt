package com.orthoo.ai.di

import android.content.Context
import com.orthoo.ai.data.db.OrthooDatabase
import com.orthoo.ai.data.prefs.AppPreferences
import com.orthoo.ai.data.repository.BenchmarkRepository
import com.orthoo.ai.data.repository.ChatRepository
import com.orthoo.ai.data.repository.ModelRepository
import com.orthoo.ai.data.repository.PalRepository
import com.orthoo.ai.services.agent.AgentRunner
import com.orthoo.ai.services.benchmark.BenchmarkEngine
import com.orthoo.ai.services.download.ModelDownloadManager
import com.orthoo.ai.services.hardware.HardwareInfoProvider
import com.orthoo.ai.services.network.HuggingFaceApi
import com.orthoo.ai.services.network.OpenAIClient
import com.orthoo.ai.services.search.SearchService
import com.orthoo.ai.services.speech.SpeechManager
import com.orthoo.ai.services.talents.TalentRegistry
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    val database: OrthooDatabase by lazy { OrthooDatabase.getInstance(appContext) }
    val preferences: AppPreferences by lazy { AppPreferences(appContext) }

    val chatRepository: ChatRepository by lazy {
        ChatRepository(database.chatSessionDao(), database.chatMessageDao())
    }
    val modelRepository: ModelRepository by lazy {
        ModelRepository(database.modelDao(), database.remoteServerDao())
    }
    val palRepository: PalRepository by lazy {
        PalRepository(database.palDao())
    }
    val benchmarkRepository: BenchmarkRepository by lazy {
        BenchmarkRepository(database.benchmarkResultDao())
    }

    val hardwareInfoProvider: HardwareInfoProvider by lazy { HardwareInfoProvider(appContext) }
    val huggingFaceApi: HuggingFaceApi by lazy { HuggingFaceApi(okHttpClient) }
    val openAIClient: OpenAIClient by lazy { OpenAIClient(appContext, okHttpClient) }
    val downloadManager: ModelDownloadManager by lazy {
        ModelDownloadManager(appContext, okHttpClient, modelRepository, hardwareInfoProvider)
    }
    val searchService: SearchService by lazy { SearchService(okHttpClient) }
    val talentRegistry: TalentRegistry by lazy { TalentRegistry(searchService) }
    val agentRunner: AgentRunner by lazy {
        AgentRunner(chatRepository, modelRepository, palRepository, openAIClient, talentRegistry)
    }
    val speechManager: SpeechManager by lazy { SpeechManager(appContext) }
    val benchmarkEngine: BenchmarkEngine by lazy {
        BenchmarkEngine(hardwareInfoProvider, modelRepository, openAIClient)
    }
}
