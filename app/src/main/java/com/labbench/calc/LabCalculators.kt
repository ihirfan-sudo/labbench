package com.labbench.calc

import kotlin.math.pow
import kotlin.math.sqrt

private val molarUnits = listOf("M", "mM", "µM", "nM")
private val volumeUnits = listOf("L", "mL", "µL")

internal fun molarFactor(unit: String) = when (unit) {
    "M" -> 1.0; "mM" -> 1e-3; "µM" -> 1e-6; "nM" -> 1e-9; else -> 1.0
}

internal fun volumeFactorLiters(unit: String) = when (unit) {
    "L" -> 1.0; "mL" -> 1e-3; "µL" -> 1e-6; else -> 1e-3
}

val solutionCalculators = listOf(

    Calculator(
        id = "c1v1",
        name = "Dilution (C1V1 = C2V2)",
        category = CalcCategory.Solutions,
        blurb = "Stock volume and diluent for any target concentration.",
        fields = listOf(
            CalcField("c1", "Stock concentration", default = "10"),
            CalcField("c2", "Final concentration", default = "1"),
            CalcField("v2", "Final volume", default = "10"),
            CalcField("vUnit", "Volume unit", kind = FieldKind.Choice, options = volumeUnits, default = "mL")
        )
    ) { i ->
        val c1 = i.positive("c1", "Stock concentration")
        val c2 = i.positive("c2", "Final concentration")
        val v2 = i.positive("v2", "Final volume")
        val unit = i.choice("vUnit", "mL")
        if (c2 > c1) throw BadInput("Final concentration is higher than the stock. You can't dilute up to it.")

        val v1 = c1v1(c1, c2, v2)
        CalcOutcome.Ok(
            listOf(
                ResultLine("Stock to take", "${fmt(v1)} $unit", primary = true),
                ResultLine("Diluent to add", "${fmt(v2 - v1)} $unit"),
                ResultLine("Dilution factor", "1 : ${fmt(c1 / c2, 3)}")
            ),
            if (v1 / v2 < 0.001) listOf("This is over a 1000-fold dilution in one step. A serial dilution will be more accurate.") else emptyList()
        )
    },

    Calculator(
        id = "serial",
        name = "Serial dilution",
        category = CalcCategory.Solutions,
        blurb = "Transfer and diluent volumes for an N-point series.",
        fields = listOf(
            CalcField("start", "Starting concentration", default = "1000"),
            CalcField("fold", "Fold per step", default = "10"),
            CalcField("steps", "Number of steps", kind = FieldKind.Integer, default = "6"),
            CalcField("volume", "Volume per tube", unit = "µL", default = "500")
        )
    ) { i ->
        val start = i.positive("start", "Starting concentration")
        val fold = i.positive("fold", "Fold per step")
        if (fold <= 1) throw BadInput("Fold per step must be greater than 1.")
        val steps = i.int("steps", "Number of steps")
        if (steps !in 1..24) throw BadInput("Use between 1 and 24 steps.")
        val volume = i.positive("volume", "Volume per tube")

        val transfer = volume / (fold - 1)
        val lines = buildList {
            add(ResultLine("Transfer each step", fmtVolume(transfer), primary = true))
            add(ResultLine("Diluent per tube", fmtVolume(volume)))
            add(ResultLine("Prepare tube 1 with", fmtVolume(volume + transfer)))
            for (s in 1..steps) {
                add(ResultLine("Tube $s", sci(start / fold.pow(s - 1)) + " ×"))
            }
        }
        CalcOutcome.Ok(
            lines,
            listOf("Each tube starts with ${fmtVolume(volume)} diluent; carry ${fmtVolume(transfer)} forward and mix before the next transfer.")
        )
    },

    Calculator(
        id = "molarity",
        name = "Molarity → mass",
        category = CalcCategory.Solutions,
        blurb = "How much powder to weigh for a target molarity.",
        fields = listOf(
            CalcField("mw", "Molecular weight", unit = "g/mol", default = "58.44"),
            CalcField("conc", "Target concentration", default = "100"),
            CalcField("cUnit", "Concentration unit", kind = FieldKind.Choice, options = molarUnits, default = "mM"),
            CalcField("volume", "Final volume", default = "500"),
            CalcField("vUnit", "Volume unit", kind = FieldKind.Choice, options = volumeUnits, default = "mL")
        )
    ) { i ->
        val mw = i.positive("mw", "Molecular weight")
        val conc = i.positive("conc", "Target concentration") * molarFactor(i.choice("cUnit", "mM"))
        val liters = i.positive("volume", "Final volume") * volumeFactorLiters(i.choice("vUnit", "mL"))

        val grams = conc * liters * mw
        val display = when {
            grams >= 1 -> "${fmt(grams)} g"
            grams >= 1e-3 -> "${fmt(grams * 1e3)} mg"
            else -> "${fmt(grams * 1e6)} µg"
        }
        CalcOutcome.Ok(
            listOf(
                ResultLine("Weigh out", display, primary = true),
                ResultLine("In grams", "${fmt(grams, 5)} g"),
                ResultLine("Moles required", "${sci(conc * liters)} mol")
            ),
            if (grams < 0.001) listOf("Under 1 mg is below the accuracy of most bench balances. Make a concentrated stock and dilute it.") else emptyList()
        )
    },

    Calculator(
        id = "percent",
        name = "Percent solution",
        category = CalcCategory.Solutions,
        blurb = "w/v and v/v amounts for a percentage solution.",
        fields = listOf(
            CalcField("percent", "Target percentage", unit = "%", default = "4"),
            CalcField("volume", "Final volume", unit = "mL", default = "100"),
            CalcField("mode", "Type", kind = FieldKind.Choice, options = listOf("w/v (g per 100 mL)", "v/v (mL per 100 mL)"), default = "w/v (g per 100 mL)")
        )
    ) { i ->
        val percent = i.positive("percent", "Percentage")
        if (percent > 100) throw BadInput("A solution can't exceed 100%.")
        val volume = i.positive("volume", "Final volume")
        val wv = i.choice("mode", "w/v (g per 100 mL)").startsWith("w/v")
        val amount = percent / 100.0 * volume

        CalcOutcome.Ok(
            listOf(
                ResultLine(if (wv) "Solute to weigh" else "Solute volume", if (wv) "${fmt(amount)} g" else "${fmt(amount)} mL", primary = true),
                ResultLine("Bring to final volume", "${fmt(volume)} mL"),
                if (wv) ResultLine("Solvent (approx.)", "${fmt(volume - amount)} mL") else ResultLine("Solvent", "${fmt(volume - amount)} mL")
            ),
            if (wv) listOf("Dissolve first in about 80% of the final volume, then top up — never add solvent to the mark before the solute dissolves.") else emptyList()
        )
    },

    Calculator(
        id = "mastermix",
        name = "Master mix",
        category = CalcCategory.Solutions,
        blurb = "Scale a per-reaction volume across reactions with overage.",
        fields = listOf(
            CalcField("perRxn", "Volume per reaction", unit = "µL", default = "5"),
            CalcField("reactions", "Reactions", kind = FieldKind.Integer, default = "24"),
            CalcField("overage", "Overage", unit = "%", default = "10"),
            CalcField("rxnVolume", "Total reaction volume", unit = "µL", default = "20")
        )
    ) { i ->
        val perRxn = i.positive("perRxn", "Volume per reaction")
        val reactions = i.int("reactions", "Reactions")
        val overage = i.nonNegative("overage", "Overage") / 100.0
        val rxnVolume = i.positive("rxnVolume", "Total reaction volume")
        val n = reactions * (1 + overage)

        CalcOutcome.Ok(
            listOf(
                ResultLine("Component volume", fmtVolume(perRxn * n), primary = true),
                ResultLine("Mix covers", "${fmt(n, 3)} reactions"),
                ResultLine("Total mix volume", fmtVolume(rxnVolume * n)),
                ResultLine("Dispense per tube", fmtVolume(rxnVolume))
            ),
            if (reactions > 8 && overage < 0.05) listOf("Under 5% overage across $reactions reactions usually leaves the last tube short.") else emptyList()
        )
    }
)

