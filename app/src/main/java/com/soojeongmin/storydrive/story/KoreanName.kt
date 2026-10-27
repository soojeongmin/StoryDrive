package com.soojeongmin.storydrive.story

object KoreanName {
    private const val HANGUL_START = 0xAC00
    private const val HANGUL_END = 0xD7A3
    private const val RIEUL = 8

    fun hasBatchim(word: String): Boolean {
        val last = word.trim().lastOrNull() ?: return false
        if (last.code !in HANGUL_START..HANGUL_END) return false
        return (last.code - HANGUL_START) % 28 != 0
    }

    fun casual(name: String): String = if (hasBatchim(name)) name + "이" else name

    fun withParticle(name: String, particle: String): String {
        val base = casual(name)
        return when (particle) {
            "" -> name
            "이" -> base
            "아" -> name + if (hasBatchim(name)) "아" else "야"
            "가" -> base + "가"
            "는" -> base + "는"
            "를" -> base + "를"
            "와" -> base + "와"
            "의" -> base + "의"
            "도" -> base + "도"
            else -> base + particle
        }
    }

    fun noun(word: String, particle: String): String {
        val batchim = hasBatchim(word)
        return word + when (particle) {
            "가", "이" -> if (batchim) "이" else "가"
            "는", "은" -> if (batchim) "은" else "는"
            "를", "을" -> if (batchim) "을" else "를"
            "와", "과" -> if (batchim) "과" else "와"
            "야", "이야" -> if (batchim) "이야" else "야"
            "로", "으로" -> if (batchim && (word.last().code - HANGUL_START) % 28 != RIEUL) "으로" else "로"
            else -> particle
        }
    }

    fun joinAll(names: List<String>, particle: String): String {
        if (names.isEmpty()) return ""
        if (names.size == 1) return withParticle(names.first(), particle)
        val head = names.dropLast(1).joinToString(", ") { casual(it) }
        val connector = if (names.size == 2) "와 " else ", "
        val joined = if (names.size == 2) withParticle(names[0], "와") + " " else "$head$connector"
        return joined + withParticle(names.last(), particle)
    }
}
