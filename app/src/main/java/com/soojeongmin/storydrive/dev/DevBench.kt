package com.soojeongmin.storydrive.dev

import androidx.compose.runtime.Composable
import com.soojeongmin.storydrive.speech.Listener
import com.soojeongmin.storydrive.speech.Narrator
import com.soojeongmin.storydrive.story.KidProfile
import com.soojeongmin.storydrive.story.RouteEvent
import com.soojeongmin.storydrive.vehicle.CabinState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

interface DevBench {
    val cabin: StateFlow<CabinState?>

    @Composable
    fun Panel(cabin: CabinState, kids: List<KidProfile>)
}

class DevHooks(
    val scope: CoroutineScope,
    val kids: () -> List<KidProfile>,
    val emitRoute: (RouteEvent) -> Unit,
    val decideHideout: (Boolean) -> Unit,
    val addMember: (KidProfile) -> Unit,
    val beginNewTrip: (Int) -> Unit,
    val narrator: Narrator,
    val listener: Listener,
)
