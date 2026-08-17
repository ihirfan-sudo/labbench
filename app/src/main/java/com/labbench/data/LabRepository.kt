package com.labbench.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Every write goes through here so that nothing changes without an audit record.
 * That's the whole point of the class — the DAOs are deliberately not exposed
 * to the UI layer.
 */
class LabRepository(
    private val db: LabDatabase,
    private val operatorProvider: () -> String = { "" }
) {
    private val cultures = db.cultures()
    private val lines = db.cellLines()
    private val inventory = db.inventory()
    private val protocolDao = db.protocols()
    private val notebook = db.notebook()

    private suspend fun audit(
        action: String,
        entityType: String,
        entityId: String,
        summary: String,
        previous: String? = null
    ) {
        notebook.record(
            AuditRecord(
                id = newId(),
                recordedAt = System.currentTimeMillis(),
                operator = operatorProvider(),
                action = action,
                entityType = entityType,
                entityId = entityId,
                summary = summary,
                previousValue = previous
            )
        )
    }

    // --- Cultures -----------------------------------------------------------

    val activeCultures: Flow<List<Culture>> = cultures.activeCultures()
    val allCellLines: Flow<List<CellLine>> = lines.all()

    fun culture(id: String) = cultures.culture(id)
    fun events(cultureId: String) = cultures.events(cultureId)
    fun feedsDue(now: Long = System.currentTimeMillis()) = cultures.feedsDue(now)

    suspend fun cellLine(id: String) = lines.byId(id)

    suspend fun startCulture(culture: Culture) {
        cultures.upsert(culture)
        audit("create", "culture", culture.id, "Started ${culture.label} at P${culture.passage}")
        notebook.upsert(
            NotebookEntry(
                id = newId(),
                createdAt = culture.startedAt,
                title = "Culture started: ${culture.label}",
                body = "Passage ${culture.passage} in ${culture.vessel}.",
                kind = "culture",
                linkedType = "culture",
                linkedId = culture.id,
                autoRecorded = true,
                operator = operatorProvider()
            )
        )
    }

    suspend fun recordFeed(cultureId: String, note: String = "", confluency: Int? = null) {
        val culture = cultures.cultureOnce(cultureId) ?: return
        val now = System.currentTimeMillis()
        cultures.upsert(
            culture.copy(
                lastFedAt = now,
                confluencyPercent = confluency ?: culture.confluencyPercent,
                confluencyRecordedAt = if (confluency != null) now else culture.confluencyRecordedAt
            )
        )
        cultures.addEvent(
            CultureEvent(newId(), cultureId, CultureEventType.Fed, now, confluency, note = note, operator = operatorProvider())
        )
        audit("feed", "culture", cultureId, "Fed ${culture.label}", previous = culture.lastFedAt?.toString())
    }

    suspend fun recordSplit(cultureId: String, ratio: String, confluency: Int?, note: String = "") {
        val culture = cultures.cultureOnce(cultureId) ?: return
        val now = System.currentTimeMillis()
        val newPassage = culture.passage + 1
        cultures.upsert(
            culture.copy(
                passage = newPassage,
                lastSplitAt = now,
                lastFedAt = now,
                confluencyPercent = 10,
                confluencyRecordedAt = now
            )
        )
        cultures.addEvent(
            CultureEvent(
                newId(), cultureId, CultureEventType.Split, now,
                confluencyPercent = confluency, passageAfter = newPassage,
                splitRatio = ratio, note = note, operator = operatorProvider()
            )
        )
        audit("split", "culture", cultureId, "Split ${culture.label} 1:$ratio → P$newPassage", "P${culture.passage}")

        val line = lines.byId(culture.cellLineId)
        if (line?.maxPassage != null && newPassage > line.maxPassage) {
            notebook.upsert(
                LabTask(
                    id = newId(),
                    title = "${culture.label} is past P${line.maxPassage} — thaw a fresh vial",
                    dueAt = now,
                    linkedType = "culture",
                    linkedId = cultureId
                )
            )
        }
    }

    suspend fun recordObservation(cultureId: String, confluency: Int?, note: String) {
        val culture = cultures.cultureOnce(cultureId) ?: return
        val now = System.currentTimeMillis()
        if (confluency != null) {
            cultures.upsert(culture.copy(confluencyPercent = confluency, confluencyRecordedAt = now))
        }
        cultures.addEvent(
            CultureEvent(newId(), cultureId, CultureEventType.Observed, now, confluency, note = note, operator = operatorProvider())
        )
        audit("observe", "culture", cultureId, note.ifBlank { "Observation logged" })
    }

    suspend fun markContaminated(cultureId: String, note: String) {
        val culture = cultures.cultureOnce(cultureId) ?: return
        val now = System.currentTimeMillis()
        cultures.upsert(culture.copy(status = CultureStatus.Contaminated))
        cultures.addEvent(
            CultureEvent(newId(), cultureId, CultureEventType.Contamination, now, note = note, operator = operatorProvider())
        )
        audit("contaminated", "culture", cultureId, "Flagged contaminated: $note", culture.status.name)
    }

    suspend fun setStatus(cultureId: String, status: CultureStatus) {
        val culture = cultures.cultureOnce(cultureId) ?: return
        cultures.upsert(culture.copy(status = status))
        audit("status", "culture", cultureId, "Status → ${status.name}", culture.status.name)
    }

    suspend fun upsertCellLine(line: CellLine) {
        lines.upsert(line)
        audit("upsert", "cell_line", line.id, line.name)
    }

    // --- Inventory ----------------------------------------------------------

    val reagents: Flow<List<Reagent>> = inventory.reagents()
    val allVials: Flow<List<Vial>> = inventory.allVials()
    val storageNodes: Flow<List<StorageNode>> = inventory.allNodes()
    val equipment: Flow<List<Equipment>> = inventory.equipment()

    fun children(parentId: String?) = inventory.children(parentId)
    fun vialsInBox(boxId: String) = inventory.vialsInBox(boxId)
    fun expiringLots(withinDays: Int = 30) =
        inventory.expiringLots(System.currentTimeMillis() + withinDays * 86_400_000L)

    suspend fun upsertReagent(reagent: Reagent) {
        inventory.upsert(reagent)
        audit("upsert", "reagent", reagent.id, reagent.name)
    }

    suspend fun upsertNode(node: StorageNode) {
        inventory.upsert(node)
        audit("upsert", "storage", node.id, "${node.kind.name}: ${node.name}")
    }

    suspend fun upsertVial(vial: Vial) {
        inventory.upsert(vial)
        audit("upsert", "vial", vial.id, "${vial.kind.name}: ${vial.label} @ ${vial.position}")
    }

    suspend fun consumeVial(vial: Vial, reason: String) {
        inventory.upsert(vial.copy(consumed = true))
        audit("consume", "vial", vial.id, "Removed ${vial.label}: $reason", "position ${vial.position}")
    }

    suspend fun vialByBarcode(code: String) = inventory.vialByBarcode(code)

    /**
     * Freeze N vials into the next N free positions of a box in one action.
     * Returns the vials actually placed — fewer than requested if the box fills.
     */
    suspend fun massFreeze(
        boxId: String,
        template: Vial,
        count: Int,
        occupied: Set<String>
    ): List<Vial> {
        val box = inventory.node(boxId) ?: return emptyList()
        val free = buildList {
            for (r in 0 until box.rows) for (c in 0 until box.columns) {
                val pos = "${'A' + r}${c + 1}"
                if (pos !in occupied) add(pos)
            }
        }.take(count)

        val created = free.map { position ->
            template.copy(id = newId(), boxId = boxId, position = position)
        }
        inventory.upsertVials(created)
        audit("mass_freeze", "storage", boxId, "Placed ${created.size} × ${template.label} in ${box.name}")
        return created
    }

    // --- Protocols ----------------------------------------------------------

    val protocols: Flow<List<Protocol>> = protocolDao.protocols()
    fun steps(protocolId: String) = protocolDao.steps(protocolId)
    suspend fun stepsOnce(protocolId: String) = protocolDao.stepsOnce(protocolId)
    suspend fun protocol(id: String) = protocolDao.protocol(id)

    suspend fun upsertProtocol(protocol: Protocol, steps: List<ProtocolStep>) {
        protocolDao.upsert(protocol)
        steps.forEach { protocolDao.upsertStep(it) }
        audit("upsert", "protocol", protocol.id, "${protocol.name} (${steps.size} steps)")
    }

    // --- Notebook, tasks, logs ---------------------------------------------

    val notebookEntries: Flow<List<NotebookEntry>> = notebook.recent()
    val openTasks: Flow<List<LabTask>> = notebook.openTasks()
    val auditTrail: Flow<List<AuditRecord>> = notebook.auditTrail()
    val calcLogs: Flow<List<CalcLog>> = notebook.calcLogs()

    suspend fun addNote(title: String, body: String, linkedType: String? = null, linkedId: String? = null) {
        val entry = NotebookEntry(
            id = newId(),
            createdAt = System.currentTimeMillis(),
            title = title,
            body = body,
            linkedType = linkedType,
            linkedId = linkedId,
            operator = operatorProvider()
        )
        notebook.upsert(entry)
        audit("note", "notebook", entry.id, title)
    }

    suspend fun upsertTask(task: LabTask) {
        notebook.upsert(task)
        audit("upsert", "task", task.id, task.title)
    }

    suspend fun completeTask(task: LabTask) {
        notebook.upsert(task.copy(done = true, completedAt = System.currentTimeMillis()))
        audit("complete", "task", task.id, task.title)
    }

    suspend fun logCalculation(
        calculatorId: String,
        calculatorName: String,
        inputs: Map<String, String>,
        resultSummary: String,
        linkedType: String? = null,
        linkedId: String? = null
    ) {
        val json = inputs.entries.joinToString(",", "{", "}") { (k, v) ->
            "\"${k.escapeJson()}\":\"${v.escapeJson()}\""
        }
        notebook.logCalculation(
            CalcLog(newId(), System.currentTimeMillis(), calculatorId, calculatorName, json, resultSummary, linkedType, linkedId)
        )
        notebook.upsert(
            NotebookEntry(
                id = newId(),
                createdAt = System.currentTimeMillis(),
                title = calculatorName,
                body = resultSummary,
                kind = "calculation",
                linkedType = linkedType,
                linkedId = linkedId,
                autoRecorded = true,
                operator = operatorProvider()
            )
        )
    }

    /** Everything the Today board needs, in one stream. */
    fun todayBoard(): Flow<TodaySnapshot> = combine(
        feedsDue(),
        notebook.tasksDue(System.currentTimeMillis() + 86_400_000L),
        expiringLots(14),
        activeCultures
    ) { feeds, tasks, lots, active ->
        TodaySnapshot(feeds, tasks, lots, active.size)
    }

    fun cultureLabel(cultureId: String): Flow<String> =
        cultures.culture(cultureId).map { it?.label.orEmpty() }
}

data class TodaySnapshot(
    val feedsDue: List<Culture> = emptyList(),
    val tasksDue: List<LabTask> = emptyList(),
    val expiringLots: List<ReagentLot> = emptyList(),
    val activeCultureCount: Int = 0
)

private fun String.escapeJson() = replace("\\", "\\\\").replace("\"", "\\\"")
