package com.labbench.ui.cultures

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.labbench.data.CellLine
import com.labbench.data.Culture
import com.labbench.data.CultureEvent
import com.labbench.data.CultureEventType
import com.labbench.data.CultureStatus
import com.labbench.data.LabRepository
import com.labbench.data.newId
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow

private val dateFormat = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

fun formatWhen(millis: Long?): String =
    millis?.let { dateFormat.format(Date(it)) } ?: "—"

fun relativeAge(millis: Long?): String {
    if (millis == null) return "never"
    val hours = (System.currentTimeMillis() - millis) / 3_600_000.0
    return when {
        hours < 1 -> "just now"
        hours < 24 -> "${hours.toInt()} h ago"
        else -> "${(hours / 24).toInt()} d ago"
    }
}

/** Confluency projected forward from the last recorded value at the line's doubling time. */
fun projectedConfluency(culture: Culture, doublingHours: Double?): Int {
    val recordedAt = culture.confluencyRecordedAt ?: return culture.confluencyPercent
    val doubling = doublingHours ?: return culture.confluencyPercent
    if (doubling <= 0 || culture.confluencyPercent <= 0) return culture.confluencyPercent
    val elapsedHours = (System.currentTimeMillis() - recordedAt) / 3_600_000.0
    val projected = culture.confluencyPercent * 2.0.pow(elapsedHours / doubling)
    return projected.coerceAtMost(100.0).toInt()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CultureListScreen(repository: LabRepository, onOpen: (String) -> Unit) {
    val cultures by repository.activeCultures.collectAsState(initial = emptyList())
    val cellLines by repository.allCellLines.collectAsState(initial = emptyList())
    var showNew by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Cultures") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNew = true }) {
                Icon(Icons.Default.Add, contentDescription = "Start a culture")
            }
        }
    ) { padding ->
        if (cultures.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("No active cultures", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Start one and this board tracks passages, feeds, and when it'll be ready to split.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { showNew = true }) { Text("Start a culture") }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(cultures, key = { it.id }) { culture ->
                    val line = cellLines.firstOrNull { it.id == culture.cellLineId }
                    CultureCard(culture, line) { onOpen(culture.id) }
                }
            }
        }
    }

    if (showNew) {
        NewCultureDialog(
            cellLines = cellLines,
            onDismiss = { showNew = false },
            onCreate = { label, lineId, passage, vessel, interval ->
                scope.launch {
                    repository.startCulture(
                        Culture(
                            id = newId(),
                            label = label,
                            cellLineId = lineId,
                            passage = passage,
                            vessel = vessel,
                            startedAt = System.currentTimeMillis(),
                            lastFedAt = System.currentTimeMillis(),
                            feedIntervalHours = interval,
                            confluencyPercent = 20,
                            confluencyRecordedAt = System.currentTimeMillis()
                        )
                    )
                }
                showNew = false
            }
        )
    }
}

