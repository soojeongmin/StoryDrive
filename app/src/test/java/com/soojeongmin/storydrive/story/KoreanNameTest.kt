package com.soojeongmin.storydrive.story

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KoreanNameTest {
    @Test
    fun detectsBatchim() {
        assertTrue(KoreanName.hasBatchim("민준"))
        assertTrue(KoreanName.hasBatchim("하은"))
        assertFalse(KoreanName.hasBatchim("서아"))
        assertFalse(KoreanName.hasBatchim("Mia"))
    }

    @Test
    fun vocative() {
        assertEquals("민준아", KoreanName.withParticle("민준", "아"))
        assertEquals("서아야", KoreanName.withParticle("서아", "아"))
    }

    @Test
    fun casualSubject() {
        assertEquals("민준이가", KoreanName.withParticle("민준", "가"))
        assertEquals("서아가", KoreanName.withParticle("서아", "가"))
        assertEquals("하은이는", KoreanName.withParticle("하은", "는"))
        assertEquals("서아를", KoreanName.withParticle("서아", "를"))
    }

    @Test
    fun joinsGroup() {
        assertEquals("민준이와 서아가", KoreanName.joinAll(listOf("민준", "서아"), "가"))
        assertEquals("민준이, 서아, 하은이는", KoreanName.joinAll(listOf("민준", "서아", "하은"), "는"))
        assertEquals("서아야", KoreanName.joinAll(listOf("서아"), "아"))
    }

    @Test
    fun nounParticles() {
        assertEquals("별조각이", KoreanName.noun("별조각", "가"))
        assertEquals("구름 솜사탕이", KoreanName.noun("구름 솜사탕", "가"))
        assertEquals("무지개 깃털을", KoreanName.noun("무지개 깃털", "를"))
        assertEquals("웃음 구슬을", KoreanName.noun("웃음 구슬", "를"))
        assertEquals("서울이야", KoreanName.noun("서울", "야"))
        assertEquals("강원도야", KoreanName.noun("강원도", "야"))
    }

    @Test
    fun rendersTemplate() {
        val kids = listOf(
            KidProfile("1", "민준", AgeBand.TODDLER, com.soojeongmin.storydrive.vehicle.SeatPosition.REAR_LEFT),
            KidProfile("2", "서아", AgeBand.EARLY, com.soojeongmin.storydrive.vehicle.SeatPosition.REAR_RIGHT),
        )
        assertEquals("서아야, 딸깍!", LineRenderer.render("{name:아}, 딸깍!", kids[1], kids))
        assertEquals("민준이와 서아가 찾았어", LineRenderer.render("{all:가} 찾았어", null, kids))
        assertEquals("짠! 별조각이 나왔어", LineRenderer.render("짠! {treasure:가} 나왔어", null, kids, mapOf("treasure" to "별조각")))
        assertEquals("친구들아, 안녕", LineRenderer.render("{all:아}, 안녕", null, emptyList()))
    }
}