val molecularCalculators = listOf(

    Calculator(
        id = "nucleic_acid",
        name = "Nucleic acid quantification",
        category = CalcCategory.MolecularBiology,
        blurb = "Concentration and purity from A260/A280 readings.",
        fields = listOf(
            CalcField("a260", "A260", default = "0.85"),
            CalcField("a280", "A280", default = "0.45"),
            CalcField("type", "Sample", kind = FieldKind.Choice, options = listOf("dsDNA", "ssDNA", "RNA", "Oligo"), default = "dsDNA"),
            CalcField("dilution", "Dilution factor", default = "1"),
            CalcField("pathLength", "Path length", unit = "cm", default = "1")
        )
    ) { i ->
        val a260 = i.positive("a260", "A260")
        val a280 = i.numOrNull("a280")
        val factor = when (i.choice("type", "dsDNA")) {
            "dsDNA" -> 50.0; "ssDNA" -> 33.0; "RNA" -> 40.0; "Oligo" -> 33.0; else -> 50.0
        }
        val dilution = i.positive("dilution", "Dilution factor")
        val path = i.positive("pathLength", "Path length")

        val conc = a260 * factor * dilution / path
        val ratio = a280?.takeIf { it > 0 }?.let { a260 / it }

        CalcOutcome.Ok(
            buildList {
                add(ResultLine("Concentration", "${fmt(conc)} ng/µL", primary = true))
                add(ResultLine("Also", "${fmt(conc)} µg/mL"))
                ratio?.let { add(ResultLine("A260/A280", fmt(it, 3))) }
            },
            buildList {
                if (a260 > 1.0) add("A260 above 1.0 is outside the linear range of most spectrophotometers. Dilute and re-read.")
                ratio?.let {
                    if (it < 1.7) add("A260/A280 below 1.7 suggests protein or phenol carryover.")
                    if (it > 2.2) add("A260/A280 above 2.2 can indicate RNA contamination in a DNA prep.")
                }
            }
        )
    },

    Calculator(
        id = "primer_tm",
        name = "Primer Tm",
        category = CalcCategory.MolecularBiology,
        blurb = "Melting temperature, GC content, and length from a sequence.",
        fields = listOf(
            CalcField("seq", "Primer sequence", kind = FieldKind.Sequence, default = "", help = "A, T, G, C only — spaces are ignored")
        )
    ) { i ->
        val seq = i.text("seq").uppercase().filter { !it.isWhitespace() }
        if (seq.isEmpty()) throw BadInput("Enter a primer sequence.")
        val invalid = seq.filterNot { it in "ATGC" }
        if (invalid.isNotEmpty()) throw BadInput("Unsupported base '${invalid.first()}'. Use A, T, G, or C.")

        val n = seq.length
        val gc = seq.count { it == 'G' || it == 'C' }
        val at = n - gc
        val gcPercent = gc * 100.0 / n
        val tm = if (n < 14) 2.0 * at + 4.0 * gc else 64.9 + 41.0 * (gc - 16.4) / n

        CalcOutcome.Ok(
            listOf(
                ResultLine("Tm", "${fmt(tm, 3)} °C", primary = true),
                ResultLine("Suggested annealing", "${fmt(tm - 5, 3)} °C"),
                ResultLine("GC content", "${fmt(gcPercent, 3)} %"),
                ResultLine("Length", "$n nt"),
                ResultLine("Reverse complement", seq.reversed().map {
                    when (it) { 'A' -> 'T'; 'T' -> 'A'; 'G' -> 'C'; else -> 'G' }
                }.joinToString(""))
            ),
            buildList {
                if (n < 14) add("Under 14 nt: using the Wallace rule, which is only a rough estimate.")
                if (gcPercent < 40 || gcPercent > 60) add("GC content outside 40–60% often gives unreliable amplification.")
                if (seq.contains("GGGG") || seq.contains("CCCC")) add("Four or more consecutive G or C bases can cause secondary structure.")
            }
        )
    },

    Calculator(
        id = "western_loading",
        name = "Western blot loading",
        category = CalcCategory.MolecularBiology,
        blurb = "Sample, buffer, and water volumes for equal protein loading.",
        fields = listOf(
            CalcField("conc", "Protein concentration", unit = "µg/µL", default = "2"),
            CalcField("target", "Protein per lane", unit = "µg", default = "20"),
            CalcField("wellVolume", "Well capacity", unit = "µL", default = "30"),
            CalcField("bufferX", "Loading buffer strength", kind = FieldKind.Choice, options = listOf("2×", "4×", "5×", "6×"), default = "4×")
        )
    ) { i ->
        val conc = i.positive("conc", "Protein concentration")
        val target = i.positive("target", "Protein per lane")
        val wellVolume = i.positive("wellVolume", "Well capacity")
        val strength = i.choice("bufferX", "4×").removeSuffix("×").toDouble()

        val sample = target / conc
        val buffer = wellVolume / strength
        val water = wellVolume - sample - buffer

        if (water < 0) {
            return@Calculator CalcOutcome.Invalid(
                "Sample (${fmt(sample)} µL) plus buffer (${fmt(buffer)} µL) already exceeds the " +
                    "${fmt(wellVolume)} µL well. Load less protein or concentrate the lysate."
            )
        }
        CalcOutcome.Ok(
            listOf(
                ResultLine("Lysate", fmtVolume(sample), primary = true),
                ResultLine("Loading buffer", fmtVolume(buffer)),
                ResultLine("Water", fmtVolume(water)),
                ResultLine("Total per lane", fmtVolume(wellVolume))
            ),
            if (sample < 1) listOf("Under 1 µL of lysate is hard to pipette reproducibly. Dilute the sample first.") else emptyList()
        )
    },

    Calculator(
        id = "cfu",
        name = "CFU / mL",
        category = CalcCategory.MolecularBiology,
        blurb = "Viable count back-calculated from a plated dilution.",
        fields = listOf(
            CalcField("colonies", "Colonies counted", kind = FieldKind.Integer, default = "84"),
            CalcField("plated", "Volume plated", unit = "µL", default = "100"),
            CalcField("dilution", "Dilution plated (1 : x)", default = "1e5")
        )
    ) { i ->
        val colonies = i.int("colonies", "Colonies")
        val plated = i.positive("plated", "Volume plated")
        val dilution = i.positive("dilution", "Dilution")

        val cfu = colonies / (plated / 1000.0) * dilution
        CalcOutcome.Ok(
            listOf(
                ResultLine("Viable count", "${sci(cfu)} CFU/mL", primary = true),
                ResultLine("On this plate", "$colonies colonies")
            ),
            buildList {
                if (colonies < 30) add("Below 30 colonies is outside the statistically reliable counting range.")
                if (colonies > 300) add("Above 300 colonies the plate is too crowded to count accurately.")
            }
        )
    },

    Calculator(
        id = "od600",
        name = "OD600 culture",
        category = CalcCategory.MolecularBiology,
        blurb = "Cell density and the volume needed to inoculate to a target OD.",
        fields = listOf(
            CalcField("od", "Measured OD600", default = "0.6"),
            CalcField("dilution", "Dilution factor", default = "1"),
            CalcField("factor", "Cells per mL at OD 1.0", default = "8e8"),
            CalcField("targetOd", "Target OD600", default = "0.05"),
            CalcField("targetVolume", "Target volume", unit = "mL", default = "50")
        )
    ) { i ->
        val od = i.positive("od", "Measured OD600") * i.positive("dilution", "Dilution factor")
        val factor = i.positive("factor", "Cells per mL at OD 1.0")
        val targetOd = i.positive("targetOd", "Target OD600")
        val targetVolume = i.positive("targetVolume", "Target volume")
        if (targetOd > od) throw BadInput("Target OD is above the culture OD. Grow it further or reduce the target.")

        val inoculum = c1v1(od, targetOd, targetVolume)
        CalcOutcome.Ok(
            listOf(
                ResultLine("Inoculum volume", fmtVolume(inoculum * 1000), primary = true),
                ResultLine("Fresh medium", "${fmt(targetVolume - inoculum)} mL"),
                ResultLine("Culture density", "${sci(od * factor)} cells/mL"),
                ResultLine("Corrected OD600", fmt(od, 3))
            ),
            if (od > 1.0) listOf("Above OD 1.0 the relationship stops being linear — dilute the sample before reading.") else emptyList()
        )
    }
)

