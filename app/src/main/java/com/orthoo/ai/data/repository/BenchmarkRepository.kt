package com.orthoo.ai.data.repository

import com.orthoo.ai.data.db.BenchmarkResultDao
import com.orthoo.ai.data.db.BenchmarkResultEntity
import kotlinx.coroutines.flow.Flow

class BenchmarkRepository(private val dao: BenchmarkResultDao) {
    val results: Flow<List<BenchmarkResultEntity>> = dao.observeAll()

    suspend fun addResult(result: BenchmarkResultEntity) = dao.insert(result)

    suspend fun removeResult(id: String) = dao.deleteById(id)

    suspend fun clearAll() = dao.clearAll()
}
