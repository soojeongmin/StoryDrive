package com.soojeongmin.storydrive.sim

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DemoCourseTest {
    private fun driveSec(course: DemoCourse) = course.minutes * 60 - 55

    @Test
    fun timelinesFitAndCoverEveryCue() {
        repeat(200) { seed ->
            DemoCourse.entries.forEach { course ->
                val drive = driveSec(course)
                val timeline = course.timeline(Random(seed), drive)
                val cues = timeline.map { it.second }.toSet()
                assertTrue("$course $timeline", timeline.zipWithNext().all { (a, b) -> a.first + a.second.seconds <= b.first })
                assertTrue("$course $timeline", timeline.last().let { it.first + it.second.seconds } <= drive)
                assertTrue("$course gangwon", Cue.GANGWON in cues)
                if (course != DemoCourse.SHORT) assertTrue("$course $cues", cues == Cue.entries.toSet())
            }
        }
    }

    @Test
    fun shortCourseVariesBetweenRuns() {
        val picks = (0 until 20).map { DemoCourse.SHORT.timeline(Random(it), driveSec(DemoCourse.SHORT)).map { c -> c.second } }.toSet()
        assertTrue(picks.size > 5)
    }
}
