package com.soojeongmin.storydrive.story

object LineRenderer {
    private val token = Regex("""\{(\w+)(?::([^}]*))?\}""")
    private val ROLE = Regex("""r\d""")

    fun render(template: String, kid: KidProfile?, kids: List<KidProfile>, values: Map<String, String> = emptyMap()): String =
        token.replace(template) { match ->
            val key = match.groupValues[1]
            val particle = match.groupValues[2]
            when (key) {
                "name" -> kid?.let { KoreanName.withParticle(it.name, particle) } ?: groupOrDefault(kids, particle)
                "all" -> groupOrDefault(kids, particle)
                else -> when {
                    ROLE.matches(key) -> values[key]?.let { KoreanName.withParticle(it, particle) } ?: groupOrDefault(kids, particle)
                    else -> values[key]?.let { KoreanName.noun(it, particle) } ?: match.value
                }
            }
        }

    private val fallbackGroup = mapOf(
        "" to "친구들", "이" to "친구들", "아" to "친구들아", "가" to "친구들이", "는" to "친구들은",
        "를" to "친구들을", "와" to "친구들과", "의" to "친구들의", "도" to "친구들도",
    )

    private fun groupOrDefault(kids: List<KidProfile>, particle: String): String =
        if (kids.isEmpty()) fallbackGroup[particle] ?: ("친구들$particle")
        else KoreanName.joinAll(kids.map { it.name }, particle)
}
