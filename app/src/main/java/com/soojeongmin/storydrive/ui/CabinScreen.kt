package com.soojeongmin.storydrive.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.soojeongmin.storydrive.R
import com.soojeongmin.storydrive.story.Hideout
import com.soojeongmin.storydrive.story.KidProfile
import com.soojeongmin.storydrive.story.Line
import com.soojeongmin.storydrive.story.TripProgress
import com.soojeongmin.storydrive.vehicle.CabinState
import com.soojeongmin.storydrive.vehicle.SeatPosition

private val frontRow = listOf(SeatPosition.DRIVER, SeatPosition.FRONT_PASSENGER)
private val rearRow = listOf(SeatPosition.REAR_LEFT, SeatPosition.REAR_CENTER, SeatPosition.REAR_RIGHT)

@Composable
fun CabinScreen(
    cabin: CabinState,
    kids: List<KidProfile>,
    progress: TripProgress,
    speaking: Line?,
    paused: Boolean,
    onTogglePause: () -> Unit,
    onManageKids: () -> Unit,
    onSeatClick: (SeatPosition, KidProfile?) -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    val t = LocalTokens.current
    val locked = cabin.setupLocked
    Column(Modifier.fillMaxSize().background(t.bg)) {
        PageHeader(stringResource(R.string.app_name), leadingIcon = R.drawable.ic_book) {
            if (!locked) PTextButton(stringResource(R.string.manage_kids), onManageKids, R.drawable.ic_profile)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .let { if (extra != null) it.verticalScroll(rememberScrollState()) else it }
                .padding(horizontal = Space.margin),
        ) {
            VGap(Space.xxl)
            Text(statusText(cabin, kids, progress), style = Type.headline, color = t.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            hintText(progress)?.let {
                VGap(Space.s)
                Muted(it)
            }
            VGap(Space.xl)

            listOf(frontRow, rearRow).forEachIndexed { index, row ->
                if (index > 0) VGap(Space.m)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    row.forEach { seat ->
                        val member = kids.firstOrNull { it.seat == seat }
                        SeatCard(Modifier.weight(1f), seat, member, cabin.seats[seat]?.buckled == true, locked) { onSeatClick(seat, member) }
                    }
                }
            }
            VGap(Space.xl)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.progress_label, (progress.ratio * 100).toInt()), style = Type.label, color = t.text3, modifier = Modifier.weight(1f))
                if (progress.treasuresFound.isNotEmpty()) {
                    Text(stringResource(R.string.treasures_label, progress.treasuresFound.size), style = Type.label, color = t.text3)
                }
            }
            VGap(Space.sm)
            ThinProgress(progress.ratio)
            VGap(Space.xl)

            StoryCard(cabin, speaking, paused, onTogglePause)

            if (extra != null) {
                Spacer(Modifier.height(LocalConfiguration.current.screenHeightDp.dp))
                extra()
                VGap(200.dp)
            }
        }
    }
}

@Composable
private fun StoryCard(cabin: CabinState, speaking: Line?, paused: Boolean, onTogglePause: () -> Unit) {
    val t = LocalTokens.current
    val text = speaking?.text?.let {
        if (cabin.limitText && cabin.maxStringLength > 0 && it.length > cabin.maxStringLength) it.take(cabin.maxStringLength) + "…" else it
    }
    Row(
        Modifier.fillMaxWidth().card().padding(Space.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(if (paused) R.string.story_paused_label else R.string.now_speaking), style = Type.label, color = t.text3)
            VGap(Space.s)
            Text(
                text ?: stringResource(if (paused) R.string.story_paused_hint else R.string.now_speaking_none),
                style = Type.bodyLBold,
                color = if (text == null) t.text3 else t.text,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PIconButton(
            icon = if (paused) R.drawable.ic_play else R.drawable.ic_pause,
            description = stringResource(if (paused) R.string.story_play else R.string.story_pause),
            onClick = onTogglePause,
            kind = if (paused) ButtonKind.Secondary else ButtonKind.Primary,
        )
    }
}

@Composable
private fun SeatCard(modifier: Modifier, seat: SeatPosition, kid: KidProfile?, buckled: Boolean, locked: Boolean, onClick: () -> Unit) {
    val t = LocalTokens.current
    val alert = kid != null && !buckled
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .defaultMinSize(minHeight = 180.dp)
            .card()
            .borderIf(alert, BorderStroke(3.dp, t.danger), Radius.l)
            .clickable(interactionSource = interaction, indication = null, enabled = !locked, role = Role.Button, onClick = onClick)
            .padding(Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Text(stringResource(seat.label()), style = Type.label, color = t.text3)
        Text(
            kid?.let {
                when {
                    !it.isAdult -> it.name
                    it.seat == SeatPosition.DRIVER -> stringResource(R.string.captain_name, it.name)
                    else -> stringResource(R.string.deputy_name, it.name)
                }
            } ?: stringResource(R.string.seat_empty),
            style = Type.titleM,
            color = if (kid == null) t.text3 else t.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when {
            kid == null && locked -> Text(stringResource(R.string.seat_locked), style = Type.bodyS, color = t.text3)
            kid == null -> StatusLine(R.drawable.ic_plus, stringResource(R.string.seat_tap_to_add), t.text2, bold = false)
            buckled -> StatusLine(R.drawable.ic_check, stringResource(R.string.belt_on), t.active, bold = true)
            else -> StatusLine(R.drawable.ic_alert, stringResource(R.string.belt_off), t.danger, bold = true)
        }
    }
}

@Composable
private fun StatusLine(icon: Int, text: String, color: androidx.compose.ui.graphics.Color, bold: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
        Text(text, style = if (bold) Type.bodySBold else Type.bodyS, color = color)
    }
}

@Composable
private fun statusText(cabin: CabinState, kids: List<KidProfile>, progress: TripProgress): String {
    val allBuckled = kids.isNotEmpty() && kids.all { cabin.seats[it.seat]?.buckled == true }
    return stringResource(
        when {
            !cabin.connected -> R.string.status_offline
            kids.isEmpty() -> R.string.status_need_kids
            progress.storyPaused -> R.string.status_paused
            progress.hideout == Hideout.VISITING -> R.string.status_hideout
            !cabin.parked && progress.inTurtleVillage -> R.string.status_turtle
            !cabin.parked -> R.string.status_driving
            allBuckled -> R.string.status_ready
            else -> R.string.status_buckle
        },
    )
}

@Composable
private fun hintText(progress: TripProgress): String? = when (progress.hideout) {
    Hideout.ASKING -> stringResource(R.string.hint_hideout_asking)
    Hideout.PLANNED -> stringResource(R.string.hint_hideout_planned)
    else -> null
}

fun SeatPosition.label() = when (this) {
    SeatPosition.DRIVER -> R.string.seat_driver
    SeatPosition.FRONT_PASSENGER -> R.string.seat_front_passenger
    SeatPosition.REAR_LEFT -> R.string.seat_rear_left
    SeatPosition.REAR_CENTER -> R.string.seat_rear_center
    SeatPosition.REAR_RIGHT -> R.string.seat_rear_right
}
