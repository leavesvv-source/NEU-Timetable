package com.lingion.sleepy.data.neu

import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NeuGraduateClientTest {
    @Test
    fun parsesGraduateWeekBitset() {
        assertEquals("1、3、4、7", NeuGraduateClient.parseWeekBits("1011001"))
        assertEquals("1-8单", NeuGraduateClient.parseWeekBits("1-8单"))
    }

    @Test
    fun formatsNumericClassTimes() {
        assertEquals("08:00", NeuGraduateClient.formatTime("800"))
        assertEquals("20:55", NeuGraduateClient.formatTime("2055"))
        assertEquals("08:30", NeuGraduateClient.formatTime("08:30"))
    }

    @Test
    fun acceptsBothGraduateAndUndergraduateTermFormats() {
        assertEquals("20261", NeuGraduateTerm.normalize("20261"))
        assertEquals("20261", NeuGraduateTerm.normalize("2026-2027-1"))
        assertEquals("20262", NeuGraduateTerm.normalize("2026-2"))
        assertNull(NeuGraduateTerm.normalize("2026-2027-3"))
    }

    @Test
    fun choosesCurrentAcademicTerm() {
        assertEquals("20261", NeuGraduateTerm.defaultCode(LocalDate.of(2026, 9, 8)))
        assertEquals("20252", NeuGraduateTerm.defaultCode(LocalDate.of(2026, 3, 1)))
    }

    @Test
    fun parsesNeuGraduateScheduleResponse() {
        val response = JSONObject(
            """{
              "jgList": [{
                "KCMC": "研究生数学",
                "JGJSXM": "张老师",
                "JASMC": "信息楼 B201",
                "XQ": 2,
                "KSJCDM": 3,
                "JSJCDM": 4,
                "ZCBH": "1011"
              }],
              "jcList": [
                {"DM": 3, "KSSJ": 1030, "JSSJ": 1115},
                {"DM": 4, "KSSJ": 1125, "JSSJ": 1210}
              ]
            }"""
        )
        val parsed = NeuGraduateClient(NeuNetworkConfig(NeuNetworkMode.DIRECT)) { null }
            .parseScheduleResponse(response)

        assertEquals(1, parsed.rows.size)
        assertEquals("研究生数学", parsed.rows.single().courseName)
        assertEquals("1、3、4", parsed.rows.single().weeks)
        assertEquals("10:30", parsed.timeRows.first().start)
        assertEquals("12:10", parsed.timeRows.last().end)
    }
}
