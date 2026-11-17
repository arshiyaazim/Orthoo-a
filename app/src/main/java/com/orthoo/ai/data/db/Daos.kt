package com.orthoo.ai.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getById(id: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: ChatSessionEntity)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM chat_sessions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun getForSession(sessionId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId AND createdAt >= :fromTimestamp")
    suspend fun deleteFromTimestamp(sessionId: String, fromTimestamp: Long)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: String)
}

@Dao
interface PalDao {
    @Query("SELECT * FROM pals ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PalEntity>>

    @Query("SELECT * FROM pals WHERE id = :id")
    suspend fun getById(id: String): PalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pal: PalEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(pals: List<PalEntity>)

    @Query("DELETE FROM pals WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM models ORDER BY origin ASC, isDownloaded DESC, name ASC")
    fun observeAll(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE id = :id")
    suspend fun getById(id: String): ModelEntity?

    @Query("SELECT * FROM models WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: String): List<ModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(model: ModelEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(models: List<ModelEntity>)

    @Update
    suspend fun update(model: ModelEntity)

    @Query("DELETE FROM models WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM models WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: String)

    @Query("DELETE FROM models WHERE origin = 'PRESET' AND isDownloaded = 0")
    suspend fun deleteUndownloadedPresets()
}

@Dao
interface RemoteServerDao {
    @Query("SELECT * FROM remote_servers ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RemoteServerEntity>>

    @Query("SELECT * FROM remote_servers WHERE id = :id")
    suspend fun getById(id: String): RemoteServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(server: RemoteServerEntity)

    @Delete
    suspend fun delete(server: RemoteServerEntity)

    @Query("DELETE FROM remote_servers WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface BenchmarkResultDao {
    @Query("SELECT * FROM benchmark_results ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<BenchmarkResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: BenchmarkResultEntity)

    @Query("DELETE FROM benchmark_results WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM benchmark_results")
    suspend fun clearAll()
}
