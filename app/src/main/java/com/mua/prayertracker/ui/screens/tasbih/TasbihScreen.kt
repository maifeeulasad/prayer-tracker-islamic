package com.mua.prayertracker.ui.screens.tasbih

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maifee.velvet.component.button.ButtonType
import com.maifee.velvet.component.button.VelvetButton
import com.maifee.velvet.component.card.VelvetCard
import com.mua.prayertracker.ui.theme.GoldenAccent
import com.mua.prayertracker.ui.theme.IslamicGreen

/**
 * Tasbih (dhikr) counter.
 *
 * In-memory only — the count resets when the app process is killed. Persisting
 * targets, named dhikrs and history is a follow-up.
 */
@Composable
fun TasbihScreen() {
    var count by remember { mutableIntStateOf(0) }
    var target by remember { mutableIntStateOf(33) }

    val targets = listOf(33, 99, 100, 500)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VelvetCard(
            modifier = Modifier.fillMaxWidth(),
            title = "Tasbih",
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Target",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    targets.forEach { t ->
                        val selected = t == target
                        VelvetButton(
                            onClick = { target = t },
                            type = if (selected) ButtonType.Primary else ButtonType.Default,
                        ) { Text(t.toString()) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Not saved — resets when the app closes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(CircleShape)
                .background(IslamicGreen)
                .clickable { count += 1 },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = count.toString(),
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "of $target",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        val cycles = if (target > 0) count / target else 0
        val remainder = if (target > 0) count % target else count
        Text(
            text = "Cycles: $cycles  ·  Current: $remainder / $target",
            style = MaterialTheme.typography.bodyMedium,
            color = GoldenAccent,
            fontWeight = FontWeight.Medium,
        )

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VelvetButton(
                onClick = { count = 0 },
                type = ButtonType.Default,
                icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
            ) { Text("Reset") }

            VelvetButton(
                onClick = { if (count > 0) count -= 1 },
                type = ButtonType.Default,
                disabled = count == 0,
            ) { Text("Undo") }
        }
    }
}
