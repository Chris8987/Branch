package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.StrikethroughS
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BranchNode
import com.example.data.model.WorkMode
import com.example.ui.theme.BranchNodePalette
import com.example.ui.theme.LocalBranchColors
import com.example.ui.theme.StatusDoGlowColor

/**
 * Minimalist Top Control Bar for the Two-Layer Trunk x Branch architecture:
 * - Left: Back-to-Hub + Project Title (on Trunk) OR minimalist translucent Breadcrumb "← Phase" (in Branch).
 * - Right: Pure-icon Ambient Cue mode toggle switch (NO "Draft"/"Exec" text labels!).
 * - Second row: Contextual minimalist node/phase action pill when a node is selected.
 */
@Composable
fun TopControlBar(
    projectTitle: String,
    activeMode: WorkMode,
    drilledBreadcrumbPhaseTitle: String?,
    selectedNode: BranchNode?,
    isSelectedPhase: Boolean,
    canDeleteSelected: Boolean,
    canManageStatesForSelected: Boolean,
    showColorPicker: Boolean,
    onBackToHub: () -> Unit,
    onExitToTrunk: () -> Unit,
    onToggleExecutionMode: (Boolean) -> Unit,
    onToggleColorPicker: () -> Unit,
    onSelectNodeColor: (String) -> Unit,
    onAddBranchToSelectedPhase: () -> Unit,
    onDuplicateNode: () -> Unit,
    onOpenStates: () -> Unit,
    onToggleStrikethrough: () -> Unit,
    onDeleteNode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val branchColors = LocalBranchColors.current
    val isExecutionMode = activeMode == WorkMode.EXECUTION

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (drilledBreadcrumbPhaseTitle != null) {
                // Minimalist translucent Breadcrumb ("← 階段二")
                Surface(
                    onClick = onExitToTrunk,
                    shape = RoundedCornerShape(18.dp),
                    color = branchColors.nodeSurface.copy(alpha = 0.78f),
                    border = BorderStroke(1.dp, branchColors.nodeBorder.copy(alpha = 0.75f)),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .height(40.dp)
                        .widthIn(max = 240.dp)
                        .testTag("branch_breadcrumb_button"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_to_trunk),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = drilledBreadcrumbPhaseTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                // Trunk Overview Left: Back to Hub + Minimalist Project Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Surface(
                        onClick = onBackToHub,
                        shape = CircleShape,
                        color = branchColors.nodeSurface.copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, branchColors.nodeBorder),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("back_to_hub_button"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back_to_hub),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = branchColors.nodeSurface.copy(alpha = 0.78f),
                        border = BorderStroke(1.dp, branchColors.nodeBorder.copy(alpha = 0.7f)),
                        modifier = Modifier.widthIn(max = 210.dp),
                    ) {
                        Text(
                            text = projectTitle,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
            }

            // Pure-Icon Ambient Cue Mode Toggle Switch (Zero Draft/Exec Text!)
            AmbientModeToggleSwitch(
                isExecutionMode = isExecutionMode,
                onToggle = onToggleExecutionMode,
            )
        }

        // Contextual Selected Node / Phase Toolbar
        AnimatedVisibility(
            visible = selectedNode != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            selectedNode?.let { node ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ContextualNodeToolbar(
                        node = node,
                        isSelectedPhase = isSelectedPhase,
                        canDelete = canDeleteSelected,
                        canManageStates = canManageStatesForSelected,
                        showColorPicker = showColorPicker,
                        onToggleColorPicker = onToggleColorPicker,
                        onAddBranchToPhase = onAddBranchToSelectedPhase,
                        onDuplicate = onDuplicateNode,
                        onOpenStates = onOpenStates,
                        onToggleStrikethrough = onToggleStrikethrough,
                        onDelete = onDeleteNode,
                    )

                    AnimatedVisibility(
                        visible = showColorPicker,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        ColorPaletteRow(
                            selectedHex = node.color,
                            onSelectHex = onSelectNodeColor,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pure-icon Ambient Cue Toggle Switch:
 * - NO text labels ("Draft" / "Exec" removed completely).
 * - Off: Soft matte track with subtle outline pen icon.
 * - On: Gently illuminated glowing track & luminous thumb with lit bolt spark.
 */
@Composable
private fun AmbientModeToggleSwitch(
    isExecutionMode: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val branchColors = LocalBranchColors.current
    val toggleDesc = stringResource(R.string.cd_ambient_mode_toggle)

    val trackColor by animateColorAsState(
        targetValue = if (isExecutionMode) {
            if (branchColors.isDark) Color(0xFF312412) else Color(0xFFFFF4D6)
        } else {
            branchColors.nodeSurface.copy(alpha = 0.92f)
        },
        animationSpec = tween(260),
        label = "ambient_track_color",
    )
    val trackBorder by animateColorAsState(
        targetValue = if (isExecutionMode) {
            StatusDoGlowColor.copy(alpha = 0.85f)
        } else {
            branchColors.nodeBorder
        },
        animationSpec = tween(260),
        label = "ambient_track_border",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (isExecutionMode) 28.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "ambient_thumb_x",
    )
    val thumbBg by animateColorAsState(
        targetValue = if (isExecutionMode) {
            StatusDoGlowColor
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(260),
        label = "ambient_thumb_bg",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 64.dp, height = 48.dp) // 48dp accessible vertical touch target
            .semantics { contentDescription = toggleDesc }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onClick = { onToggle(!isExecutionMode) },
            )
            .testTag("ambient_mode_toggle"),
    ) {
        // Outer luminous glow ring when Execution Mode is active
        if (isExecutionMode) {
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(38.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(19.dp),
                        ambientColor = StatusDoGlowColor,
                        spotColor = StatusDoGlowColor,
                    )
                    .clip(RoundedCornerShape(19.dp))
                    .background(StatusDoGlowColor.copy(alpha = 0.14f)),
            )
        }

        // Main pill switch track
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(trackColor)
                .border(1.2.dp, trackBorder, RoundedCornerShape(17.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            // Subtle background ghost icons inside track
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isExecutionMode) 0.35f else 0f),
                    modifier = Modifier.size(13.dp),
                )
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = StatusDoGlowColor.copy(alpha = if (isExecutionMode) 0f else 0.45f),
                    modifier = Modifier.size(14.dp),
                )
            }

            // Sliding circular thumb
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(26.dp)
                    .shadow(
                        elevation = if (isExecutionMode) 6.dp else 1.5.dp,
                        shape = CircleShape,
                        ambientColor = if (isExecutionMode) StatusDoGlowColor else Color.Black,
                        spotColor = if (isExecutionMode) StatusDoGlowColor else Color.Black,
                    )
                    .clip(CircleShape)
                    .background(thumbBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isExecutionMode) Icons.Rounded.Bolt else Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = if (isExecutionMode) Color(0xFF1F1604) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
private fun ContextualNodeToolbar(
    node: BranchNode,
    isSelectedPhase: Boolean,
    canDelete: Boolean,
    canManageStates: Boolean,
    showColorPicker: Boolean,
    onToggleColorPicker: () -> Unit,
    onAddBranchToPhase: () -> Unit,
    onDuplicate: () -> Unit,
    onOpenStates: () -> Unit,
    onToggleStrikethrough: () -> Unit,
    onDelete: () -> Unit,
) {
    val branchColors = LocalBranchColors.current
    var showOverflowMenu by remember(node.id) { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = branchColors.nodeSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, branchColors.nodeBorder),
        shadowElevation = 4.dp,
        modifier = Modifier.testTag("node_selection_toolbar"),
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isSelectedPhase) {
                Surface(
                    onClick = onAddBranchToPhase,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("toolbar_add_branch_button"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.trunk_add_branch),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            IconButton(
                onClick = onToggleColorPicker,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("toolbar_color_button"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = stringResource(R.string.action_color),
                    tint = if (showColorPicker) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }

            if (canDelete) {
                IconButton(
                    onClick = onDuplicate,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("toolbar_duplicate_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.action_duplicate),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { showOverflowMenu = true },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("toolbar_more_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = stringResource(R.string.action_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }

                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false },
                ) {
                    if (canManageStates) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_states)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Layers,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                showOverflowMenu = false
                                onOpenStates()
                            },
                            modifier = Modifier.testTag("menu_states_item"),
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    }

                    // Soft Delete (Strikethrough) placed directly ABOVE Delete
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(
                                    if (node.isStrikethrough) R.string.action_un_strikethrough
                                    else R.string.action_strikethrough
                                ),
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.StrikethroughS,
                                contentDescription = null,
                                tint = if (node.isStrikethrough) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            showOverflowMenu = false
                            onToggleStrikethrough()
                        },
                        modifier = Modifier.testTag("menu_strikethrough_item"),
                    )

                    if (canDelete) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(
                                        if (isSelectedPhase) R.string.action_delete_phase
                                        else R.string.action_delete_node
                                    ),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                showOverflowMenu = false
                                onDelete()
                            },
                            modifier = Modifier.testTag("menu_delete_node_item"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPaletteRow(
    selectedHex: String,
    onSelectHex: (String) -> Unit,
) {
    val branchColors = LocalBranchColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = branchColors.nodeSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, branchColors.nodeBorder),
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BranchNodePalette.forEach { option ->
                val isChosen = option.hex.equals(selectedHex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(option.color)
                        .border(
                            width = if (isChosen) 2.5.dp else 1.dp,
                            color = if (isChosen) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.5f),
                            shape = CircleShape,
                        )
                        .clickable { onSelectHex(option.hex) }
                        .testTag("color_swatch_${option.hex}"),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isChosen) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = option.label,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
    }
}
