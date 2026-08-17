package com.labbench.ui.calculators

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labbench.calc.CalcCategory
import com.labbench.calc.CalcField
import com.labbench.calc.CalcOutcome
import com.labbench.calc.Calculator
import com.labbench.calc.CalculatorCatalog
import com.labbench.calc.FieldKind
import com.labbench.calc.run
import com.labbench.data.LabRepository
import com.labbench.ui.theme.ReadoutStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorHubScreen(onOpen: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { CalculatorCatalog.search(query) }
    val grouped = remember(results) { results.groupBy { it.category } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Calculators") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search ${CalculatorCatalog.all.size} calculators") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (results.isEmpty()) {
                item {
                    Text(
                        "Nothing matches \"$query\". Try a shorter word — \"dilution\", \"seed\", \"Tm\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
            }
            CalcCategory.entries.forEach { category ->
                val entries = grouped[category].orEmpty()
                if (entries.isEmpty()) return@forEach
                item(key = "header-${category.name}") {
                    Text(
                        category.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                items(entries, key = { it.id }) { calculator ->
                    Card(
                        onClick = { onOpen(calculator.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(calculator.name, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                calculator.blurb,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorDetailScreen(
    calculatorId: String,
    repository: LabRepository,
    onBack: () -> Unit
) {
    val calculator = remember(calculatorId) { CalculatorCatalog.byId(calculatorId) }
    if (calculator == null) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("That calculator isn't available.", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onBack) { Text("Go back") }
        }
        return
    }

    val values = remember(calculatorId) {
        mutableStateMapOf<String, String>().apply {
            calculator.fields.forEach { put(it.key, it.default) }
        }
    }
    var outcome by remember(calculatorId) { mutableStateOf<CalcOutcome?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Live recalculation: the result updates as you type, so there is no "=" to
    // forget to press when your hands are full.
    LaunchedEffect(values.toMap()) {
        outcome = calculator.run(values.toMap())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(calculator.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    calculator.blurb,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item { ResultPanel(outcome) }

            items(calculator.fields, key = { it.key }) { field ->
                FieldEditor(
                    field = field,
                    value = values[field.key].orEmpty(),
                    onChange = { values[field.key] = it }
                )
            }

            item {
                val result = outcome
                FilledTonalButton(
                    onClick = {
                        val summary = (result as? CalcOutcome.Ok)
                            ?.lines?.joinToString("; ") { "${it.label}: ${it.value}" }
                        if (summary == null) {
                            scope.launch { snackbar.showSnackbar("Fix the inputs before saving to the notebook.") }
                        } else {
                            scope.launch {
                                repository.logCalculation(
                                    calculator.id, calculator.name, values.toMap(), summary
                                )
                                snackbar.showSnackbar("Saved to notebook")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Bookmark, contentDescription = null)
                    Text("  Save result to notebook")
                }
            }
        }
    }
}

@Composable
private fun ResultPanel(outcome: CalcOutcome?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(18.dp)) {
            when (outcome) {
                null -> Text("Enter values to see a result.", style = MaterialTheme.typography.bodyMedium)

                is CalcOutcome.Invalid -> Text(
                    outcome.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                is CalcOutcome.Ok -> {
                    outcome.lines.forEachIndexed { index, line ->
                        if (line.primary) {
                            Text(
                                line.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                line.value,
                                style = ReadoutStyle.copy(fontSize = 30.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(10.dp))
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    line.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    line.value,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                                )
                            }
                        }
                        if (index == 0 && outcome.lines.size > 1) {
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        }
                    }
                    if (outcome.warnings.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        outcome.warnings.forEach { warning ->
                            Row(Modifier.padding(vertical = 4.dp)) {
                                Spacer(
                                    Modifier
                                        .width(3.dp)
                                        .height(18.dp)
                                        .background(MaterialTheme.colorScheme.error)
                                )
                                Text(
                                    warning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldEditor(field: CalcField, value: String, onChange: (String) -> Unit) {
    when (field.kind) {
        FieldKind.Choice -> {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(field.label) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(
                        androidx.compose.material3.MenuAnchorType.PrimaryNotEditable
                    )
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    field.options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = { onChange(option); expanded = false }
                        )
                    }
                }
            }
        }

        else -> OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(field.label) },
            suffix = field.unit?.let { { Text(it) } },
            supportingText = field.help?.let { { Text(it) } },
            singleLine = field.kind != FieldKind.Sequence,
            keyboardOptions = KeyboardOptions(
                keyboardType = when (field.kind) {
                    FieldKind.Integer -> KeyboardType.Number
                    FieldKind.Decimal -> KeyboardType.Decimal
                    else -> KeyboardType.Text
                }
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
