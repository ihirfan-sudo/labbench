package com.labbench.calc

import kotlin.math.ln
import kotlin.math.pow

private val plateFormats = listOf("6-well", "12-well", "24-well", "48-well", "96-well", "T25", "T75", "T175")

/** Working volume in mL per well/flask, and growth area in cm². */
internal fun vesselSpec(name: String): Pair<Double, Double> = when (name) {
    "6-well" -> 2.0 to 9.6
    "12-well" -> 1.0 to 3.8
    "24-well" -> 0.5 to 1.9
    "48-well" -> 0.3 to 1.1
    "96-well" -> 0.1 to 0.32
    "T25" -> 5.0 to 25.0
    "T75" -> 15.0 to 75.0
    "T175" -> 35.0 to 175.0
    else -> 1.0 to 1.0
}

val cultureCalculators = listOf(

    Calculator(
        id = "hemocytometer",
        name = "Hemocytometer count",
        category = CalcCategory.CellCulture,
        blurb = "Cells/mL and total yield from counted squares.",
        fields = listOf(
            CalcField("counted", "Total cells counted", kind = FieldKind.Integer, default = ""),
            CalcField("squares", "Large squares counted", kind = FieldKind.Integer, default = "4"),
            CalcField("dilution", "Dilution factor", default = "2", help = "2 for a 1:1 trypan blue mix"),
            CalcField("volume", "Suspension volume", unit = "mL", default = "10")
        )
    ) { i ->
        val counted = i.int("counted", "Cells counted")
        val squares = i.int("squares", "Squares counted")
        if (squares <= 0) throw BadInput("Count at least one square.")
        val dilution = i.positive("dilution", "Dilution factor")
        val volume = i.positive("volume", "Suspension volume")

        val perSquare = counted.toDouble() / squares
        val concentration = perSquare * dilution * 1e4
        val total = concentration * volume

        val warnings = buildList {
            if (perSquare < 20) add("Under 20 cells per square — counting error is high. Concentrate the sample or count more squares.")
            if (perSquare > 250) add("Over 250 cells per square — dilute further before trusting this number.")
        }
        CalcOutcome.Ok(
            listOf(
                ResultLine("Concentration", "${sci(concentration)} cells/mL", primary = true),
                ResultLine("Total cells", sci(total)),
                ResultLine("Mean per square", fmt(perSquare, 3))
            ),
            warnings
        )
    },

    Calculator(
        id = "viability",
        name = "Viability",
        category = CalcCategory.CellCulture,
        blurb = "Trypan blue exclusion: % viable and live-cell concentration.",
        fields = listOf(
            CalcField("live", "Live (unstained) cells", kind = FieldKind.Integer),
            CalcField("dead", "Dead (blue) cells", kind = FieldKind.Integer, default = "0"),
            CalcField("squares", "Large squares counted", kind = FieldKind.Integer, default = "4"),
            CalcField("dilution", "Dilution factor", default = "2")
        )
    ) { i ->
        val live = i.int("live", "Live cells")
        val dead = i.int("dead", "Dead cells")
        val squares = i.int("squares", "Squares counted")
        val dilution = i.positive("dilution", "Dilution factor")
        val total = live + dead
        if (total == 0) throw BadInput("Enter at least one counted cell.")

        val viability = live * 100.0 / total
        val liveConc = (live.toDouble() / squares) * dilution * 1e4

        val warnings = buildList {
            if (viability < 80) add("Viability under 80% — check trypsin exposure, centrifuge speed, and medium age before seeding.")
            if (total < 50) add("Fewer than 50 total cells counted; the percentage is noisy.")
        }
        CalcOutcome.Ok(
            listOf(
                ResultLine("Viability", "${fmt(viability, 3)} %", primary = true),
                ResultLine("Live cell concentration", "${sci(liveConc)} cells/mL"),
                ResultLine("Total counted", "$total")
            ),
            warnings
        )
    },

    Calculator(
        id = "seeding",
        name = "Cell seeding",
        category = CalcCategory.CellCulture,
        blurb = "Suspension and medium volumes to hit a target density.",
        fields = listOf(
            CalcField("stock", "Stock concentration", unit = "cells/mL", default = "1e6"),
            CalcField("vessel", "Vessel", kind = FieldKind.Choice, options = plateFormats, default = "6-well"),
            CalcField("count", "Number of wells/flasks", kind = FieldKind.Integer, default = "6"),
            CalcField("target", "Target cells per well", default = "2e5"),
            CalcField("overage", "Overage", unit = "%", default = "10")
        )
    ) { i ->
        val stock = i.positive("stock", "Stock concentration")
        val vessel = i.choice("vessel", "6-well")
        val (workingVolume, area) = vesselSpec(vessel)
        val count = i.int("count", "Number of wells")
        val target = i.positive("target", "Target cells per well")
        val overage = i.nonNegative("overage", "Overage") / 100.0

        val effectiveCount = count * (1 + overage)
        val cellsNeeded = target * effectiveCount
        val suspensionMl = cellsNeeded / stock
        val totalMl = workingVolume * effectiveCount
        val mediumMl = totalMl - suspensionMl

        if (mediumMl < 0) {
            return@Calculator CalcOutcome.Invalid(
                "Stock is too dilute: you'd need ${fmt(suspensionMl)} mL of suspension but the " +
                    "vessels only hold ${fmt(totalMl)} mL. Concentrate the cells or lower the target."
            )
        }
        CalcOutcome.Ok(
            listOf(
                ResultLine("Cell suspension", "${fmt(suspensionMl)} mL", primary = true),
                ResultLine("Medium to add", "${fmt(mediumMl)} mL"),
                ResultLine("Total mix", "${fmt(totalMl)} mL"),
                ResultLine("Per vessel", "${fmt(workingVolume)} mL"),
                ResultLine("Seeding density", "${sci(target / area)} cells/cm²")
            ),
            if (suspensionMl < 0.02) listOf("Suspension volume is under 20 µL — pipetting error will dominate. Pre-dilute the stock.") else emptyList()
        )
    },

    Calculator(
        id = "splitting",
        name = "Cell splitting",
        category = CalcCategory.CellCulture,
        blurb = "Split ratio into volumes, or volumes into a ratio.",
        fields = listOf(
            CalcField("ratio", "Split ratio 1 :", default = "4"),
            CalcField("vessel", "New vessel", kind = FieldKind.Choice, options = plateFormats, default = "T75"),
            CalcField("count", "New vessels", kind = FieldKind.Integer, default = "1"),
            CalcField("harvest", "Harvest volume", unit = "mL", default = "10")
        )
    ) { i ->
        val ratio = i.positive("ratio", "Split ratio")
        val vessel = i.choice("vessel", "T75")
        val (workingVolume, _) = vesselSpec(vessel)
        val count = i.int("count", "New vessels")
        val harvest = i.positive("harvest", "Harvest volume")

        val perVessel = harvest / ratio
        val used = perVessel * count
        val mediumPerVessel = workingVolume - perVessel

        CalcOutcome.Ok(
            listOf(
                ResultLine("Suspension per vessel", "${fmt(perVessel)} mL", primary = true),
                ResultLine("Medium per vessel", "${fmt(mediumPerVessel.coerceAtLeast(0.0))} mL"),
                ResultLine("Total suspension used", "${fmt(used)} mL"),
                ResultLine("Suspension left over", "${fmt(harvest - used)} mL")
            ),
            buildList {
                if (used > harvest) add("You need ${fmt(used)} mL but only harvested ${fmt(harvest)} mL. Reduce the vessel count or the ratio.")
                if (mediumPerVessel < 0) add("A 1:${fmt(ratio)} split of ${fmt(harvest)} mL overfills a $vessel.")
            }
        )
    },

    Calculator(
        id = "freezing",
        name = "Freezing medium",
        category = CalcCategory.CellCulture,
        blurb = "Vial count, resuspension volume, and DMSO/serum split.",
        fields = listOf(
            CalcField("total", "Total cells to freeze", default = "1e7"),
            CalcField("perVial", "Cells per vial", default = "1e6"),
            CalcField("vialVolume", "Volume per vial", unit = "mL", default = "1"),
            CalcField("dmso", "DMSO", unit = "%", default = "10"),
            CalcField("overage", "Overage", unit = "%", default = "10")
        )
    ) { i ->
        val total = i.positive("total", "Total cells")
        val perVial = i.positive("perVial", "Cells per vial")
        val vialVolume = i.positive("vialVolume", "Volume per vial")
        val dmsoPct = i.nonNegative("dmso", "DMSO percentage")
        val overage = i.nonNegative("overage", "Overage") / 100.0

        val vials = kotlin.math.floor(total / perVial).toInt()
        if (vials < 1) throw BadInput("That's less than one vial's worth of cells.")
        val mediumNeeded = vials * vialVolume * (1 + overage)
        val dmsoVolume = mediumNeeded * dmsoPct / 100.0

        CalcOutcome.Ok(
            listOf(
                ResultLine("Vials", "$vials", primary = true),
                ResultLine("Freezing medium to prepare", "${fmt(mediumNeeded)} mL"),
                ResultLine("DMSO", "${fmt(dmsoVolume)} mL"),
                ResultLine("Base medium / serum", "${fmt(mediumNeeded - dmsoVolume)} mL"),
                ResultLine("Resuspend pellet in", "${fmt(vials * vialVolume)} mL")
            ),
            buildList {
                if (dmsoPct > 15) add("Above 15% DMSO is cytotoxic for most lines.")
                add("Add freezing medium cold and drop-wise, then move to −80 °C within 15 minutes.")
            }
        )
    },

    Calculator(
        id = "doubling",
        name = "Doubling time",
        category = CalcCategory.CellCulture,
        blurb = "Population doublings and doubling time between two counts.",
        fields = listOf(
            CalcField("seeded", "Cells seeded", default = "5e5"),
            CalcField("harvested", "Cells harvested", default = "4e6"),
            CalcField("hours", "Time in culture", unit = "h", default = "72")
        )
    ) { i ->
        val seeded = i.positive("seeded", "Cells seeded")
        val harvested = i.positive("harvested", "Cells harvested")
        val hours = i.positive("hours", "Time in culture")
        if (harvested <= seeded) throw BadInput("Harvested count must exceed the seeded count to compute growth.")

        val doublings = log2(harvested / seeded)
        val doublingTime = hours / doublings
        val growthRate = ln(harvested / seeded) / hours

        CalcOutcome.Ok(
            listOf(
                ResultLine("Doubling time", "${fmt(doublingTime, 3)} h", primary = true),
                ResultLine("Population doublings", fmt(doublings, 3)),
                ResultLine("Specific growth rate", "${fmt(growthRate, 3)} h⁻¹"),
                ResultLine("Fold expansion", "${fmt(harvested / seeded, 3)}×")
            )
        )
    },

    Calculator(
        id = "moi",
        name = "MOI / virus volume",
        category = CalcCategory.CellCulture,
        blurb = "Virus volume per well at a target multiplicity of infection.",
        fields = listOf(
            CalcField("moi", "Target MOI", default = "1"),
            CalcField("cells", "Cells per well at infection", default = "1e5"),
            CalcField("titer", "Viral titer", unit = "per mL", default = "1e8"),
            CalcField("vessel", "Vessel", kind = FieldKind.Choice, options = plateFormats, default = "24-well"),
            CalcField("wells", "Wells to infect", kind = FieldKind.Integer, default = "3"),
            CalcField("overage", "Overage", unit = "%", default = "10")
        )
    ) { i ->
        val moi = i.positive("moi", "Target MOI")
        val cells = i.positive("cells", "Cells per well")
        val titer = i.positive("titer", "Viral titer")
        val vessel = i.choice("vessel", "24-well")
        val (workingVolume, _) = vesselSpec(vessel)
        val wells = i.int("wells", "Wells")
        val overage = i.nonNegative("overage", "Overage") / 100.0

        val perWellMl = (moi * cells) / titer
        val effective = wells * (1 + overage)
        val totalVirus = perWellMl * effective
        val totalMedium = workingVolume * effective - totalVirus

        CalcOutcome.Ok(
            listOf(
                ResultLine("Virus per well", fmtVolume(perWellMl * 1000), primary = true),
                ResultLine("Total virus (with overage)", fmtVolume(totalVirus * 1000)),
                ResultLine("Medium for the mix", "${fmt(totalMedium.coerceAtLeast(0.0))} mL"),
                ResultLine("Infection volume per well", "${fmt(workingVolume)} mL")
            ),
            buildList {
                if (perWellMl * 1000 < 1) add("Under 1 µL per well — make an intermediate dilution rather than pipetting this directly.")
                if (perWellMl > workingVolume) add("Required virus volume exceeds the well's working volume. The titer is too low for this MOI.")
            }
        )
    },

    Calculator(
        id = "transfection",
        name = "Transfection mix",
        category = CalcCategory.CellCulture,
        blurb = "DNA, reagent, and diluent volumes scaled across a plate.",
        fields = listOf(
            CalcField("vessel", "Vessel", kind = FieldKind.Choice, options = plateFormats, default = "6-well"),
            CalcField("wells", "Wells", kind = FieldKind.Integer, default = "6"),
            CalcField("dnaPerWell", "DNA per well", unit = "µg", default = "2.5"),
            CalcField("dnaStock", "DNA stock concentration", unit = "µg/µL", default = "1"),
            CalcField("ratio", "Reagent : DNA ratio (µL : µg)", default = "3"),
            CalcField("diluentPerWell", "Diluent per well", unit = "µL", default = "150"),
            CalcField("overage", "Overage", unit = "%", default = "10")
        )
    ) { i ->
        val wells = i.int("wells", "Wells")
        val dnaPerWell = i.positive("dnaPerWell", "DNA per well")
        val dnaStock = i.positive("dnaStock", "DNA stock concentration")
        val ratio = i.positive("ratio", "Reagent to DNA ratio")
        val diluentPerWell = i.positive("diluentPerWell", "Diluent per well")
        val overage = i.nonNegative("overage", "Overage") / 100.0
        val n = wells * (1 + overage)

        val dnaVolume = (dnaPerWell / dnaStock) * n
        val reagentVolume = dnaPerWell * ratio * n
        val diluentTotal = diluentPerWell * n

        CalcOutcome.Ok(
            listOf(
                ResultLine("DNA stock", fmtVolume(dnaVolume), primary = true),
                ResultLine("Transfection reagent", fmtVolume(reagentVolume)),
                ResultLine("Diluent (per tube)", fmtVolume(diluentTotal)),
                ResultLine("Total DNA", "${fmt(dnaPerWell * n)} µg"),
                ResultLine("Mix covers", "${fmt(n, 3)} wells")
            ),
            listOf("Dilute DNA and reagent in separate tubes, combine, then rest 15 minutes before adding drop-wise.")
        )
    },

    Calculator(
        id = "confluency",
        name = "Confluency projection",
        category = CalcCategory.CellCulture,
        blurb = "When a flask reaches your target confluency at the current growth rate.",
        fields = listOf(
            CalcField("current", "Current confluency", unit = "%", default = "40"),
            CalcField("target", "Target confluency", unit = "%", default = "85"),
            CalcField("doubling", "Doubling time", unit = "h", default = "24")
        )
    ) { i ->
        val current = i.positive("current", "Current confluency")
        val target = i.positive("target", "Target confluency")
        val doubling = i.positive("doubling", "Doubling time")
        if (target <= current) throw BadInput("Target confluency must be above the current value.")
        if (target > 100) throw BadInput("Confluency cannot exceed 100%.")

        val doublings = log2(target / current)
        val hours = doublings * doubling

        CalcOutcome.Ok(
            listOf(
                ResultLine("Ready in", "${fmt(hours, 3)} h", primary = true),
                ResultLine("That's about", "${fmt(hours / 24.0, 2)} days"),
                ResultLine("Doublings required", fmt(doublings, 3))
            ),
            listOf("Exponential projection. Growth flattens above roughly 80% confluency, so treat this as the earliest plausible time, not a promise.")
        )
    }
)
