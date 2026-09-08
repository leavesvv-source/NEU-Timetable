package com.lingion.sleepy.data.neu

import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun parsesDetailedCourseListInsteadOfNullGridLocations() {
        val courseListResponse = JSONObject(
            """{
              "datas": {
                "xsjxrwcx": {
                  "rows": [
                    {
                      "KCMC": "最优化方法与理论",
                      "RKJS": "张丽丽",
                      "PKSJDD": "2-13周[讲授X]/星期一/第三节-第四节/张丽丽[主讲]/教102, 2-13周[讲授X]/星期二/第七节-第八节/张丽丽[主讲]/教102"
                    },
                    {
                      "KCMC": "图像处理与计算机视觉",
                      "RKJS": "魏颖,林明秀,张云洲",
                      "PKSJDD": "10-11周,13-15周,17-18周[理论]/星期二/第一节-第二节/魏颖[主讲],林明秀[主讲],张云洲[主讲]/教304; 11周[理论]/星期四/第六节-第八节/魏颖[主讲],林明秀[主讲],张云洲[主讲]/线上"
                    },
                    {
                      "KCMC": "未排课课程",
                      "RKJS": null,
                      "PKSJDD": null
                    }
                  ]
                }
              }
            }"""
        )
        val timeResponse = JSONObject(
            """{
              "datas": {
                "xsskjccx": {
                  "rows": [
                    {"DM": 1, "KSSJ": 830, "JSSJ": 915},
                    {"DM": 2, "KSSJ": 925, "JSSJ": 1010}
                  ]
                }
              }
            }"""
        )

        val parsed = NeuGraduateClient(NeuNetworkConfig(NeuNetworkMode.DIRECT)) { null }
            .parseCourseListResponse(courseListResponse, timeResponse)

        assertEquals(4, parsed.rows.size)
        assertEquals(listOf(1, 2, 2, 4), parsed.rows.map { it.dayOfWeek })
        assertEquals("教102", parsed.rows.first().location)
        assertEquals("10-11周,13-15周,17-18周", parsed.rows[2].weeks)
        assertEquals("魏颖,林明秀,张云洲", parsed.rows[2].teacher)
        assertEquals("线上", parsed.rows.last().location)
        assertEquals("08:30", parsed.timeRows.first().start)
        assertEquals("10:10", parsed.timeRows.last().end)
        assertTrue(parsed.rows.none { it.location.equals("null", ignoreCase = true) })
    }

    @Test
    fun treatsJsonNullAndNullTextAsMissingInLegacyResponse() {
        val response = JSONObject(
            """{
              "jgList": [
                {
                  "KCMC": "研究生数学",
                  "JGJSXM": null,
                  "JASMC": "NULL",
                  "XQ": 2,
                  "KSJCDM": 3,
                  "JSJCDM": 4,
                  "ZCBH": "11"
                }
              ]
            }"""
        )

        val row = NeuGraduateClient(NeuNetworkConfig(NeuNetworkMode.DIRECT)) { null }
            .parseScheduleResponse(response).rows.single()

        assertEquals("", row.teacher)
        assertEquals("暂未安排教室", row.location)
    }
}
