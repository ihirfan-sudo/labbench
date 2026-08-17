package com.labbench.ui.timers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.labbench.data.LabDatabase
import com.labbench.data.LabRepository
import com.labbench.data.Protocol
import com.labbench.timer.ProtocolTimerService
import com.labbench.timer.formatClock
import com.labbench.timer.remainingSeconds
import com.labbench.ui.theme.MonoDisplay
import kotlinx.coroutines.delay

private val quickPresets = listOf(
    "30 s" to 30,
    "45 s heat shock" to 45,
    "1 min" to 60,
    "3 min trypsin" to 180,
    "5 min spin" to 300,
    "15 min" to 900,
    "1 h" to 3600
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(repository: LabRepository) {
    val context = LocalContext.current
    val db = remember { LabDatabase.get(context) }
    val activeRun by db.timers().activeRun().collectAsState(initial = null)
    val protocols by repository.protocols.collectAsState(initial = emptyList())

    // Recompose once a second while something is running, and not at all when
    // nothing is — the countdown itself lives in the service, not here.
    val remaining by produceState(initialValue = 0, activeRun) {
        while (true) {
            value = activeRun?.let { remainingSeconds(it) } ?: 0
            delay(250)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Timers") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                val run = activeRun
                if (run == null) {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(24.dp)) {
                            Text("Nothing running", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Timers keep counting with the screen off and alert you at every step boundary.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                run.protocolName.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(run.stepTitle, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(16.dp))
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = {
                                        1f - (remaining.toFloat() / run.stepDurationSeconds.coerceAtLeast(1))
                                    },
                                    modifier = Modifier.height(190.dp).fillMaxWidth(),
                                    strokeWidth = 6.dp
                                )
                                Text(
                                    formatClock(remaining),
                                    style = MonoDisplay,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(Modifier.height(20.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    ProtocolTimerService.send(context, ProtocolTimerService.ACTION_PAUSE)
                                }) { Text(if (run.paused) "Resume" else "Pause") }
                                Button(onClick = {
                                    ProtocolTimerService.send(context, ProtocolTimerService.ACTION_NEXT)
                                }) { Text("Next step") }
                                OutlinedButton(onClick = {
                                    ProtocolTimerService.send(context, ProtocolTimerService.ACTION_STOP)
                                }) { Text("Stop") }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "QUICK TIMER",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(quickPresets) { (label, seconds) ->
                        AssistChip(
                            onClick = { ProtocolTimerService.startQuickTimer(context, label, seconds) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            item {
                Text(
                    "PROTOCOLS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(protocols, key = { it.id }) { protocol ->
                ProtocolRow(protocol) { ProtocolTimerService.startProtocol(context, protocol.id) }
            }
        }
    }
}

@Composable
private fun ProtocolRow(protocol: Protocol, onStart: () -> Unit) {
    Card(onClick = onStart, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(protocol.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    protocol.description.ifBlank { protocol.category },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("Start", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
