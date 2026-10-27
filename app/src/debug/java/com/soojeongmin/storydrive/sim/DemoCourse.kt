package com.soojeongmin.storydrive.sim

import kotlin.random.Random

enum class Cue(val seconds: Int, val repeatable: Boolean = false) {
    TUNNEL(25, true),
    REST_AREA(120),
    CONGESTION(140),
    SLOW(40, true),
    FAST(40, true),
    STOPLIGHT(25, true),
    GANGWON(15),
    HEADLIGHTS(15),
    WIPER(50),
    TURN(15, true),
    AC(15),
    HEATER(15),
}

enum class DemoCourse(val minutes: Int, val origin: String, val destination: String, val pick: (Random) -> List<Cue>) {
    SHORT(5, "서울 송파구 잠실동", "강원 춘천시 남산면", { random ->
        (Cue.entries - Cue.REST_AREA - Cue.CONGESTION - Cue.GANGWON).shuffled(random).take(5) + Cue.GANGWON
    }),
    MEDIUM(15, "경기 성남시 분당구", "강원 원주시 문막읍", { Cue.entries }),
    LONG(30, "충남 아산시 배방읍", "강원 강릉시 사천면", { random ->
        Cue.entries + Cue.entries.filter { it.repeatable }.shuffled(random).take(4)
    });

    fun timeline(random: Random, driveSec: Int): List<Pair<Int, Cue>> {
        val cues = pick(random).toMutableList()
        while (cues.size > 1 && cues.sumOf { it.seconds } > driveSec * 0.8) cues.remove(cues.filter { it != Cue.GANGWON }.random(random))
        val order = cues.shuffled(random).let { list ->
            val gangwon = list.indexOf(Cue.GANGWON)
            if (gangwon > list.size / 2) list else list - Cue.GANGWON + Cue.GANGWON
        }
        val slack = driveSec - order.sumOf { it.seconds }
        val weights = List(order.size + 1) { 0.5 + random.nextDouble() }
        val unit = slack / weights.sum()
        var at = 0
        return order.mapIndexed { i, cue ->
            at += (weights[i] * unit).toInt()
            (at to cue).also { at += cue.seconds }
        }
    }
}
