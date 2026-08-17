package com.labbench.ui.today

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.labbench.data.LabRepository
import com.labbench.data.TodaySnapshot
import com.labbench.ui.cultures.relativeAge
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    repository: LabRepository,
    onOpenCulture: (String) -> Unit,
    onOpenTimers: () -> Unit
) {
    val snapshot by repository.todayBoard().collectAsState(initial = TodaySnapshot())
    val scope = rememberCoroutineScope()
    val today = remember { SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date()) }

    Scaffold(topBar = { TopAppBar(title = { Text("Today") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text(today, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${snapshot.activeCultureCount} active cultures",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (snapshot.feedsDue.isEmpty() && snapshot.tasksDue.isEmpty() && snapshot.expiringLots.isEmpty()) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text("Nothing is due", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Feeds, tasks, and expiring stock appear here as they come up.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (snapshot.feedsDue.isNotEmpty()) {
                item { SectionHeader("FEEDS DUE") }
                items(snapshot.feedsDue, key = { it.id }) { culture ->
                    Card(
                        onClick = { onOpenCulture(culture.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(culture.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "P${culture.passage} · fed ${relativeAge(culture.lastFedAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            TextButton(onClick = { scope.launch { repository.recordFeed(culture.id) } }) {
                                Text("Log feed")
                            }
                        }
                    }
                }
            }

            if (snapshot.tasksDue.isNotEmpty()) {
                item { SectionHeader("TASKS") }
                items(snapshot.tasksDue, key = { it.id }) { task ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = task.done,
                            onCheckedChange = { scope.launch { repository.completeTask(task) } }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(task.title, style = MaterialTheme.typography.bodyLarge)
                            task.dueAt?.let {
                                Text(
                                    "due ${relativeAge(it)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (snapshot.expiringLots.isNotEmpty()) {
                item { SectionHeader("EXPIRING SOON") }
                items(snapshot.expiringLots, key = { it.id }) { lot ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Lot ${lot.lotNumber}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            relativeAge(lot.expiresAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp)
    )
}
