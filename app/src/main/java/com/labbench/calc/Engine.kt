package com.labbench.calc

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * A calculator is data, not a screen. One generic UI renders every entry in
 * [CalculatorCatalog], so adding a calculator is adding one object here — never
 * a new Composable, a new ViewModel, or a new navigation route.
 */

enum class CalcCategory(val label: String) {
    CellCulture("Cell culture"),
    Solutions("Solutions & dilutions"),
    MolecularBiology("Molecular biology"),
    Instruments("Instruments")
}

enum class FieldKind { Decimal, Integer, Choice, Sequence }

data class CalcField(
    val key: String,
    val label: String,
    val unit: String? = null,
    val default: String = "",
    val kind: FieldKind = FieldKind.Decimal,
    val options: List<String> = emptyList(),
    val help: String? = null
)

data class ResultLine(val label: String, val value: String, val primary: Boolean = false)

sealed interface CalcOutcome {
    data class Ok(
        val lines: List<ResultLine>,
        val warnings: List<String> = emptyList()
    ) : CalcOutcome

    data class Invalid(val message: String) : CalcOutcome
}

data class Calculator(
    val id: String,
    val name: String,
    val category: CalcCategory,
    val blurb: String,
    val fields: List<CalcField>,
    val compute: (Inputs) -> CalcOutcome
)

class BadInput(message: String) : Exception(message)

/**
 * Reads user text into numbers. Deliberately tolerant: a bench user typing
 * "0,5" or "1 000 000" or "1e6" at 7am with gloves on means what you think
 * they mean, and every one of those has been a real bug in shipped lab apps.
 */
class Inputs(private val raw: Map<String, String>) {

    fun text(key: String): String = raw[key].orEmpty().trim()

    fun num(key: String, label: String = key): Double =
        numOrNull(key) ?: throw BadInput("Enter a value for $label.")

    fun numOrNull(key: String): Double? {
        val cleaned = raw[key].orEmpty()
            .replace('\u00A0', ' ')
            .replace(" ", "")
            .replace(',', '.')
            .trim()
        if (cleaned.isEmpty()) return null
        return cleaned.toDoubleOrNull()
    }

    fun positive(key: String, label: String): Double {
        val v = num(key, label)
        if (v <= 0.0) throw BadInput("$label must be greater than zero.")
        return v
    }

    fun nonNegative(key: String, label: String): Double {
        val v = num(key, label)
        if (v < 0.0) throw BadInput("$label cannot be negative.")
        return v
    }

    fun int(key: String, label: String): Int {
        val v = num(key, label)
        if (abs(v - v.roundToLong()) > 1e-9) throw BadInput("$label must be a whole number.")
        return v.roundToLong().toInt()
    }

    fun choice(key: String, fallback: String = ""): String =
        raw[key]?.takeIf { it.isNotBlank() } ?: fallback
}

/** Runs a calculator and converts thrown input errors into a displayable outcome. */
fun Calculator.run(values: Map<String, String>): CalcOutcome = try {
    compute(Inputs(values))
} catch (e: BadInput) {
    CalcOutcome.Invalid(e.message ?: "Check your inputs.")
} catch (e: Exception) {
    CalcOutcome.Invalid("That combination of inputs doesn't produce a result.")
}

// ---------------------------------------------------------------------------
// Formatting
// ---------------------------------------------------------------------------

/**
 * Significant-figure formatting with space-grouped thousands.
 *
 * Trailing zeros are stripped only from the fractional part — trimming the
 * whole string turns 1 000 into 1, which is exactly the kind of silent error
 * that makes someone seed a plate a thousandfold wrong.
 */
fun fmt(value: Double, sigFigs: Int = 4): String {
    if (value.isNaN() || value.isInfinite()) return "—"
    if (value == 0.0) return "0"
    val magnitude = log10(abs(value))
    if (magnitude >= 7 || magnitude < -3) return sci(value, sigFigs)

    val decimals = (sigFigs - 1 - kotlin.math.floor(magnitude).toInt()).coerceIn(0, 6)
    var text = String.format("%.${decimals}f", value)
    if (text.contains('.')) text = text.trimEnd('0').trimEnd('.')

    val negative = text.startsWith("-")
    val body = if (negative) text.drop(1) else text
    val integerPart = body.substringBefore('.')
    val fractionPart = body.substringAfter('.', "")
    val grouped = integerPart.reversed().chunked(3).joinToString(" ").reversed()

    return buildString {
        if (negative) append('-')
        append(grouped)
        if (fractionPart.isNotEmpty()) {
            append('.')
            append(fractionPart)
        }
    }
}

fun sci(value: Double, sigFigs: Int = 3): String {
    val exponent = kotlin.math.floor(log10(abs(value))).toInt()
    val mantissa = value / 10.0.pow(exponent)
    var m = String.format("%.${(sigFigs - 1).coerceAtLeast(0)}f", mantissa)
    if (m.contains('.')) m = m.trimEnd('0').trimEnd('.')
    return "$m × 10^$exponent"
}

fun fmtVolume(microliters: Double): String = when {
    abs(microliters) >= 1000 -> "${fmt(microliters / 1000)} mL"
    abs(microliters) < 1 -> "${fmt(microliters * 1000)} nL"
    else -> "${fmt(microliters)} µL"
}

internal fun log2(x: Double) = ln(x) / ln(2.0)
