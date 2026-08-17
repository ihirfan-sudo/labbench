package com.labbench.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CultureDao {
    @Query("SELECT * FROM cultures WHERE status = 'Active' ORDER BY label")
    fun activeCultures(): Flow<List<Culture>>

    @Query("SELECT * FROM cultures ORDER BY status, label")
    fun allCultures(): Flow<List<Culture>>

    @Query("SELECT * FROM cultures WHERE id = :id")
    fun culture(id: String): Flow<Culture?>

    @Query("SELECT * FROM cultures WHERE id = :id")
    suspend fun cultureOnce(id: String): Culture?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(culture: Culture)

    @Delete
    suspend fun delete(culture: Culture)

    @Query("SELECT * FROM culture_events WHERE cultureId = :cultureId ORDER BY occurredAt DESC")
    fun events(cultureId: String): Flow<List<CultureEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addEvent(event: CultureEvent)

    @Delete
    suspend fun deleteEvent(event: CultureEvent)

    /** Cultures whose next feed is already due, cheapest possible query for the Today board. */
    @Query(
        """
        SELECT * FROM cultures
        WHERE status = 'Active'
          AND (lastFedAt IS NULL OR lastFedAt + (feedIntervalHours * 3600000) <= :now)
        ORDER BY lastFedAt ASC
        """
    )
    fun feedsDue(now: Long): Flow<List<Culture>>
}

@Dao
interface CellLineDao {
    @Query("SELECT * FROM cell_lines ORDER BY name")
    fun all(): Flow<List<CellLine>>

    @Query("SELECT * FROM cell_lines WHERE id = :id")
    suspend fun byId(id: String): CellLine?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(line: CellLine)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(lines: List<CellLine>)

    @Delete
    suspend fun delete(line: CellLine)
}

@Dao
interface InventoryDao {
    @Query("SELECT * FROM reagents ORDER BY name")
    fun reagents(): Flow<List<Reagent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reagent: Reagent)

    @Delete
    suspend fun delete(reagent: Reagent)

    @Query("SELECT * FROM reagent_lots WHERE reagentId = :reagentId ORDER BY expiresAt")
    fun lots(reagentId: String): Flow<List<ReagentLot>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(lot: ReagentLot)

    @Query("SELECT * FROM reagent_lots WHERE expiresAt IS NOT NULL AND expiresAt <= :before ORDER BY expiresAt")
    fun expiringLots(before: Long): Flow<List<ReagentLot>>

    @Query("SELECT * FROM storage_nodes WHERE parentId IS :parentId ORDER BY sortIndex, name")
    fun children(parentId: String?): Flow<List<StorageNode>>

    @Query("SELECT * FROM storage_nodes ORDER BY kind, name")
    fun allNodes(): Flow<List<StorageNode>>

    @Query("SELECT * FROM storage_nodes WHERE id = :id")
    suspend fun node(id: String): StorageNode?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(node: StorageNode)

    @Delete
    suspend fun delete(node: StorageNode)

    @Query("SELECT * FROM vials WHERE boxId = :boxId AND consumed = 0 ORDER BY position")
    fun vialsInBox(boxId: String): Flow<List<Vial>>

    @Query("SELECT * FROM vials WHERE consumed = 0 ORDER BY label")
    fun allVials(): Flow<List<Vial>>

    @Query("SELECT * FROM vials WHERE barcode = :barcode LIMIT 1")
    suspend fun vialByBarcode(barcode: String): Vial?

    @Query("SELECT COUNT(*) FROM vials WHERE boxId = :boxId AND consumed = 0")
    fun vialCount(boxId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vial: Vial)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVials(vials: List<Vial>)

    @Delete
    suspend fun delete(vial: Vial)

    @Query("SELECT * FROM equipment ORDER BY name")
    fun equipment(): Flow<List<Equipment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: Equipment)
}

@Dao
interface ProtocolDao {
    @Query("SELECT * FROM protocols ORDER BY category, name")
    fun protocols(): Flow<List<Protocol>>

    @Query("SELECT * FROM protocols WHERE id = :id")
    suspend fun protocol(id: String): Protocol?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(protocol: Protocol)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProtocols(protocols: List<Protocol>)

    @Delete
    suspend fun delete(protocol: Protocol)

    @Query("SELECT * FROM protocol_steps WHERE protocolId = :protocolId ORDER BY `order`")
    fun steps(protocolId: String): Flow<List<ProtocolStep>>

    @Query("SELECT * FROM protocol_steps WHERE protocolId = :protocolId ORDER BY `order`")
    suspend fun stepsOnce(protocolId: String): List<ProtocolStep>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStep(step: ProtocolStep)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSteps(steps: List<ProtocolStep>)

    @Query("SELECT COUNT(*) FROM protocols")
    suspend fun protocolCount(): Int
}

@Dao
interface TimerDao {
    @Query("SELECT * FROM timer_runs WHERE active = 1 LIMIT 1")
    fun activeRun(): Flow<TimerRun?>

    @Query("SELECT * FROM timer_runs WHERE active = 1 LIMIT 1")
    suspend fun activeRunOnce(): TimerRun?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(run: TimerRun)

    @Update
    suspend fun update(run: TimerRun)

    @Query("UPDATE timer_runs SET active = 0 WHERE active = 1")
    suspend fun clearActive()

    @Query("SELECT * FROM timer_runs ORDER BY startedAt DESC LIMIT 50")
    fun history(): Flow<List<TimerRun>>
}

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebook_entries ORDER BY createdAt DESC LIMIT :limit")
    fun recent(limit: Int = 200): Flow<List<NotebookEntry>>

    @Query("SELECT * FROM notebook_entries WHERE createdAt BETWEEN :from AND :to ORDER BY createdAt DESC")
    fun between(from: Long, to: Long): Flow<List<NotebookEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: NotebookEntry)

    @Delete
    suspend fun delete(entry: NotebookEntry)

    @Query("SELECT * FROM tasks WHERE done = 0 ORDER BY dueAt IS NULL, dueAt")
    fun openTasks(): Flow<List<LabTask>>

    @Query("SELECT * FROM tasks WHERE done = 0 AND dueAt IS NOT NULL AND dueAt <= :before ORDER BY dueAt")
    fun tasksDue(before: Long): Flow<List<LabTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: LabTask)

    @Delete
    suspend fun delete(task: LabTask)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun record(audit: AuditRecord)

    @Query("SELECT * FROM audit_log ORDER BY recordedAt DESC LIMIT :limit")
    fun auditTrail(limit: Int = 500): Flow<List<AuditRecord>>

    @Query("SELECT * FROM audit_log WHERE entityId = :entityId ORDER BY recordedAt DESC")
    fun auditFor(entityId: String): Flow<List<AuditRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun logCalculation(log: CalcLog)

    @Query("SELECT * FROM calc_logs ORDER BY recordedAt DESC LIMIT :limit")
    fun calcLogs(limit: Int = 200): Flow<List<CalcLog>>
}
