package com.soojeongmin.storydrive.story

object HideoutAnswer {
    private val no = listOf("지나", "아니", "안 들", "패스", "다음에", "괜찮", "그냥 가")
    private val yes = listOf("들러", "들르", "들려", "응", "네", "좋아", "그래", "가자", "갈래", "쉬자", "쉬어")

    fun parse(heard: String?): Boolean? {
        val text = heard?.replace(" ", "")?.takeIf { it.isNotBlank() } ?: return null
        if (no.any { text.contains(it.replace(" ", "")) }) return false
        if (yes.any { text.contains(it) }) return true
        return null
    }
}
