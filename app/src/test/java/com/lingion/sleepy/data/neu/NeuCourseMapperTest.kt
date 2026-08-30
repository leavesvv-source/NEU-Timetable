package com.lingion.sleepy.data.neu

import org.junit.Assert.assertEquals
import org.junit.Test

class NeuCourseMapperTest {
    @Test
    fun parsesContinuousOddEvenAndDiscreteWeeks() {
        assertEquals((1..16).toList(), NeuCourseMapper.parseWeekNumbers("1-16周"))
        assertEquals(listOf(1, 3, 5, 7), NeuCourseMapper.parseWeekNumbers("1-8周(单)"))
        assertEquals(listOf(2, 4, 6, 8), NeuCourseMapper.parseWeekNumbers("1-8周(双)"))
        assertEquals(listOf(1, 3, 6, 9), NeuCourseMapper.parseWeekNumbers("1、3、6、9周"))
    }

    @Test
    fun mapsOddWeekCourseWithoutLosingParity() {
        val mapped = NeuCourseMapper.mapRows(
            listOf(NeuCourseRow("测试课", 2, 3, 4, "教师", "A101", "1-8单", "浑南校区"))
        )
        assertEquals(1, mapped.size)
        assertEquals(1, mapped.single().type)
        assertEquals(1, mapped.single().startWeek)
        assertEquals(7, mapped.single().endWeek)
    }
}
