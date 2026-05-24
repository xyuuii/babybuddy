package com.yueming.baby.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.yueming.baby.ui.motion.BabyMotion
import com.yueming.baby.ui.motion.motionCardPress

object BabyPalette {
    val Rose = Color(0xFFFF5B86)
    val RoseDeep = Color(0xFFE94F76)
    val RoseSoft = Color(0xFFFFEEF3)
    val Blue = Color(0xFF6E9FC3)
    val BlueSoft = Color(0xFFF2F7FB)
    val Gold = Color(0xFFE6A93F)
    val GoldSoft = Color(0xFFFFF7E8)
    val Mint = Color(0xFF63B77D)
    val MintSoft = Color(0xFFF1FAF4)
    val Cream = Color(0xFFFBFBFD)
    val Ink = Color(0xFF1C1C1E)
}

private val BabyPageBackgroundBrush = Brush.verticalGradient(
    listOf(
        Color(0xFFFBFBFD),
        Color(0xFFF7F7F8),
        Color(0xFFFCFCFE)
    )
)

@Composable
private fun babyPageBackgroundBrush(): Brush {
    return if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Brush.verticalGradient(
            listOf(
                Color(0xFF0F0F13),
                Color(0xFF15161A),
                Color(0xFF101014)
            )
        )
    } else {
        BabyPageBackgroundBrush
    }
}

@Composable
fun Modifier.babyPageBackground(): Modifier = background(babyPageBackgroundBrush())

@Composable
fun BabyContentCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable BoxScope.() -> Unit
) {
    BabySoftCard(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.985f),
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
        content = content
    )
}

@Composable
fun BabySoftCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.995f),
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f),
    tonalElevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        tonalElevation = tonalElevation,
        shadowElevation = 1.dp,
        border = BorderStroke(0.55.dp, borderColor)
    ) {
        Box {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.16f),
                                Color.Transparent,
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.06f)
                            )
                        )
                    )
            )
            content()
        }
    }
}

@Composable
fun BabyDynamicContentCard(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    cornerRadius: Dp = 30.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.986f else 1f,
        animationSpec = BabyMotion.cardPressSpring(),
        label = "babyDynamicCardScale"
    )
    val rotation by animateFloatAsState(
        targetValue = if (pressed && onClick != null) -0.35f else 0f,
        animationSpec = BabyMotion.cardPressSpring(),
        label = "babyDynamicCardRotation"
    )
    val corner by animateDpAsState(
        targetValue = if (pressed && onClick != null) cornerRadius + 4.dp else cornerRadius,
        animationSpec = BabyMotion.cardShapeSpring(),
        label = "babyDynamicCardCorner"
    )
    val shadow by animateDpAsState(
        targetValue = if (pressed && onClick != null) 1.dp else 4.dp,
        animationSpec = BabyMotion.cardShapeSpring(),
        label = "babyDynamicCardShadow"
    )
    val borderColor by animateColorAsState(
        targetValue = if (pressed && onClick != null) {
            accent.copy(alpha = 0.26f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
        },
        animationSpec = BabyMotion.colorFadeSpec(),
        label = "babyDynamicCardBorder"
    )
    val shape = RoundedCornerShape(corner)
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            }
            .then(clickableModifier),
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.965f),
        tonalElevation = 0.dp,
        shadowElevation = shadow,
        border = BorderStroke(0.7.dp, borderColor)
    ) {
        Box {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.Transparent,
                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.08f)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 52.dp, y = (-74).dp)
                    .size(190.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                accent.copy(alpha = if (pressed && onClick != null) 0.22f else 0.13f),
                                accent.copy(alpha = 0.055f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-80).dp, y = 58.dp)
                    .size(170.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.055f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )
            content()
        }
    }
}

@Composable
fun BabyGlassFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    content: @Composable BoxScope.() -> Unit
) {
    BabyGlassButton(
        onClick = onClick,
        modifier = modifier.size(66.dp),
        shape = shape,
        role = BabyGlassRole.ClearChrome,
        content = content
    )
}