@Composable
private fun CultureCard(culture: Culture, line: CellLine?, onClick: () -> Unit) {
    val projected = projectedConfluency(culture, line?.doublingTimeHours)
    val feedDue = culture.lastFedAt?.let {
        it + culture.feedIntervalHours * 3_600_000L <= System.currentTimeMillis()
    } ?: true
    val overPassage = line?.maxPassage?.let { culture.passage > it } == true

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(culture.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${line?.name ?: "Unknown line"} · P${culture.passage} · ${culture.vessel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "$projected%",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                    color = confluencyColor(projected)
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { projected / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = confluencyColor(projected)
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Fed ${relativeAge(culture.lastFedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (feedDue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (overPassage) {
                    Text(
                        "· past passage limit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun confluencyColor(percent: Int): Color = when {
    percent >= 90 -> MaterialTheme.colorScheme.error
    percent >= 70 -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.tertiary
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CultureDetailScreen(cultureId: String, repository: LabRepository, onBack: () -> Unit) {
    val culture by repository.culture(cultureId).collectAsState(initial = null)
    val events by repository.events(cultureId).collectAsState(initial = emptyList())
    val cellLines by repository.allCellLines.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var showSplit by remember { mutableStateOf(false) }
    var showObserve by remember { mutableStateOf(false) }

    val current = culture
    val line = cellLines.firstOrNull { it.id == current?.cellLineId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.label ?: "Culture") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (current == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("This culture no longer exists.", style = MaterialTheme.typography.titleMedium)
                Text(
                    "It may have been deleted on another device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onBack) { Text("Go back") }
            }
            return@Scaffold
        }

        val projected = projectedConfluency(current, line?.doublingTimeHours)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "$projected%",
                            style = MaterialTheme.typography.displaySmall,
                            color = confluencyColor(projected)
                        )
                        Text(
                            "estimated confluency · measured ${relativeAge(current.confluencyRecordedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        DetailRow("Cell line", line?.name ?: "—")
                        DetailRow("Passage", "P${current.passage}${line?.maxPassage?.let { " of $it" } ?: ""}")
                        DetailRow("Vessel", current.vessel)
                        DetailRow("Last fed", "${formatWhen(current.lastFedAt)} (${relativeAge(current.lastFedAt)})")
                        DetailRow("Last split", formatWhen(current.lastSplitAt))
                        DetailRow("Feed interval", "every ${current.feedIntervalHours} h")
                        DetailRow("Medium", line?.medium.orEmpty().ifBlank { "—" })
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { scope.launch { repository.recordFeed(cultureId) } },
                        modifier = Modifier.weight(1f)
                    ) { Text("Log feed") }
                    OutlinedButton(onClick = { showSplit = true }, modifier = Modifier.weight(1f)) {
                        Text("Log split")
                    }
                    OutlinedButton(onClick = { showObserve = true }, modifier = Modifier.weight(1f)) {
                        Text("Observe")
                    }
                }
            }

            item {
                Text(
                    "HISTORY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (events.isEmpty()) {
                item {
                    Text(
                        "Nothing logged yet. Every feed, split, and observation lands here with a timestamp.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(events, key = { it.id }) { event -> EventRow(event) }
        }
    }

    if (showSplit && current != null) {
        SplitDialog(
            suggestedRatio = line?.splitRatio?.substringAfter(":")?.trim().orEmpty().ifBlank { "4" },
            onDismiss = { showSplit = false },
            onConfirm = { ratio, confluency, note ->
                scope.launch { repository.recordSplit(cultureId, ratio, confluency, note) }
                showSplit = false
            }
        )
    }

    if (showObserve && current != null) {
        ObserveDialog(
            onDismiss = { showObserve = false },
            onConfirm = { confluency, note ->
                scope.launch { repository.recordObservation(cultureId, confluency, note) }
                showObserve = false
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EventRow(event: CultureEvent) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                when (event.type) {
                    CultureEventType.Fed -> "Fed"
                    CultureEventType.Split -> "Split 1:${event.splitRatio.orEmpty()} → P${event.passageAfter}"
                    CultureEventType.Observed -> "Observed"
                    CultureEventType.Frozen -> "Frozen"
                    CultureEventType.Thawed -> "Thawed"
                    CultureEventType.Treated -> "Treated"
                    CultureEventType.Contamination -> "Contamination"
                    CultureEventType.MycoplasmaTest -> "Mycoplasma test"
                },
                style = MaterialTheme.typography.bodyMedium
            )
            if (event.note.isNotBlank()) {
                Text(
                    event.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatWhen(event.occurredAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            event.confluencyPercent?.let {
                Text("$it%", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NewCultureDialog(
    cellLines: List<CellLine>,
    onDismiss: () -> Unit,
    onCreate: (String, String, Int, String, Int) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var selectedLine by remember { mutableStateOf(cellLines.firstOrNull()?.id.orEmpty()) }
    var passage by remember { mutableStateOf("1") }
    var vessel by remember { mutableStateOf("T75") }
    var interval by remember { mutableStateOf("48") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start a culture") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    placeholder = { Text("HeLa flask A") },
                    singleLine = true
                )
                Text("Cell line", style = MaterialTheme.typography.labelSmall)
                LazyColumn(Modifier.height(120.dp)) {
                    items(cellLines, key = { it.id }) { line ->
                        AssistChip(
                            onClick = { selectedLine = line.id },
                            label = { Text(line.name) },
                            leadingIcon = if (line.id == selectedLine) {
                                { Text("✓") }
                            } else null
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = passage,
                        onValueChange = { passage = it.filter(Char::isDigit) },
                        label = { Text("Passage") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = vessel,
                        onValueChange = { vessel = it },
                        label = { Text("Vessel") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it.filter(Char::isDigit) },
                    label = { Text("Feed every (hours)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank() && selectedLine.isNotBlank(),
                onClick = {
                    onCreate(
                        label.trim(),
                        selectedLine,
                        passage.toIntOrNull() ?: 1,
                        vessel.ifBlank { "T75" },
                        interval.toIntOrNull() ?: 48
                    )
                }
            ) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SplitDialog(
    suggestedRatio: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Int?, String) -> Unit
) {
    var ratio by remember { mutableStateOf(suggestedRatio) }
    var confluency by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log a split") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = ratio,
                    onValueChange = { ratio = it },
                    label = { Text("Split ratio 1 :") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = confluency,
                    onValueChange = { confluency = it.filter(Char::isDigit).take(3) },
                    label = { Text("Confluency at split (%)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") }
                )
                Text(
                    "Passage number advances by one and confluency resets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(ratio, confluency.toIntOrNull(), note) }) { Text("Log split") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ObserveDialog(onDismiss: () -> Unit, onConfirm: (Int?, String) -> Unit) {
    var confluency by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log an observation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = confluency,
                    onValueChange = { confluency = it.filter(Char::isDigit).take(3) },
                    label = { Text("Confluency (%)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("What did you see?") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(confluency.toIntOrNull(), note) }) { Text("Log") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
