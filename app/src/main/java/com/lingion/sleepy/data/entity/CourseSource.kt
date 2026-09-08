package com.lingion.sleepy.data.entity

/** 课程来源只用于展示，不参与课程名称、分组或提醒。 */
object CourseSource {
    const val UNDERGRADUATE = "undergraduate"
    const val GRADUATE = "graduate"

    fun badge(source: String): String = when (source) {
        UNDERGRADUATE -> "本"
        GRADUATE -> "研"
        else -> ""
    }
}
