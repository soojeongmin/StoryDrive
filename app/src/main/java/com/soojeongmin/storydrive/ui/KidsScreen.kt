package com.soojeongmin.storydrive.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.unit.dp
import com.soojeongmin.storydrive.R
import com.soojeongmin.storydrive.story.AgeBand
import com.soojeongmin.storydrive.story.KidProfile
import com.soojeongmin.storydrive.story.KoreanName
import com.soojeongmin.storydrive.story.MixedLevel
import com.soojeongmin.storydrive.story.Role
import com.soojeongmin.storydrive.vehicle.SeatPosition
import java.util.UUID

private val ContentWidth = 1000.dp

@Composable
fun KidsScreen(
    kids: List<KidProfile>,
    mixedLevel: MixedLevel,
    onMixedLevel: (MixedLevel) -> Unit,
    onSave: (KidProfile) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteAll: () -> Unit,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf<KidProfile?>(null) }
    var adding by remember { mutableStateOf(kids.isEmpty()) }

    if (adding || editing != null) {
        KidQuestions(
            initial = editing,
            takenSeats = kids.filter { it.id != editing?.id }.map { it.seat }.toSet(),
            onDone = { kid ->
                onSave(kid)
                adding = false
                editing = null
            },
            onCancel = if (kids.isEmpty() && editing == null) null else ({ adding = false; editing = null }),
        )
        return
    }

    BackHandler(onBack = onBack)
    val t = LocalTokens.current
    Column(Modifier.fillMaxSize().background(t.bg)) {
        PageHeader(stringResource(R.string.title_kids), onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.margin)) {
            Column(Modifier.widthIn(max = ContentWidth)) {
                VGap(Space.xxl)
                Muted(stringResource(R.string.kids_help))
                VGap(Space.l)
                if (kids.isNotEmpty()) {
                    ListContainer {
                        kids.forEachIndexed { index, kid ->
                            if (index > 0) Divider()
                            KidRow(kid, onEdit = { editing = kid }, onDelete = { onDelete(kid.id) })
                        }
                    }
                    VGap(Space.l)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    PButton(stringResource(R.string.add_kid), onClick = { adding = true }, kind = ButtonKind.Secondary, icon = R.drawable.ic_plus)
                    if (kids.isNotEmpty()) PButton(stringResource(R.string.delete_all), onClick = onDeleteAll, icon = R.drawable.ic_trash)
                }
                if (kids.filterNot { it.isAdult }.map { it.age }.toSet().size > 1) {
                    VGap(Space.xxxl)
                    Text(stringResource(R.string.mixed_title), style = Type.titleS, color = t.text)
                    VGap(Space.s)
                    Muted(stringResource(R.string.mixed_help))
                    VGap(Space.m)
                    ListContainer {
                        PRadioRow(stringResource(R.string.mixed_toddler), mixedLevel == MixedLevel.TODDLER) { onMixedLevel(MixedLevel.TODDLER) }
                        PRadioRow(stringResource(R.string.mixed_early), mixedLevel == MixedLevel.EARLY) { onMixedLevel(MixedLevel.EARLY) }
                    }
                }
                VGap(Space.xxxl)
            }
        }
    }
}

@Composable
private fun KidRow(kid: KidProfile, onEdit: () -> Unit, onDelete: () -> Unit) {
    val t = LocalTokens.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 112.dp)
            .clip(Radius.m)
            .clickable(interactionSource = interaction, indication = null, role = SemanticsRole.Button, onClick = onEdit)
            .padding(horizontal = Space.ml, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(kid.name, style = Type.titleS, color = t.text)
            Text("${stringResource(if (kid.isAdult) R.string.role_adult else kid.age.label())} · ${stringResource(kid.seat.label())}", style = Type.bodyS, color = t.text3)
        }
        PIconButton(R.drawable.ic_trash, stringResource(R.string.delete), onDelete, size = 64.dp, shape = Radius.s)
        Icon(painterResource(R.drawable.ic_chevron), contentDescription = stringResource(R.string.change), tint = t.text3, modifier = Modifier.size(40.dp))
    }
}

