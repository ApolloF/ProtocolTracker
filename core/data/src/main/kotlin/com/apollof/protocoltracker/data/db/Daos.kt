package com.apollof.protocoltracker.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CompoundDao {
    @Query("SELECT * FROM compounds ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CompoundEntity>>

    @Query("SELECT * FROM compounds")
    suspend fun getAll(): List<CompoundEntity>

    @Query("SELECT * FROM compounds WHERE id = :id")
    suspend fun get(id: String): CompoundEntity?

    @Query("SELECT id FROM compounds")
    suspend fun ids(): List<String>

    @Upsert
    suspend fun upsert(items: List<CompoundEntity>)

    @Query("DELETE FROM compounds WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM compounds")
    suspend fun clear()
}

@Dao
interface PhaseDao {
    @Query("SELECT * FROM phases ORDER BY startDate, id")
    fun observeAll(): Flow<List<PhaseEntity>>

    @Query("SELECT * FROM phases ORDER BY startDate, id")
    suspend fun getAll(): List<PhaseEntity>

    @Upsert
    suspend fun upsert(items: List<PhaseEntity>)

    @Query("DELETE FROM phases WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM phases")
    suspend fun clear()
}

@Dao
interface PlanItemDao {
    @Query("SELECT * FROM plan_items ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<PlanItemEntity>>

    @Query("SELECT * FROM plan_items ORDER BY sortOrder, id")
    suspend fun getAll(): List<PlanItemEntity>

    @Query("SELECT COUNT(*) FROM plan_items WHERE compoundId = :compoundId")
    suspend fun countForCompound(compoundId: String): Int

    @Upsert
    suspend fun upsert(items: List<PlanItemEntity>)

    @Query("DELETE FROM plan_items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM plan_items WHERE phaseId = :phaseId")
    suspend fun deleteForPhase(phaseId: String)

    @Query("DELETE FROM plan_items")
    suspend fun clear()
}

@Dao
interface DoseLogDao {
    /** Emits on every change to the table; the value itself is only a trigger. */
    @Query("SELECT COUNT(*) FROM dose_logs")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM dose_logs ORDER BY takenAtMs DESC")
    fun observeAll(): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_logs WHERE takenAtMs >= :fromMs OR scheduledAtMs >= :fromMs ORDER BY takenAtMs")
    fun observeSince(fromMs: Long): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_logs ORDER BY takenAtMs")
    suspend fun getAll(): List<DoseLogEntity>

    @Query("SELECT * FROM dose_logs WHERE takenAtMs >= :fromMs OR scheduledAtMs >= :fromMs ORDER BY takenAtMs")
    suspend fun getSince(fromMs: Long): List<DoseLogEntity>

    @Query("SELECT * FROM dose_logs WHERE id = :id")
    suspend fun get(id: String): DoseLogEntity?

    @Query("SELECT * FROM dose_logs WHERE occurrenceKey = :key")
    suspend fun byOccurrence(key: String): DoseLogEntity?

    @Query("SELECT COUNT(*) FROM dose_logs WHERE compoundId = :compoundId")
    suspend fun countForCompound(compoundId: String): Int

    @Upsert
    suspend fun upsert(items: List<DoseLogEntity>)

    @Query("DELETE FROM dose_logs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM dose_logs")
    suspend fun clear()
}
