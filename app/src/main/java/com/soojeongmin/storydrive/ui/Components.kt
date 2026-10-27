package com.soojeongmin.storydrive.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soojeongmin.storydrive.R

enum class ButtonKind { Primary, Secondary }

@Composable
private fun pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(Motion.FAST_MS, easing = Motion.easing), label = "press")
    return Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
}

@Composable
private fun pressColor(interaction: MutableInteractionSource, normal: Color, pressed: Color): Color {
    val isPressed by interaction.collectIsPressedAsState()
    val color by animateColorAsState(if (isPressed) pressed else normal, tween(Motion.FAST_MS, easing = Motion.easing), label = "pressColor")
    return color
}

@Composable
fun PButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val t = LocalTokens.current
    val interaction = remember { MutableInteractionSource() }
    val secondary = kind == ButtonKind.Secondary
    val fill = when {
        !enabled -> Color.Transparent
        secondary -> pressColor(interaction, t.btnFilled, t.btnFilledPressed)
        else -> pressColor(interaction, t.btnBasic, t.btnBasicPressed)
    }
    val content = when {
        !enabled -> t.text4
        secondary -> t.onFilled
        else -> t.text
    }
    Row(
        modifier
            .then(pressScale(interaction))
            .defaultMinSize(minWidth = 140.dp, minHeight = 100.dp)
            .clip(Radius.l)
            .background(fill)
            .let { if (enabled) it else it.border(2.dp, t.divider2, Radius.l) }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.xxl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.CenterHorizontally),
    ) {
        icon?.let { Icon(painterResource(it), contentDescription = null, tint = content, modifier = Modifier.size(52.dp)) }
        Text(text, style = Type.buttonL, color = content)
    }
}

@Composable
fun PIconButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    kind: ButtonKind = ButtonKind.Primary,
    size: Dp = 80.dp,
    shape: Shape = CircleShape,
) {
    val t = LocalTokens.current
    val interaction = remember { MutableInteractionSource() }
    val secondary = kind == ButtonKind.Secondary
    val fill = if (secondary) pressColor(interaction, t.btnFilled, t.btnFilledPressed) else pressColor(interaction, t.btnBasic, t.btnBasicPressed)
    Box(
        Modifier
            .then(pressScale(interaction))
            .size(size)
            .clip(shape)
            .background(fill)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = if (secondary) t.onFilled else t.text, modifier = Modifier.size(size * 0.7f))
    }
}

@Composable
fun PTextButton(text: String, onClick: () -> Unit, @DrawableRes icon: Int? = null) {
    val t = LocalTokens.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .then(pressScale(interaction))
            .defaultMinSize(minHeight = 64.dp)
            .clip(Radius.s)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        icon?.let { Icon(painterResource(it), contentDescription = null, tint = t.text, modifier = Modifier.size(32.dp)) }
        Text(text, style = Type.bodyLBold, color = t.text)
    }
}

@Composable
fun PTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = Type.field.copy(color = t.text),
        cursorBrush = SolidColor(t.text),
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 100.dp)
            .clip(Radius.m)
            .background(t.fieldBg)
            .border(if (focused) 3.dp else 2.dp, if (focused) t.fieldFocus else t.divider2, Radius.m)
            .padding(horizontal = Space.xl, vertical = 28.dp),
    )
}

@Composable
fun PRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val t = LocalTokens.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 112.dp)
            .clip(Radius.m)
            .background(pressColor(interaction, Color.Transparent, t.btnBasic))
            .clickable(interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Space.ml, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.White else Color.Transparent)
                    .border(if (selected) 12.dp else 4.dp, if (selected) t.active else t.divider3, CircleShape),
            )
        }
        Text(label, style = if (selected) Type.bodyLBold else Type.bodyL, color = t.text)
    }
}

@Composable
fun ListContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().card().padding(horizontal = Space.l, vertical = Space.ml)) { content() }
}

@Composable
fun Modifier.card(shape: Shape = Radius.l): Modifier {
    val t = LocalTokens.current
    return this
        .shadow(6.dp, shape, ambientColor = t.shadow, spotColor = t.shadow)
        .clip(shape)
        .background(t.surface)
}

@Composable
fun ThinProgress(fraction: Float, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)).background(t.divider2)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(999.dp)).background(t.btnFilled))
    }
}

@Composable
fun Divider() = HorizontalDivider(thickness = 1.dp, color = LocalTokens.current.divider2)

@Composable
fun Muted(text: String, style: TextStyle = Type.bodyM) = Text(text, style = style, color = LocalTokens.current.text3)

@Composable
fun VGap(height: Dp) = Spacer(Modifier.height(height))

@Composable
fun HGap(width: Dp) = Spacer(Modifier.width(width))

@Composable
fun PageHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    @DrawableRes leadingIcon: Int? = null,
    trailing: @Composable () -> Unit = {},
) {
    val t = LocalTokens.current
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(Space.appBar))
        Row(
            Modifier.fillMaxWidth().height(Space.header).padding(horizontal = Space.margin),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            when {
                onBack != null -> PIconButton(R.drawable.ic_back, stringResource(R.string.back), onBack, size = 64.dp, shape = Radius.s)
                leadingIcon != null -> Box(Modifier.size(64.dp).clip(Radius.s).background(t.text), contentAlignment = Alignment.Center) {
                    Icon(painterResource(leadingIcon), contentDescription = null, tint = t.bg, modifier = Modifier.size(40.dp))
                }
            }
            Text(title, style = Type.title, color = t.text, modifier = Modifier.weight(1f))
            trailing()
        }
        HorizontalDivider(thickness = 1.dp, color = t.divider3)
    }
}

@Composable
fun ErrorLine(text: String) {
    val t = LocalTokens.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        Icon(painterResource(R.drawable.ic_alert), contentDescription = null, tint = t.danger, modifier = Modifier.size(32.dp))
        Text(text, style = Type.bodySBold, color = t.danger)
    }
}

fun Modifier.borderIf(on: Boolean, stroke: BorderStroke, shape: Shape): Modifier = if (on) border(stroke, shape) else this
