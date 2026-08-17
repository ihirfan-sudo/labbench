package com.labbench.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labbench.data.LabRepository
import com.labbench.data.Reagent
import com.labbench.data.StorageKind
import com.labbench.data.StorageNode
import com.labbench.data.Vial
import com.labbench.data.VialKind
import com.labbench.data.newId
import kotlinx.coroutines.launch

private enum class InventoryTab(val label: String) { Freezer("Freezer"), Reagents("Reagents") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(repository: LabRepository) {
    var tab by remember { mutableStateOf(InventoryTab.Freezer) }
    // Empty path = top level. Each entry is a node we've drilled into.
    var path by remember { mutableStateOf(listOf<StorageNode>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(path.lastOrNull()?.name ?: "Inventory") },
                navigationIcon = {
                    if (path.isNotEmpty()) {
                        IconButton(onClick = { path = path.dropLast(1) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up one level")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (path.isEmpty()) {
                SingleChoiceSegmentedButtonRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    InventoryTab.entries.forEachIndexed { index, entry ->
                        SegmentedButton(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            shape = SegmentedButtonDefaults.itemShape(index, InventoryTab.entries.size)
                        ) { Text(entry.label) }
                    }
                }
            }

            val current = path.lastOrNull()
            when {
                current?.kind == StorageKind.Box -> BoxGrid(repository, current)
                path.isNotEmpty() || tab == InventoryTab.Freezer ->
                    StorageLevel(repository, current) { path = path + it }
                else -> ReagentList(repository)
            }
        }
    }
}

@Composable
private fun StorageLevel(
    repository: LabRepository,
    parent: StorageNode?,
    onOpen: (StorageNode) -> Unit
) {
    val children by repository.children(parent?.id).collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (children.isEmpty()) {
                item {
                    Column(Modifier.padding(top = 40.dp)) {
                        Text(
                            if (parent == null) "No storage set up yet" else "${parent.name} is empty",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Build the hierarchy the way your lab is actually laid out: a freezer holds racks, a rack holds boxes, a box holds vials at fixed positions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(children, key = { it.id }) { node ->
                Card(
                    onClick = { onOpen(node) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(node.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                buildString {
                                    append(node.kind.name)
                                    if (node.temperature.isNotBlank()) append(" · ${node.temperature}")
                                    if (node.kind == StorageKind.Box) append(" · ${node.rows}×${node.columns}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "Add storage") }
    }

    if (showAdd) {
        AddStorageDialog(
            parent = parent,
            onDismiss = { showAdd = false },
            onCreate = { name, kind, temp, rows, columns ->
                scope.launch {
                    repository.upsertNode(
                        StorageNode(
                            id = newId(),
                            parentId = parent?.id,
                            name = name,
                            kind = kind,
                            temperature = temp,
                            rows = rows,
                            columns = columns
                        )
                    )
                }
                showAdd = false
            }
        )
    }
}

/**
 * A box rendered as its real grid. Position labels match what's printed on the
 * physical box (A1 top-left), so what's on screen matches what's in your hand.
 */
@Composable
private fun BoxGrid(repository: LabRepository, box: StorageNode) {
    val vials by repository.vialsInBox(box.id).collectAsState(initial = emptyList())
    val byPosition = remember(vials) { vials.associateBy { it.position } }
    var selected by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "${vials.size} of ${box.rows * box.columns} positions filled",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(box.columns.coerceAtLeast(1)),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items((0 until box.rows * box.columns).toList()) { index ->
                val row = index / box.columns
                val column = index % box.columns
                val position = "${'A' + row}${column + 1}"
                val vial = byPosition[position]
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (vial != null) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { selected = position },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        vial?.label?.take(4) ?: position,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }
        }
    }

    selected?.let { position ->
        val vial = byPosition[position]
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Position $position") },
            text = {
                if (vial == null) {
                    Text("Empty. Add a vial here from the freeze flow.")
                } else {
                    Column {
                        Text(vial.label, style = MaterialTheme.typography.titleMedium)
                        Text("${vial.kind.name}${vial.passage?.let { " · P$it" } ?: ""}")
                        if (vial.notes.isNotBlank()) Text(vial.notes)
                    }
                }
            },
            confirmButton = {
                if (vial != null) {
                    TextButton(onClick = {
                        scope.launch { repository.consumeVial(vial, "Taken from $position") }
                        selected = null
                    }) { Text("Take out") }
                } else {
                    TextButton(onClick = { selected = null }) { Text("Close") }
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ReagentList(repository: LabRepository) {
    val reagents by repository.reagents.collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (reagents.isEmpty()) {
                item {
                    Column(Modifier.padding(top = 40.dp)) {
                        Text("No reagents yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Add what you actually use. Each reagent can carry several lots with their own expiry dates.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(reagents, key = { it.id }) { reagent ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(reagent.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(
                                reagent.vendor.ifBlank { null },
                                reagent.catalogNumber.ifBlank { null },
                                reagent.storageTemp.ifBlank { null }
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "Add reagent") }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var vendor by remember { mutableStateOf("") }
        var catalog by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add a reagent") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(vendor, { vendor = it }, label = { Text("Vendor") }, singleLine = true)
                    OutlinedTextField(catalog, { catalog = it }, label = { Text("Catalog number") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch {
                        repository.upsertReagent(Reagent(newId(), name.trim(), vendor.trim(), catalog.trim()))
                    }
                    showAdd = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AddStorageDialog(
    parent: StorageNode?,
    onDismiss: () -> Unit,
    onCreate: (String, StorageKind, String, Int, Int) -> Unit
) {
    val suggested = when (parent?.kind) {
        null -> StorageKind.Freezer
        StorageKind.Freezer, StorageKind.Tank -> StorageKind.Rack
        StorageKind.Rack, StorageKind.Shelf -> StorageKind.Box
        StorageKind.Box -> StorageKind.Box
    }
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(suggested) }
    var temperature by remember { mutableStateOf(parent?.temperature ?: "−80 °C") }
    var rows by remember { mutableStateOf("9") }
    var columns by remember { mutableStateOf("9") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (parent == null) "Add storage" else "Add inside ${parent.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StorageKind.entries.forEach { option ->
                        TextButton(onClick = { kind = option }) {
                            Text(
                                option.name,
                                color = if (option == kind) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (kind == StorageKind.Box) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            rows, { rows = it.filter(Char::isDigit).take(2) },
                            label = { Text("Rows") }, singleLine = true, modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            columns, { columns = it.filter(Char::isDigit).take(2) },
                            label = { Text("Columns") }, singleLine = true, modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    OutlinedTextField(
                        temperature, { temperature = it },
                        label = { Text("Temperature") }, singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onCreate(
                    name.trim(), kind, temperature,
                    rows.toIntOrNull() ?: 9, columns.toIntOrNull() ?: 9
                )
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