@Composable
fun KidQuestions(
    initial: KidProfile?,
    takenSeats: Set<SeatPosition>,
    onDone: (KidProfile) -> Unit,
    onCancel: (() -> Unit)?,
    presetSeat: SeatPosition? = null,
) {
    var role by remember { mutableStateOf(initial?.role) }
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var age by remember { mutableStateOf(initial?.age) }
    val titles = TITLE_IDS.map { stringResource(it) }
    val initialTitle = initial?.takeIf { it.isAdult }?.name
    var title by remember { mutableStateOf(initialTitle?.let { if (it in titles) it else CUSTOM }) }
    var customTitle by remember { mutableStateOf(initialTitle?.takeIf { it !in titles }.orEmpty()) }
    var seat by remember { mutableStateOf(presetSeat ?: initial?.seat) }
    var error by remember { mutableStateOf<Int?>(null) }
    val t = LocalTokens.current

    val steps = buildList {
        if (initial == null) add(Step.ROLE)
        if (role == Role.ADULT) add(Step.TITLE) else { add(Step.NAME); add(Step.AGE) }
        if (presetSeat == null) add(Step.SEAT)
    }
    val current = steps[step.coerceAtMost(steps.lastIndex)]
    val isLast = step >= steps.lastIndex
    val displayName = if (role == Role.ADULT) resolvedTitle(title, customTitle) else name.trim()
    val seatChoices = SeatPosition.entries.filter { it !in takenSeats && (role == Role.ADULT || it != SeatPosition.DRIVER) }
    val goBack: (() -> Unit)? = if (step > 0) ({ step--; error = null }) else onCancel
    BackHandler(enabled = goBack != null) { goBack?.invoke() }

    Column(Modifier.fillMaxSize().background(t.bg)) {
        PageHeader(stringResource(R.string.title_kids), onBack = goBack) {
            onCancel?.let { PIconButton(R.drawable.ic_close, stringResource(R.string.cancel), it, size = 64.dp, shape = Radius.s) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.margin)) {
            Column(Modifier.widthIn(max = ContentWidth)) {
                VGap(Space.xxl)
                ThinProgress((step + 1) / steps.size.toFloat())
                VGap(Space.xxl)
                val (question, help) = when (current) {
                    Step.ROLE -> stringResource(R.string.q_role) to stringResource(R.string.q_role_help)
                    Step.NAME -> stringResource(R.string.q_name) to stringResource(R.string.q_name_help)
                    Step.AGE -> stringResource(R.string.q_age, nameFor(name, "")) to stringResource(R.string.q_age_help)
                    Step.TITLE -> stringResource(R.string.q_title) to stringResource(R.string.q_title_help)
                    Step.SEAT -> stringResource(R.string.q_seat, if (role == Role.ADULT) displayName else nameFor(name, "는")) to stringResource(R.string.q_seat_help)
                }
                Text(question, style = Type.headline, color = t.text)
                VGap(Space.s)
                Muted(help)
                error?.let {
                    VGap(Space.m)
                    ErrorLine(stringResource(it))
                }
                VGap(Space.xl)
                when (current) {
                    Step.ROLE -> ListContainer {
                        PRadioRow(stringResource(R.string.role_child), role == Role.CHILD) { role = Role.CHILD; error = null }
                        PRadioRow(stringResource(R.string.role_adult), role == Role.ADULT) { role = Role.ADULT; error = null }
                    }
                    Step.NAME -> PTextField(name, { name = it.take(MAX_NAME); error = null })
                    Step.AGE -> ListContainer {
                        AgeBand.entries.forEach { PRadioRow(stringResource(it.label()), age == it) { age = it; error = null } }
                    }
                    Step.TITLE -> {
                        ListContainer {
                            titles.forEach { option -> PRadioRow(option, title == option) { title = option; error = null } }
                            PRadioRow(stringResource(R.string.title_custom), title == CUSTOM) { title = CUSTOM; error = null }
                        }
                        if (title == CUSTOM) {
                            VGap(Space.m)
                            PTextField(customTitle, { customTitle = it.take(MAX_NAME); error = null })
                        }
                    }
                    Step.SEAT -> ListContainer {
                        seatChoices.forEach { PRadioRow(stringResource(it.label()), seat == it) { seat = it; error = null } }
                    }
                }
                VGap(Space.xxl)
            }
        }
        Box(Modifier.padding(horizontal = Space.margin, vertical = Space.l)) {
            PButton(
                stringResource(if (isLast) R.string.save else R.string.save_continue),
                kind = ButtonKind.Secondary,
                modifier = Modifier.widthIn(max = ContentWidth).fillMaxWidth(),
                onClick = {
                    val problem = when (current) {
                        Step.ROLE -> R.string.err_role.takeIf { role == null }
                        Step.NAME -> R.string.err_name.takeIf { name.isBlank() }
                        Step.AGE -> R.string.err_age.takeIf { age == null }
                        Step.TITLE -> R.string.err_title.takeIf { displayName.isBlank() }
                        Step.SEAT -> R.string.err_seat.takeIf { seat == null || seat !in seatChoices }
                    }
                    when {
                        problem != null -> error = problem
                        !isLast -> step++
                        else -> onDone(
                            KidProfile(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                name = displayName,
                                age = age ?: AgeBand.EARLY,
                                seat = seat!!,
                                role = role ?: Role.CHILD,
                            ),
                        )
                    }
                },
            )
        }
    }
}

private enum class Step { ROLE, NAME, AGE, TITLE, SEAT }

private const val CUSTOM = "\u0000custom"

private val TITLE_IDS = listOf(R.string.title_mom, R.string.title_dad, R.string.title_grandma, R.string.title_grandpa)

private fun resolvedTitle(title: String?, custom: String): String = when (title) {
    null -> ""
    CUSTOM -> custom.trim()
    else -> title
}

private const val MAX_NAME = 8

private fun nameFor(name: String, particle: String): String =
    if (java.util.Locale.getDefault().language == "ko") KoreanName.withParticle(name.trim(), particle.ifEmpty { "이" }) else name.trim()

fun AgeBand.label() = when (this) {
    AgeBand.TODDLER -> R.string.age_toddler
    AgeBand.EARLY -> R.string.age_early
}

@Composable
fun SetupLockedScreen() {
    val t = LocalTokens.current
    Column(Modifier.fillMaxSize().background(t.bg)) {
        PageHeader(stringResource(R.string.app_name), leadingIcon = R.drawable.ic_book)
        Column(Modifier.padding(horizontal = Space.margin)) {
            VGap(Space.xxl)
            Text(stringResource(R.string.setup_locked_title), style = Type.headline, color = t.text)
            VGap(Space.s)
            Muted(stringResource(R.string.setup_locked_body))
        }
    }
}
