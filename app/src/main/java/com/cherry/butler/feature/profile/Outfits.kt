package com.cherry.butler.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cherry.butler.core.data.Outfit
import com.cherry.butler.core.data.Wardrobe
import com.cherry.butler.core.design.ButlerTheme

/**
 * Outfits under the persona's description (fork feature): tap one to put its text in the
 * field, Save puts it on Janitor. "Save as outfit" keeps what's in the field under a name.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OutfitsSection(
    wardrobe: Wardrobe,
    current: String,
    enabled: Boolean,
    onWear: (Outfit) -> Unit,
    onAdd: (String) -> Unit,
    onRename: (Outfit, String) -> Unit,
    onRemove: (Outfit) -> Unit,
) {
    var naming by remember { mutableStateOf<Outfit?>(null) }
    var adding by remember { mutableStateOf(false) }
    if (adding || naming != null) {
        NameDialog(
            title = if (adding) "Save as outfit" else "Rename outfit",
            initial = naming?.name.orEmpty(),
            onDone = { name -> if (adding) onAdd(name) else naming?.let { onRename(it, name) }; adding = false; naming = null },
            onDismiss = { adding = false; naming = null },
        )
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Checkroom, contentDescription = null, tint = ButlerTheme.colors.textMed, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Outfits", style = MaterialTheme.typography.labelLarge, color = ButlerTheme.colors.textMed)
        }
        Text(
            if (wardrobe.outfits.isEmpty()) "Keep different versions of this description (casual, fight night, injured…) and switch with a tap. You can also switch from a chat's menu."
            else "Tap one to put it in the description, then Save. In a chat, the menu's Outfit puts one on at once.",
            style = MaterialTheme.typography.labelMedium,
            color = ButlerTheme.colors.textLow,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            wardrobe.outfits.forEach { o ->
                val wearing = o.id == wardrobe.wornId
                val inField = o.appearance == current
                OutfitChip(
                    label = o.name + when {
                        wearing && inField -> "  ·  wearing"
                        wearing -> "  ·  edited"
                        else -> ""
                    },
                    selected = inField,
                    enabled = enabled,
                    onClick = { onWear(o) },
                    onRename = { naming = o },
                    onRemove = { onRemove(o) },
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(ButlerTheme.colors.surfaceHigh)
                    .clickable(enabled = enabled && current.isNotBlank()) { adding = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Save as outfit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun OutfitChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit, onRename: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else ButlerTheme.colors.surfaceHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        Box {
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled) { menu = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Outfit options", tint = ButlerTheme.colors.textLow, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Delete", color = ButlerTheme.colors.danger) }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

@Composable
internal fun NameDialog(title: String, initial: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text("Casual, fight night, injured…") }) },
        confirmButton = { TextButton(onClick = { onDone(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
