package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BranchNode
import com.example.data.model.NodeVariant
import com.example.ui.theme.LocalBranchColors
import com.example.ui.theme.stateGradientForIndex

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeStatesSheet(
    node: BranchNode,
    onDismiss: () -> Unit,
    onAddState: () -> Unit,
    onSelectState: (String) -> Unit,
    onRenameState: (String, String) -> Unit,
    onSetDefaultState: (String) -> Unit,
    onDeleteState: (String) -> Unit,
) {
    val branchColors = LocalBranchColors.current
    var renamingVariant by remember { mutableStateOf<NodeVariant?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = branchColors.nodeSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.states_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.states_sheet_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (node.variants.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.states_add_first_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    node.variants.forEach { variant ->
                        val isActive = variant.id == node.activeVariantId
                        val grad = stateGradientForIndex(variant.gradientIndex)
                        val rowBrush = Brush.horizontalGradient(
                            colors = if (branchColors.isDark) {
                                listOf(grad.darkStart, grad.darkEnd)
                            } else {
                                listOf(grad.lightStart, grad.lightEnd)
                            }
                        )

                        Surface(
                            onClick = { onSelectState(variant.id) },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                width = if (isActive) 2.dp else 1.dp,
                                color = if (isActive) grad.accent else branchColors.nodeBorder,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("state_row_${variant.id}"),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(rowBrush)
                                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(grad.accent),
                                    )
                                    Text(
                                        text = variant.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (variant.isDefault) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = grad.accent.copy(alpha = 0.16f),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.states_badge_default),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = grad.accent,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                    if (isActive) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = stringResource(R.string.states_badge_active),
                                            tint = grad.accent,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Set as Default
                                    IconButton(
                                        onClick = { onSetDefaultState(variant.id) },
                                        modifier = Modifier.testTag("state_default_${variant.id}"),
                                    ) {
                                        Icon(
                                            imageVector = if (variant.isDefault) {
                                                Icons.Rounded.Star
                                            } else {
                                                Icons.Outlined.StarOutline
                                            },
                                            contentDescription = stringResource(R.string.states_set_default),
                                            tint = if (variant.isDefault) grad.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(19.dp),
                                        )
                                    }
                                    // Rename
                                    IconButton(
                                        onClick = { renamingVariant = variant },
                                        modifier = Modifier.testTag("state_rename_${variant.id}"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = stringResource(R.string.states_rename),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    // Delete other state
                                    if (node.variants.size > 1) {
                                        IconButton(
                                            onClick = { onDeleteState(variant.id) },
                                            modifier = Modifier.testTag("state_delete_${variant.id}"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = stringResource(R.string.states_delete),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onAddState,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("states_add_button"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(stringResource(R.string.states_add_button))
            }
        }
    }

    renamingVariant?.let { target ->
        var text by remember(target.id) { mutableStateOf(target.label) }
        AlertDialog(
            onDismissRequest = { renamingVariant = null },
            shape = RoundedCornerShape(20.dp),
            title = { Text(stringResource(R.string.states_rename_title)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(60) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("state_rename_input"),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = text.trim()
                        if (clean.isNotEmpty()) {
                            onRenameState(target.id, clean)
                        }
                        renamingVariant = null
                    },
                    enabled = text.trim().isNotEmpty(),
                ) {
                    Text(stringResource(R.string.hub_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingVariant = null }) {
                    Text(stringResource(R.string.hub_cancel))
                }
            },
        )
    }
}
