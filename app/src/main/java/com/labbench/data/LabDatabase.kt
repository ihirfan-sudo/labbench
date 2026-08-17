package com.labbench.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class Converters {
    @TypeConverter fun cultureStatus(v: CultureStatus): String = v.name
    @TypeConverter fun toCultureStatus(v: String): CultureStatus =
        runCatching { CultureStatus.valueOf(v) }.getOrDefault(CultureStatus.Active)

    @TypeConverter fun eventType(v: CultureEventType): String = v.name
    @TypeConverter fun toEventType(v: String): CultureEventType =
        runCatching { CultureEventType.valueOf(v) }.getOrDefault(CultureEventType.Observed)

    @TypeConverter fun storageKind(v: StorageKind): String = v.name
    @TypeConverter fun toStorageKind(v: String): StorageKind =
        runCatching { StorageKind.valueOf(v) }.getOrDefault(StorageKind.Box)

    @TypeConverter fun vialKind(v: VialKind): String = v.name
    @TypeConverter fun toVialKind(v: String): VialKind =
        runCatching { VialKind.valueOf(v) }.getOrDefault(VialKind.Other)
}

@Database(
    entities = [
        CellLine::class, Culture::class, CultureEvent::class,
        Reagent::class, ReagentLot::class, StorageNode::class, Vial::class, Equipment::class,
        Protocol::class, ProtocolStep::class, TimerRun::class,
        NotebookEntry::class, LabTask::class, AuditRecord::class, CalcLog::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class LabDatabase : RoomDatabase() {
    abstract fun cultures(): CultureDao
    abstract fun cellLines(): CellLineDao
    abstract fun inventory(): InventoryDao
    abstract fun protocols(): ProtocolDao
    abstract fun timers(): TimerDao
    abstract fun notebook(): NotebookDao

    companion object {
        @Volatile private var instance: LabDatabase? = null

        fun get(context: Context): LabDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(context: Context): LabDatabase =
            Room.databaseBuilder(context, LabDatabase::class.java, "labbench.db")
                // No destructive fallback: losing a researcher's passage history to a
                // schema bump is unacceptable. Every version bump ships a Migration.
                .build()
                .also { db ->
                    CoroutineScope(Dispatchers.IO).launch { Seed.applyIfEmpty(db) }
                }
    }
}

fun newId(): String = UUID.randomUUID().toString()

/**
 * First-run content. Kept small and factual — a handful of common lines and the
 * timed protocols people actually stand at the bench for.
 */
object Seed {
    suspend fun applyIfEmpty(db: LabDatabase) {
        if (db.protocols().protocolCount() > 0) return

        db.cellLines().insertAll(
            listOf(
                CellLine(newId(), "HEK293T", "Human", "Embryonic kidney", "DMEM", "10% FBS, 1% Pen-Strep", "1:6", 24.0, 2, 30),
                CellLine(newId(), "HeLa", "Human", "Cervical adenocarcinoma", "DMEM", "10% FBS, 1% Pen-Strep", "1:8", 24.0, 2, 40),
                CellLine(newId(), "A549", "Human", "Lung carcinoma", "F-12K", "10% FBS", "1:6", 22.0, 2, 30),
                CellLine(newId(), "MCF-7", "Human", "Breast adenocarcinoma", "EMEM", "10% FBS, insulin", "1:4", 38.0, 1, 25),
                CellLine(newId(), "NIH/3T3", "Mouse", "Embryonic fibroblast", "DMEM", "10% BCS", "1:8", 20.0, 1, 20),
                CellLine(newId(), "CHO-K1", "Hamster", "Ovary", "F-12K", "10% FBS", "1:8", 18.0, 1, null),
                CellLine(newId(), "Jurkat", "Human", "T lymphocyte", "RPMI-1640", "10% FBS", "1:4 (suspension)", 30.0, 2, null),
                CellLine(newId(), "Caco-2", "Human", "Colorectal adenocarcinoma", "EMEM", "20% FBS", "1:3", 62.0, 1, 40)
            )
        )

        val protocols = mutableListOf<Protocol>()
        val steps = mutableListOf<ProtocolStep>()

        fun protocol(name: String, category: String, description: String, vararg s: Triple<String, Int, String>) {
            val id = newId()
            protocols += Protocol(id, name, category, description, builtIn = true)
            s.forEachIndexed { index, (title, seconds, detail) ->
                steps += ProtocolStep(newId(), id, index, title, detail, seconds)
            }
        }

        protocol(
            "Passaging adherent cells", "Cell culture",
            "Standard trypsin passage for adherent monolayers.",
            Triple("Aspirate medium and rinse with PBS", 60, "Rinse gently down the side of the flask, not onto the monolayer."),
            Triple("Add trypsin and incubate", 240, "Just enough to cover the surface. Check detachment at 3 minutes."),
            Triple("Neutralise with serum-containing medium", 60, "Use at least twice the trypsin volume."),
            Triple("Centrifuge", 300, "200 × g. Confirm the RCF for your rotor before you spin."),
            Triple("Resuspend and seed", 180, "Resuspend gently — no bubbles — then count and seed.")
        )
        protocol(
            "Thawing cryovial", "Cell culture",
            "Rapid thaw with DMSO removal.",
            Triple("Warm vial in 37 °C bath", 120, "Stop while a small ice crystal remains."),
            Triple("Transfer drop-wise into warm medium", 90, "Dilute the DMSO slowly to avoid osmotic shock."),
            Triple("Centrifuge", 300, "200 × g to remove residual DMSO."),
            Triple("Resuspend and plate", 120, "Plate at higher density than usual; recovery is poor at low density.")
        )
        protocol(
            "Freezing cells", "Cell culture",
            "Controlled-rate freeze into cryovials.",
            Triple("Harvest and count", 300, ""),
            Triple("Centrifuge", 300, "200 × g, 5 minutes."),
            Triple("Resuspend in cold freezing medium", 120, "Add drop-wise while swirling."),
            Triple("Aliquot into vials", 180, "Label with line, passage, date, and operator before you fill them."),
            Triple("Move to −80 °C in a controlled-rate container", 60, "Transfer to liquid nitrogen within 24 hours.")
        )
        protocol(
            "Medium change", "Cell culture",
            "Routine feed for adherent cultures.",
            Triple("Warm medium to 37 °C", 900, "Cold medium stresses the monolayer."),
            Triple("Aspirate spent medium", 60, "Check colour and clarity first — record anything unusual."),
            Triple("Add fresh medium", 60, "Down the side of the vessel.")
        )
        protocol(
            "Trypan blue viability count", "Assays",
            "Load, settle, and count a hemocytometer.",
            Triple("Mix sample 1:1 with trypan blue", 30, ""),
            Triple("Load chamber and let cells settle", 60, "Counting before cells settle biases the count low."),
            Triple("Count four large squares", 300, "Count cells touching the top and left lines only.")
        )
        protocol(
            "Bradford assay", "Assays",
            "Colorimetric protein quantification.",
            Triple("Prepare standards", 600, ""),
            Triple("Add reagent and incubate", 300, "Room temperature, protected from light."),
            Triple("Read at 595 nm", 120, "Read within 60 minutes; the colour is not stable.")
        )
        protocol(
            "SDS-PAGE run", "Molecular biology",
            "Gel electrophoresis with denatured samples.",
            Triple("Heat samples at 95 °C", 300, ""),
            Triple("Load gel", 600, ""),
            Triple("Run at constant voltage", 3600, "Stop when the dye front reaches the bottom.")
        )
        protocol(
            "Western transfer (wet)", "Molecular biology",
            "Tank transfer onto membrane.",
            Triple("Equilibrate gel and membrane", 900, ""),
            Triple("Assemble cassette", 300, "Roll out every bubble."),
            Triple("Transfer at 100 V", 3600, "Keep the tank cold — use an ice pack.")
        )
        protocol(
            "Membrane blocking and antibody", "Molecular biology",
            "Blocking through secondary antibody.",
            Triple("Block", 3600, "5% milk or BSA in TBST."),
            Triple("Primary antibody", 3600, "Overnight at 4 °C is usually cleaner."),
            Triple("Wash", 900, "3 × 5 minutes in TBST."),
            Triple("Secondary antibody", 3600, ""),
            Triple("Wash", 900, "3 × 5 minutes in TBST.")
        )
        protocol(
            "Bacterial transformation", "Molecular biology",
            "Heat-shock transformation of chemically competent cells.",
            Triple("Thaw competent cells on ice", 600, ""),
            Triple("Add DNA and incubate on ice", 1800, "Do not mix by pipetting — flick gently."),
            Triple("Heat shock at 42 °C", 45, "Timing matters. 45 seconds, not 60."),
            Triple("Return to ice", 120, ""),
            Triple("Recover in SOC at 37 °C", 3600, "Shaking at 220 rpm."),
            Triple("Plate on selective agar", 300, "")
        )
        protocol(
            "Plasmid miniprep", "Molecular biology",
            "Alkaline lysis column prep.",
            Triple("Pellet overnight culture", 180, ""),
            Triple("Resuspend and lyse", 300, "Do not exceed 5 minutes in lysis buffer."),
            Triple("Neutralise and clear", 600, ""),
            Triple("Bind, wash, elute", 600, "Let elution buffer sit on the column for 1 minute.")
        )
        protocol(
            "MTT assay", "Assays",
            "Metabolic viability readout.",
            Triple("Add MTT reagent", 60, ""),
            Triple("Incubate at 37 °C", 14400, "Until purple formazan crystals are visible."),
            Triple("Solubilise", 900, ""),
            Triple("Read at 570 nm", 120, "")
        )

        db.protocols().insertProtocols(protocols)
        db.protocols().insertSteps(steps)
    }
}
