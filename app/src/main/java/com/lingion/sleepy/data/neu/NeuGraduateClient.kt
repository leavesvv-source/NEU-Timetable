package com.lingion.sleepy.data.neu

import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class NeuGraduateSchedule(
    val rows: List<NeuCourseRow>,
    val timeRows: List<TimeTableUtils.TimeSlotRow>
)

/** 东北大学研究生教务课表客户端；强基班账号返回本科与研究生课程的合并课表。 */
class NeuGraduateClient(
    private val networkConfig: NeuNetworkConfig,
    private val cookieProvider: (String) -> String?
) {
    suspend fun fetchCurrentUser(): NeuCurrentUser = withContext(Dispatchers.IO) {
        val response = requestJson(
            "/gsapp/sys/yjsemaphome/modules/pubWork/getUserInfo.do",
            ""
        )
        if (response.optString("code") !in setOf("", "0")) {
            throw IOException("未检测到研究生教务登录态，请先在官方页面完成登录。")
        }
        val data = response.optJSONObject("data")
            ?: response.optJSONObject("datas")
            ?: response.optJSONObject("res")
            ?: response
        val userName = findString(data, listOf("userName", "USER_NAME", "XM", "xm", "name"))
        val userId = findString(data, listOf("userId", "USER_ID", "XH", "xh", "account"))
        if (userName.isBlank() && userId.isBlank()) {
            throw IOException("研究生教务返回了页面，但未识别到用户信息；请确认已登录。")
        }
        val term = NeuGraduateTerm.defaultCode()
        NeuCurrentUser(userName, userId, term, NeuGraduateTerm.displayName(term))
    }

    suspend fun fetchSchedule(termInput: String): NeuGraduateSchedule = withContext(Dispatchers.IO) {
        val termCode = NeuGraduateTerm.normalize(termInput)
            ?: throw IOException("研究生学期代码应为 20261，或 2026-2027-1。")
        val response = requestJson(
            "/gsapp/sys/wdkbapp/xskcb/loadPkjg.do",
            encodeFormBody(mapOf("XNXQDM" to termCode, "ZC" to ""))
        )
        val schedule = parseScheduleResponse(response)
        if (schedule.rows.isEmpty()) {
            throw IOException("本研课表为空或解析失败，请确认学期代码与登录状态。")
        }
        schedule
    }

    internal fun parseScheduleResponse(response: JSONObject): NeuGraduateSchedule {
        val courseList = response.optJSONArray("jgList")
            ?: response.optJSONObject("datas")
                ?.optJSONObject("xspkjgcx")
                ?.optJSONArray("rows")
            ?: JSONArray()
        val rows = parseCourses(courseList)
        val timeRows = parseTimeRows(response.optJSONArray("jcList") ?: JSONArray())
        return NeuGraduateSchedule(rows, timeRows)
    }

    suspend fun fetchTermStartDate(termInput: String): String = withContext(Dispatchers.IO) {
        val termCode = NeuGraduateTerm.normalize(termInput)
            ?: throw IOException("研究生学期代码不正确。")
        val response = requestJson(
            "/gsapp/sys/yjsemaphome/homeAppend/getSchoolCalendar.do",
            encodeFormBody(mapOf("xnxqdm" to termCode))
        )
        val calendar = when (val raw = response.opt("msg")) {
            is JSONObject -> raw
            is String -> runCatching { JSONObject(raw) }.getOrNull()
            else -> null
        } ?: response.optJSONObject("data") ?: response
        val rawDate = findString(calendar, listOf("QSRQ", "qsrq", "startDate", "XQKSRQ"))
        val parsed = runCatching {
            LocalDate.parse(rawDate.take(10).replace('/', '-'))
        }.getOrElse { throw IOException("未获取到研究生学期起始日期。") }
        parsed.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).toString()
    }

    private fun parseCourses(items: JSONArray): List<NeuCourseRow> = buildList {
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val name = firstNonBlank(item, "KCMC", "courseName", "KCM")
            val day = firstInt(item, "XQ", "dayOfWeek", "SKXQ")
            val begin = firstInt(item, "KSJCDM", "beginSection", "KSJC")
            val end = firstInt(item, "JSJCDM", "endSection", "JSJC")
            val weeks = parseWeekBits(firstNonBlank(item, "ZCBH", "weeks", "SKZC"))
            if (name.isBlank() || day !in 1..7 || begin <= 0 || end < begin || weeks.isBlank()) continue
            add(
                NeuCourseRow(
                    courseName = name,
                    dayOfWeek = day,
                    beginSection = begin,
                    endSection = end,
                    teacher = firstNonBlank(item, "JGJSXM", "JSXM", "teacher", "SKJS"),
                    location = firstNonBlank(item, "JASMC", "location", "JASDM")
                        .ifBlank { "暂未安排教室" },
                    weeks = weeks,
                    campus = firstNonBlank(item, "XXXQMC", "campusName", "XQXQMC")
                )
            )
        }
    }

    private fun parseTimeRows(items: JSONArray): List<TimeTableUtils.TimeSlotRow> = buildList {
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val node = firstInt(item, "DM", "JC", "node")
            if (node <= 0) continue
            val start = formatTime(firstNonBlank(item, "KSSJ", "startTime"))
            val end = formatTime(firstNonBlank(item, "JSSJ", "endTime"))
            if (start.isNotBlank() && end.isNotBlank()) {
                add(TimeTableUtils.TimeSlotRow(node, start, end))
            }
        }
    }.sortedBy { it.node }

    private fun requestJson(path: String, body: String): JSONObject {
        val resolvedUrl = networkConfig.resolve("$BASE_URL$path")
        val connection = (URL(resolvedUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            doOutput = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("X-Requested-With", "XMLHttpRequest")
            setRequestProperty("Origin", networkConfig.graduateRequestOrigin)
            setRequestProperty("Referer", networkConfig.graduateRequestReferer)
            cookieProvider(resolvedUrl)?.takeIf { it.isNotBlank() }
                ?.let { setRequestProperty("Cookie", it) }
            outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val payload = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) throw IOException("研究生教务请求失败（$code）")
        return runCatching { JSONObject(payload) }
            .getOrElse { throw IOException("研究生教务返回的不是 JSON，登录可能已过期。") }
    }

    private fun encodeFormBody(params: Map<String, String>): String = params.entries.joinToString("&") {
        "${URLEncoder.encode(it.key, StandardCharsets.UTF_8.toString())}=" +
            URLEncoder.encode(it.value, StandardCharsets.UTF_8.toString())
    }

    private fun firstNonBlank(item: JSONObject, vararg keys: String): String =
        keys.firstNotNullOfOrNull { key -> item.optString(key).trim().takeIf(String::isNotBlank) }.orEmpty()

    private fun firstInt(item: JSONObject, vararg keys: String): Int =
        keys.firstNotNullOfOrNull { key ->
            item.opt(key)?.toString()?.trim()?.toIntOrNull()
        } ?: -1

    private fun findString(item: JSONObject, keys: List<String>, depth: Int = 0): String {
        keys.forEach { key -> item.optString(key).trim().takeIf(String::isNotBlank)?.let { return it } }
        if (depth >= 2) return ""
        for (key in item.keys()) {
            val child = item.optJSONObject(key) ?: continue
            findString(child, keys, depth + 1).takeIf(String::isNotBlank)?.let { return it }
        }
        return ""
    }

    companion object {
        private const val BASE_URL = "https://yjs.neu.edu.cn"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        internal fun parseWeekBits(raw: String): String {
            val compact = raw.trim()
            if (compact.matches(Regex("[01]+"))) {
                return compact.mapIndexedNotNull { index, value ->
                    (index + 1).takeIf { value == '1' }
                }.joinToString("、")
            }
            return compact
        }

        internal fun formatTime(raw: String): String {
            val digits = raw.filter(Char::isDigit)
            if (digits.length !in 3..4) return raw.takeIf { it.matches(Regex("\\d{2}:\\d{2}")) }.orEmpty()
            val padded = digits.padStart(4, '0')
            return "${padded.take(2)}:${padded.takeLast(2)}"
        }
    }
}

object NeuGraduateTerm {
    fun normalize(raw: String): String? {
        val value = raw.trim()
        Regex("^(\\d{4})([12])$").matchEntire(value)?.let { return it.groupValues[1] + it.groupValues[2] }
        Regex("^(\\d{4})-\\d{4}-([12])$").matchEntire(value)?.let { return it.groupValues[1] + it.groupValues[2] }
        Regex("^(\\d{4})-([12])$").matchEntire(value)?.let { return it.groupValues[1] + it.groupValues[2] }
        return null
    }

    fun defaultCode(today: LocalDate = LocalDate.now()): String =
        if (today.monthValue >= 8) "${today.year}1" else "${today.year - 1}2"

    fun displayName(code: String): String {
        val normalized = normalize(code) ?: return code
        val year = normalized.take(4).toInt()
        return "$year-${year + 1}学年第${normalized.last()}学期"
    }

    fun estimatedStartDate(code: String): String {
        val normalized = normalize(code) ?: defaultCode()
        val year = normalized.take(4).toInt()
        val date = if (normalized.last() == '1') LocalDate.of(year, 9, 1)
        else LocalDate.of(year + 1, 3, 1)
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).toString()
    }
}
