package com.cinch.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.cinch.shared.domain.Card

/** Formlarda "Neyle ödedin?": nakit ya da kartlardan biri. [selectedId] null = nakit. */
@Composable
fun CardSelector(
    cards: List<Card>,
    selectedId: Long?,
    enabled: Boolean,
    title: String = "Neyle ödedin?",
    onPick: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = cards.firstOrNull { it.id == selectedId }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
                Text(if (selected == null) "💵 Nakit ▾" else "💳 ${selected.name} ▾")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("💵  Nakit") },
                    onClick = {
                        onPick(null)
                        expanded = false
                    },
                )
                cards.forEach { card ->
                    DropdownMenuItem(
                        text = { Text("💳  ${card.name}") },
                        onClick = {
                            onPick(card.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
