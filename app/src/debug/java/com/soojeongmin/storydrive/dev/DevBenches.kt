package com.soojeongmin.storydrive.dev

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.soojeongmin.storydrive.R
import com.soojeongmin.storydrive.sim.DriveSimulator
import com.soojeongmin.storydrive.story.KidProfile
import com.soojeongmin.storydrive.story.Line
import com.soojeongmin.storydrive.story.Priority
import com.soojeongmin.storydrive.ui.SimulatorPanel
import com.soojeongmin.storydrive.vehicle.CabinState
import kotlinx.coroutines.flow.StateFlow

object DevBenches {
    lateinit var simulator: DriveSimulator
        private set

    fun create(hooks: DevHooks): DevBench {
        simulator = DriveSimulator(hooks.scope, hooks.kids, hooks.emitRoute, hooks.decideHideout, hooks.addMember, hooks.beginNewTrip)
        return object : DevBench {
            override val cabin: StateFlow<CabinState?> = simulator.cabin

            @Composable
            override fun Panel(cabin: CabinState, kids: List<KidProfile>) {
                val context = LocalContext.current
                SimulatorPanel(simulator, cabin, kids, simulator.active) { listenTest(context, hooks) }
            }
        }
    }

    private fun listenTest(context: android.content.Context, hooks: DevHooks) {
        hooks.narrator.say(Line(context.getString(R.string.sim_listen_prompt), Priority.SAFETY))
        Handler(Looper.getMainLooper()).postDelayed({
            hooks.listener.listen(6_000L) { heard ->
                val text = heard?.let { context.getString(R.string.sim_listen_heard, it) } ?: context.getString(R.string.sim_listen_none)
                hooks.narrator.say(Line(text, Priority.SAFETY))
            }
        }, 3_000L)
    }
}