val instrumentCalculators = listOf(

    Calculator(
        id = "rcf",
        name = "RPM ↔ RCF",
        category = CalcCategory.Instruments,
        blurb = "Convert between rotor speed and relative centrifugal force.",
        fields = listOf(
            CalcField("radius", "Rotor radius", unit = "mm", default = "100"),
            CalcField("rpm", "Speed", unit = "RPM", default = "300"),
            CalcField("rcf", "Force", unit = "× g", default = "")
        )
    ) { i ->
        val radius = i.positive("radius", "Rotor radius")
        val rpm = i.numOrNull("rpm")
        val rcf = i.numOrNull("rcf")

        when {
            rpm != null && rpm > 0 -> CalcOutcome.Ok(
                listOf(
                    ResultLine("Relative force", "${fmt(rcfFromRpm(rpm, radius))} × g", primary = true),
                    ResultLine("At", "${fmt(rpm)} RPM"),
                    ResultLine("Rotor radius", "${fmt(radius)} mm")
                ),
                if (rcf != null) listOf("Both fields were filled, so RPM was used. Clear the RPM field to convert the other way.") else emptyList()
            )
            rcf != null && rcf > 0 -> CalcOutcome.Ok(
                listOf(
                    ResultLine("Speed", "${fmt(rpmFromRcf(rcf, radius))} RPM", primary = true),
                    ResultLine("At", "${fmt(rcf)} × g")
                )
            )
            else -> CalcOutcome.Invalid("Enter either a speed or a force, and the rotor radius.")
        }
    },

    Calculator(
        id = "fold_dilution",
        name = "Fold dilution",
        category = CalcCategory.Instruments,
        blurb = "Volumes for a stated fold dilution.",
        fields = listOf(
            CalcField("fold", "Fold dilution (1 : x)", default = "10"),
            CalcField("final", "Final volume", unit = "µL", default = "1000")
        )
    ) { i ->
        val fold = i.positive("fold", "Fold dilution")
        if (fold < 1) throw BadInput("Fold dilution must be at least 1.")
        val final = i.positive("final", "Final volume")
        val sample = final / fold

        CalcOutcome.Ok(
            listOf(
                ResultLine("Sample", fmtVolume(sample), primary = true),
                ResultLine("Diluent", fmtVolume(final - sample)),
                ResultLine("Final volume", fmtVolume(final))
            ),
            if (sample < 2) listOf("Under 2 µL is at the edge of pipette accuracy. Scale the final volume up or dilute in two steps.") else emptyList()
        )
    }
)

// --- Pure formulas, kept separate so they are trivially unit-testable ---

fun c1v1(c1: Double, c2: Double, v2: Double): Double = c2 * v2 / c1

fun rcfFromRpm(rpm: Double, radiusMm: Double): Double = 1.118e-6 * radiusMm * rpm * rpm

fun rpmFromRcf(rcf: Double, radiusMm: Double): Double = sqrt(rcf / (1.118e-6 * radiusMm))

object CalculatorCatalog {
    val all: List<Calculator> =
        cultureCalculators + solutionCalculators + molecularCalculators + instrumentCalculators

    fun byId(id: String): Calculator? = all.firstOrNull { it.id == id }

    fun grouped(): Map<CalcCategory, List<Calculator>> = all.groupBy { it.category }

    fun search(query: String): List<Calculator> {
        if (query.isBlank()) return all
        val q = query.trim().lowercase()
        return all.filter { it.name.lowercase().contains(q) || it.blurb.lowercase().contains(q) }
    }
}
