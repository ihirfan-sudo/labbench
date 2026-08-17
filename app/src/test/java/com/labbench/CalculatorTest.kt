package com.labbench

import com.labbench.calc.CalcOutcome
import com.labbench.calc.CalculatorCatalog
import com.labbench.calc.Inputs
import com.labbench.calc.c1v1
import com.labbench.calc.rcfFromRpm
import com.labbench.calc.rpmFromRcf
import com.labbench.calc.run
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberParsingTest {

    @Test
    fun `comma decimals parse like periods`() {
        assertEquals(0.5, Inputs(mapOf("x" to "0,5")).num("x"), 1e-9)
    }

    @Test
    fun `grouped digits parse`() {
        assertEquals(1_000_000.0, Inputs(mapOf("x" to "1 000 000")).num("x"), 1e-9)
    }

    @Test
    fun `scientific notation parses`() {
        assertEquals(1e6, Inputs(mapOf("x" to "1e6")).num("x"), 1e-9)
    }

    @Test
    fun `blank input yields null rather than zero`() {
        assertEquals(null, Inputs(mapOf("x" to "  ")).numOrNull("x"))
    }
}

class FormulaTest {

    @Test
    fun `c1v1 solves for stock volume`() {
        assertEquals(1.0, c1v1(c1 = 10.0, c2 = 1.0, v2 = 10.0), 1e-9)
    }

    @Test
    fun `rcf matches the published relationship`() {
        // 3000 RPM at a 100 mm radius is about 1006 x g.
        assertEquals(1006.0, rcfFromRpm(3000.0, 100.0), 1.0)
    }

    @Test
    fun `rcf and rpm round trip`() {
        val rcf = rcfFromRpm(4500.0, 85.0)
        assertEquals(4500.0, rpmFromRcf(rcf, 85.0), 1e-6)
    }
}

class CalculatorCatalogTest {

    @Test
    fun `every calculator has a unique id`() {
        val ids = CalculatorCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every calculator produces something from its own defaults`() {
        CalculatorCatalog.all.forEach { calculator ->
            val defaults = calculator.fields.associate { it.key to it.default }
            val outcome = calculator.run(defaults)
            assertTrue(
                "${calculator.name} failed on its own defaults: $outcome",
                outcome is CalcOutcome.Ok || calculator.fields.any { it.default.isBlank() }
            )
        }
    }

    @Test
    fun `hemocytometer applies the ten thousand factor`() {
        val outcome = CalculatorCatalog.byId("hemocytometer")!!.run(
            mapOf("counted" to "100", "squares" to "4", "dilution" to "2", "volume" to "10")
        )
        // 25 per square x 2 x 1e4 = 5e5 cells/mL
        assertTrue(outcome is CalcOutcome.Ok)
        val value = (outcome as CalcOutcome.Ok).lines.first().value
        assertTrue("got $value", value.contains("5 × 10^5"))
    }

    @Test
    fun `seeding refuses an impossible dilution instead of returning a negative volume`() {
        val outcome = CalculatorCatalog.byId("seeding")!!.run(
            mapOf(
                "stock" to "1000", "vessel" to "96-well", "count" to "96",
                "target" to "1e6", "overage" to "0"
            )
        )
        assertTrue(outcome is CalcOutcome.Invalid)
    }

    @Test
    fun `viability flags a low result`() {
        val outcome = CalculatorCatalog.byId("viability")!!.run(
            mapOf("live" to "60", "dead" to "40", "squares" to "4", "dilution" to "2")
        ) as CalcOutcome.Ok
        assertTrue(outcome.lines.first().value.startsWith("60"))
        assertTrue(outcome.warnings.any { it.contains("80%") })
    }

    @Test
    fun `primer tm rejects unsupported bases`() {
        val outcome = CalculatorCatalog.byId("primer_tm")!!.run(mapOf("seq" to "ATGCXX"))
        assertTrue(outcome is CalcOutcome.Invalid)
    }

    @Test
    fun `primer tm computes reverse complement`() {
        val outcome = CalculatorCatalog.byId("primer_tm")!!.run(
            mapOf("seq" to "ATGCATGCATGCATGC")
        ) as CalcOutcome.Ok
        val revComp = outcome.lines.first { it.label == "Reverse complement" }.value
        assertEquals("GCATGCATGCATGCAT", revComp)
    }

    @Test
    fun `cfu warns outside the countable range`() {
        val outcome = CalculatorCatalog.byId("cfu")!!.run(
            mapOf("colonies" to "12", "plated" to "100", "dilution" to "1e5")
        ) as CalcOutcome.Ok
        assertTrue(outcome.warnings.any { it.contains("30") })
    }

    @Test
    fun `western loading refuses to overfill the well`() {
        val outcome = CalculatorCatalog.byId("western_loading")!!.run(
            mapOf("conc" to "0.1", "target" to "50", "wellVolume" to "30", "bufferX" to "4×")
        )
        assertTrue(outcome is CalcOutcome.Invalid)
    }

    @Test
    fun `doubling time is correct for a clean four fold expansion`() {
        val outcome = CalculatorCatalog.byId("doubling")!!.run(
            mapOf("seeded" to "1e5", "harvested" to "4e5", "hours" to "48")
        ) as CalcOutcome.Ok
        // Two doublings in 48 h means 24 h per doubling.
        assertTrue(outcome.lines.first().value.startsWith("24"))
    }
}
