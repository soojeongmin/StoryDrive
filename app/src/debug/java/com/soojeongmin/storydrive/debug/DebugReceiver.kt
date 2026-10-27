package com.soojeongmin.storydrive.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.soojeongmin.storydrive.StoryDriveApp
import com.soojeongmin.storydrive.sim.DemoCourse

class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val engine = (context.applicationContext as StoryDriveApp).engine
        val sim = com.soojeongmin.storydrive.dev.DevBenches.simulator
        when (intent.getStringExtra("cmd")) {
            "sim_on" -> sim.enable(true)
            "sim_off" -> sim.enable(false)
            "course" -> DemoCourse.entries.firstOrNull { it.minutes == intent.getIntExtra("minutes", 5) }?.let(sim::runCourse)
            "drive" -> sim.setParked(false)
            "park" -> sim.setParked(true)
            "buckle_all" -> sim.buckleAll(true)
            "tunnel" -> sim.tunnel()
            "rest_area" -> sim.restArea(3.0)
            "congestion" -> sim.congestion()
            "hear" -> engine.listener.deliver(intent.getStringExtra("text").orEmpty())
        }
    }
}
