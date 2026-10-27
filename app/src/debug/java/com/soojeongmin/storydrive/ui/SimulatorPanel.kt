package com.soojeongmin.storydrive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.soojeongmin.storydrive.R
import com.soojeongmin.storydrive.sim.DemoCourse
import com.soojeongmin.storydrive.sim.DriveSimulator
import com.soojeongmin.storydrive.story.KidProfile
import com.soojeongmin.storydrive.vehicle.CabinState
import com.soojeongmin.storydrive.vehicle.Turn

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimulatorPanel(sim: DriveSimulator, cabin: CabinState, kids: List<KidProfile>, enabled: Boolean, onListenTest: () -> Unit = {}) {
    val t = LocalTokens.current
    Column {
        Divider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sim_title), style = Type.titleS, color = t.text, modifier = Modifier.weight(1f))
            PTextButton(stringResource(if (enabled) R.string.sim_off else R.string.sim_on), { sim.enable(!enabled) })
        }
        if (!enabled) return@Column
        VGap(Space.m)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DemoCourse.entries.forEach { course ->
                PButton(stringResource(R.string.sim_course, course.minutes), onClick = { sim.runCourse(course) }, kind = ButtonKind.Secondary)
            }
            PButton(stringResource(R.string.sim_listen), onClick = onListenTest)
            PButton(stringResource(if (cabin.parked) R.string.sim_drive else R.string.sim_park), onClick = { sim.setParked(!cabin.parked) })
            kids.forEach { kid -> PButton(stringResource(R.string.sim_belt, kid.name), onClick = { sim.toggleBelt(kid.seat) }) }
            PButton(stringResource(R.string.sim_tunnel), onClick = sim::tunnel)
            PButton(stringResource(R.string.sim_rest_area), onClick = { sim.restArea(3.0) })
            PButton(stringResource(R.string.sim_congestion), onClick = sim::congestion)
            PButton(stringResource(R.string.sim_slow), onClick = { sim.setSpeed(if (cabin.speedKph < 15f) 70f else 8f) })
            PButton(stringResource(R.string.sim_stop), onClick = { sim.setSpeed(0f) })
            PButton(stringResource(R.string.sim_region), onClick = { sim.region("강원 원주시 문막읍") })
            PButton(stringResource(R.string.sim_progress), onClick = { sim.advance(0.26) })
            PButton(stringResource(R.string.sim_night), onClick = { sim.setHeadlights(!cabin.headlights) })
            PButton(stringResource(R.string.sim_rain), onClick = { sim.setWiper(!cabin.wiper) })
            PButton(stringResource(R.string.sim_turn), onClick = { sim.setTurn(if (cabin.turn == Turn.OFF) Turn.LEFT else Turn.OFF) })
            PButton(stringResource(R.string.sim_ac), onClick = sim::toggleAc)
            PButton(stringResource(R.string.sim_heater), onClick = sim::toggleHeater)
            PButton(stringResource(R.string.sim_arrive), onClick = sim::arrive)
        }
    }
}
