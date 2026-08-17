package com.labbench.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ---------------------------------------------------------------------------
// Cell lines and cultures
// ---------------------------------------------------------------------------

@Entity(tableName = "cell_lines")
data class CellLine(
    @PrimaryKey val id: String,
    val name: String,
    val organism: String = "",
    val tissue: String = "",
    val medium: String = "",
    val supplements: String = "",
    val splitRatio: String = "",
    val doublingTimeHours: Double? = null,
    val biosafetyLevel: Int = 1,
    val maxPassage: Int? = null,
    val notes: String = ""
)

enum class CultureStatus { Active, Frozen, Discarded, Contaminated }

@Entity(
    tableName = "cultures",
    indices = [Index("cellLineId"), Index("status")]
)
data class Culture(
    @PrimaryKey val id: String,
    val label: String,
    val cellLineId: String,
    val passage: Int,
    val vessel: String = "T75",
    val startedAt: Long,
    val lastFedAt: Long? = null,
    val lastSplitAt: Long? = null,
    val feedIntervalHours: Int = 48,
    val confluencyPercent: Int = 0,
    val confluencyRecordedAt: Long? = null,
    val incubator: String = "",
    val status: CultureStatus = CultureStatus.Active,
    val mycoplasmaTestedAt: Long? = null,
    val mycoplasmaClear: Boolean? = null,
    val notes: String = ""
)

enum class CultureEventType { Fed, Split, Observed, Frozen, Thawed, Treated, Contamination, MycoplasmaTest }

@Entity(tableName = "culture_events", indices = [Index("cultureId"), Index("occurredAt")])
data class CultureEvent(
    @PrimaryKey val id: String,
    val cultureId: String,
    val type: CultureEventType,
    val occurredAt: Long,
    val confluencyPercent: Int? = null,
    val passageAfter: Int? = null,
    val splitRatio: String? = null,
    val note: String = "",
    val operator: String = ""
)

// ---------------------------------------------------------------------------
// Inventory: reagents, lots, freezer hierarchy
// ---------------------------------------------------------------------------

@Entity(tableName = "reagents", indices = [Index("name")])
data class Reagent(
    @PrimaryKey val id: String,
    val name: String,
    val vendor: String = "",
    val catalogNumber: String = "",
    val concentration: String = "",
    val storageTemp: String = "4 °C",
    val hazardClass: String = "",
    val notes: String = ""
)

@Entity(tableName = "reagent_lots", indices = [Index("reagentId"), Index("expiresAt")])
data class ReagentLot(
    @PrimaryKey val id: String,
    val reagentId: String,
    val lotNumber: String,
    val openedAt: Long? = null,
    val expiresAt: Long? = null,
    val remainingFraction: Double = 1.0,
    val price: Double? = null,
    val performanceNote: String = ""
)

/** Tank → Rack → Box → position. One table, self-referencing, so any depth works. */
enum class StorageKind { Freezer, Tank, Shelf, Rack, Box }

@Entity(tableName = "storage_nodes", indices = [Index("parentId")])
data class StorageNode(
    @PrimaryKey val id: String,
    val parentId: String? = null,
    val name: String,
    val kind: StorageKind,
    val temperature: String = "",
    val rows: Int = 0,
    val columns: Int = 0,
    val sortIndex: Int = 0
)

enum class VialKind {
    Cells, Plasmid, Antibody, Primer, RNA, DNA, Protein, Peptide, Buffer,
    BacterialStock, VirusStock, Tissue, Serum, Other
}

@Entity(tableName = "vials", indices = [Index("boxId"), Index("kind"), Index("expiresAt")])
data class Vial(
    @PrimaryKey val id: String,
    val boxId: String?,
    val position: String = "",
    val kind: VialKind,
    val label: String,
    val cellLineId: String? = null,
    val passage: Int? = null,
    val cellCount: Double? = null,
    val concentration: String = "",
    val frozenAt: Long? = null,
    val expiresAt: Long? = null,
    val barcode: String? = null,
    val quantity: Int = 1,
    val consumed: Boolean = false,
    val notes: String = ""
)

@Entity(tableName = "equipment", indices = [Index("nextServiceAt")])
data class Equipment(
    @PrimaryKey val id: String,
    val name: String,
    val location: String = "",
    val serialNumber: String = "",
    val lastServicedAt: Long? = null,
    val nextServiceAt: Long? = null,
    val serviceIntervalDays: Int = 180,
    val outOfService: Boolean = false,
    val notes: String = ""
)

// ---------------------------------------------------------------------------
// Protocols and timers
// ---------------------------------------------------------------------------

@Entity(tableName = "protocols")
data class Protocol(
    @PrimaryKey val id: String,
    val name: String,
    val category: String = "General",
    val description: String = "",
    val builtIn: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "protocol_steps", indices = [Index("protocolId")])
data class ProtocolStep(
    @PrimaryKey val id: String,
    val protocolId: String,
    val order: Int,
    val title: String,
    val detail: String = "",
    val durationSeconds: Int,
    val critical: Boolean = false
)

/** A protocol actually being run. At most one is active at a time. */
@Entity(tableName = "timer_runs", indices = [Index("active")])
data class TimerRun(
    @PrimaryKey val id: String,
    val protocolId: String?,
    val protocolName: String,
    val stepIndex: Int,
    val stepTitle: String,
    val stepDurationSeconds: Int,
    val stepEndsAt: Long,
    val paused: Boolean = false,
    val pausedRemainingSeconds: Int = 0,
    val active: Boolean = true,
    val startedAt: Long = System.currentTimeMillis(),
    val cultureId: String? = null
)

// ---------------------------------------------------------------------------
// Notebook, tasks, audit
// ---------------------------------------------------------------------------

@Entity(tableName = "notebook_entries", indices = [Index("createdAt")])
data class NotebookEntry(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val title: String,
    val body: String = "",
    val kind: String = "note",
    val linkedType: String? = null,
    val linkedId: String? = null,
    val autoRecorded: Boolean = false,
    val operator: String = ""
)

@Entity(tableName = "tasks", indices = [Index("dueAt"), Index("done")])
data class LabTask(
    @PrimaryKey val id: String,
    val title: String,
    val dueAt: Long?,
    val done: Boolean = false,
    val completedAt: Long? = null,
    val linkedType: String? = null,
    val linkedId: String? = null,
    val notes: String = ""
)

/**
 * Append-only record of every mutation. This is the piece that separates a
 * bench toy from something a QA auditor will accept: who changed what, when,
 * and what the value was before.
 */
@Entity(tableName = "audit_log", indices = [Index("recordedAt"), Index("entityType")])
data class AuditRecord(
    @PrimaryKey val id: String,
    val recordedAt: Long,
    val operator: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val summary: String,
    val previousValue: String? = null
)

@Entity(tableName = "calc_logs", indices = [Index("recordedAt")])
data class CalcLog(
    @PrimaryKey val id: String,
    val recordedAt: Long,
    val calculatorId: String,
    val calculatorName: String,
    val inputsJson: String,
    val resultSummary: String,
    val linkedType: String? = null,
    val linkedId: String? = null
)
