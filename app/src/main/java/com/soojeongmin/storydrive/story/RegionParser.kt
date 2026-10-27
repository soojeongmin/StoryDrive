package com.soojeongmin.storydrive.story

object RegionParser {
    private val provinces = linkedMapOf(
        "서울" to "서울", "부산" to "부산", "대구" to "대구", "인천" to "인천", "광주" to "광주",
        "대전" to "대전", "울산" to "울산", "세종" to "세종", "경기" to "경기도", "강원" to "강원도",
        "충북" to "충청북도", "충청북" to "충청북도", "충남" to "충청남도", "충청남" to "충청남도",
        "전북" to "전라북도", "전라북" to "전라북도", "전남" to "전라남도", "전라남" to "전라남도",
        "경북" to "경상북도", "경상북" to "경상북도", "경남" to "경상남도", "경상남" to "경상남도", "제주" to "제주도",
    )

    fun province(address: String): String? {
        val first = address.trim().split(" ").firstOrNull() ?: return null
        return provinces.entries.firstOrNull { first.startsWith(it.key) }?.value
    }
}