@Composable
fun BabyLiquidFab(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    contentDescription: String? = label
) {
    val hasLabel = label != null
    BabyGlassButton(
        onClick = onClick,
        modifier = modifier.then(
            if (hasLabel) {
                Modifier.heightIn(min = 62.dp)
            } else {
                Modifier.size(66.dp)
            }
        ),
        shape = RoundedCornerShape(999.dp),
        role = BabyGlassRole.FloatingTabBar,
        useBackdrop = true
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0.035f),
                            Color.Transparent
                        )
                    ),
                    RoundedCornerShape(999.dp)
                )
        )
        Row(
            modifier = Modifier.padding(
                horizontal = if (hasLabel) 22.dp else 0.dp,
                vertical = if (hasLabel) 14.dp else 0.dp
            ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(if (hasLabel) 22.dp else 30.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            if (label != null) {
                Spacer(Modifier.width(7.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun BabyGlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true,
    accent: Color = MaterialTheme.colorScheme.onSurface
) {
    val interactionSource = remember { MutableInteractionSource() }
    val color by animateColorAsState(
        targetValue = if (enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        animationSpec = BabyMotion.colorFadeSpec(),
        label = "babyGlassIconButtonColor"
    )

    BabyGlassSurface(
        modifier = modifier
            .size(44.dp)
            .motionCardPress(interactionSource = interactionSource, enabled = enabled, pressedScale = 0.94f)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        shape = CircleShape,
        role = BabyGlassRole.ClearChrome,
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, Modifier.size(21.dp), tint = color)
    }
}

@Composable
fun BabyGlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val labelColor by animateColorAsState(
        targetValue = when {
            selected -> accent
            enabled -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
        },
        animationSpec = BabyMotion.colorFadeSpec(),
        label = "babyGlassChipLabel"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.30f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
        animationSpec = BabyMotion.colorFadeSpec(),
        label = "babyGlassChipBorder"
    )

    val chipContent: @Composable BoxScope.() -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(15.dp), tint = labelColor)
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (selected) {
        BabyGlassSurface(
            modifier = modifier
                .heightIn(min = 40.dp)
                .motionCardPress(interactionSource = interactionSource, enabled = enabled, pressedScale = 0.96f)
                .clip(RoundedCornerShape(999.dp))
                .clickable(interactionSource, indication = null, enabled = enabled, onClick = onClick),
            shape = RoundedCornerShape(999.dp),
            role = BabyGlassRole.RegularChrome,
            contentAlignment = Alignment.Center,
            content = chipContent
        )
    } else {
        Surface(
            modifier = modifier
                .heightIn(min = 40.dp)
                .motionCardPress(interactionSource = interactionSource, enabled = enabled, pressedScale = 0.96f)
                .clip(RoundedCornerShape(999.dp))
                .clickable(interactionSource, indication = null, enabled = enabled, onClick = onClick),
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            border = BorderStroke(0.7.dp, borderColor),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center, content = chipContent)
        }
    }
}

@Composable
fun BabyGlassSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    BabyGlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        role = BabyGlassRole.RegularChrome,
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onSelected(index) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) MaterialTheme.colorScheme.surface.copy(alpha = 0.62f) else Color.Transparent,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            option,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BabyGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        label = label?.let {
            { Text(it, style = MaterialTheme.typography.labelMedium) }
        },
        placeholder = placeholder?.let {
            { Text(it, style = MaterialTheme.typography.bodyMedium) }
        },
        shape = RoundedCornerShape(22.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.70f),
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.36f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
            disabledBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f)
        )
    )
}

@Composable
fun BabySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val trackColor by animateColorAsState(
        targetValue = when {
            checked -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = BabyMotion.colorFadeSpec(),
        label = "babySwitchTrack"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = BabyMotion.cardShapeSpring(),
        label = "babySwitchThumb"
    )
    Surface(
        modifier = modifier
            .width(54.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        shape = RoundedCornerShape(999.dp),
        color = trackColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            0.7.dp,
            if (checked) Color.White.copy(alpha = 0.34f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            Surface(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(28.dp),
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 2.dp
            ) {}
        }
    }
}

@Composable
fun BabyPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 13.dp)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val containerColor by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = BabyMotion.colorFadeSpec(),
        label = "babyPrimaryButtonContainer"
    )
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .motionCardPress(interactionSource = interactionSource, enabled = enabled, pressedScale = 0.965f)
            .clip(RoundedCornerShape(999.dp))
            .clickable(interactionSource, indication = null, enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BabyDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 13.dp)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val containerColor by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = BabyMotion.colorFadeSpec(),
        label = "babyDangerButtonContainer"
    )
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .motionCardPress(interactionSource = interactionSource, enabled = enabled, pressedScale = 0.965f)
            .clip(RoundedCornerShape(999.dp))
            .clickable(interactionSource, indication = null, enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onError)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BabySecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .heightIn(min = 46.dp)
            .motionCardPress(interactionSource = interactionSource, enabled = enabled, pressedScale = 0.965f)
            .clip(RoundedCornerShape(999.dp))
            .clickable(interactionSource, indication = null, enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f)
            )
        }
    }
}

@Composable
fun BabySettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    BabySoftCard(
        modifier = modifier
            .fillMaxWidth()
            .motionCardPress(interactionSource = interactionSource, pressedScale = 0.978f)
            .clip(RoundedCornerShape(24.dp))
            .clickable(interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BabyIconBubble(icon = icon, accent = accent)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (trailing != null) {
                Row(
                    modifier = Modifier.widthIn(min = 24.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = trailing
                )
            }
        }
    }
}
@Composable
fun BabyPressCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
    pressedColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedColor by animateColorAsState(
        targetValue = if (isPressed && enabled) pressedColor else color,
        animationSpec = BabyMotion.colorFadeSpec(),
        label = "babyPressCardColor"
    )
    val corner by animateDpAsState(
        targetValue = if (isPressed && enabled) 24.dp else 30.dp,
        animationSpec = BabyMotion.cardShapeSpring(),
        label = "babyPressCardCorner"
    )

    BabySoftCard(
        modifier = modifier
            .motionCardPress(interactionSource = interactionSource, enabled = enabled, pressedScale = 0.975f)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        shape = RoundedCornerShape(corner),
        color = animatedColor,
        content = content
    )
}

@Composable
fun BabySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (actionText != null && onAction != null) {
            Text(
                text = actionText,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = BabyPalette.Rose
            )
        }
    }
}

@Composable
fun BabyGlassTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(999.dp)
    BabyGlassTitleBar(
        modifier = modifier,
        shape = shape
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val titleModifier = if (trailing != null) Modifier.weight(1f) else Modifier
            Column(modifier = titleModifier) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun BabyPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = BabyPalette.Rose,
    containerColor: Color = accent.copy(alpha = 0.14f)
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        border = BorderStroke(0.6.dp, accent.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = accent
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BabyBrandWordmark(
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "baby",
                color = BabyPalette.Rose,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "buddy",
                color = BabyPalette.Blue,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BabyIconBubble(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accent: Color = BabyPalette.Rose,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.86f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(21.dp),
            tint = accent.copy(alpha = 0.88f)
        )
    }
}

@Composable
fun BabyMetricChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = BabyPalette.Rose
) {
    BabySoftCard(
        modifier = modifier.height(78.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.62f),
        borderColor = accent.copy(alpha = 0.16f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
