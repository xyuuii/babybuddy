package com.yueming.baby.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yueming.baby.ui.motion.BabyMotion
import com.yueming.baby.ui.motion.motionCardPress

object BabyPalette {
    val Rose = Color(0xFFFF7E9A)
    val RoseDeep = Color(0xFFE94F76)
    val RoseSoft = Color(0xFFFFE4EC)
    val Blue = Color(0xFF7EA9C8)
    val BlueSoft = Color(0xFFEAF4FC)
    val Gold = Color(0xFFFFC56F)
    val GoldSoft = Color(0xFFFFF2D9)
    val Mint = Color(0xFF74C58F)
    val MintSoft = Color(0xFFEAF8EE)
    val Cream = Color(0xFFFFF8F2)
    val Ink = Color(0xFF2F262A)
}
fun Modifier.babyPageBackground(): Modifier = background(
    Brush.verticalGradient(
        listOf(
            Color(0xFFFFFAF4),
            Color(0xFFFFF6F0),
            Color(0xFFFFFBF8)
        )
    )
)

@Composable
fun BabySoftCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(30.dp),
    color: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f),
    tonalElevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        tonalElevation = tonalElevation,
        shadowElevation = 0.dp,
        border = BorderStroke(0.7.dp, borderColor)
    ) {
        Box(content = content)
    }
}
@Composable
fun BabyPressCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(30.dp),
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
fun BabyIllustrationCard(
    @DrawableRes imageRes: Int,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    accent: Color = BabyPalette.Rose,
    onClick: (() -> Unit)? = null
) {
    val content: @Composable BoxScope.() -> Unit = {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.72f), Color.White.copy(alpha = 0.18f))
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (badge != null) {
                BabyPill(text = badge, accent = accent, containerColor = Color.White.copy(alpha = 0.76f))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (onClick == null) {
        BabySoftCard(
            modifier = modifier,
            shape = RoundedCornerShape(30.dp),
            color = BabyPalette.RoseSoft.copy(alpha = 0.72f),
            borderColor = accent.copy(alpha = 0.18f),
            content = content
        )
    } else {
        BabyPressCard(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(30.dp),
            color = BabyPalette.RoseSoft.copy(alpha = 0.72f),
            pressedColor = accent.copy(alpha = 0.12f),
            content = content
        )
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
            .background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(21.dp),
            tint = accent
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
