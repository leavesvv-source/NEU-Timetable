package com.lingion.sleepy.data.neu

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.util.Locale
import java.util.TimeZone

data class NeuCurrentUser(
    val userName: String,
    val userId: String,
    val defaultTermCode: String,
    val termName: String
)

data class NeuCourseRow(
    val courseName: String,
    val dayOfWeek: Int,
    val beginSection: Int,
    val endSection: Int,
    val teacher: String,
    val location: String,
    val weeks: String,
    val campus: String
)

/** 东北大学 2026 新版本科教务 API 客户端。 */
class NeuJwxtClient(
    private val networkConfig: NeuNetworkConfig,
    private val cookieProvider: (String) -> String?
) {
    suspend fun fetchCurrentUser(): NeuCurrentUser = withContext(Dispatchers.IO) {
        val response = requestJson("GET", "/jwapp/sys/homeapp/api/home/currentUser.do")
        val datas = response.optJSONObject("datas")
            ?: throw IOException("未检测到登录态，请先在官方页面完成登录。")
        val welcome = datas.optJSONObject("welcomeInfo")
        NeuCurrentUser(
            userName = datas.optString("userName"),
            userId = datas.optString("userId"),
            defaultTermCode = welcome?.optString("xnxqdm").orEmpty(),
            termName = welcome?.optString("xnxqmc").orEmpty()
        )
    }

    suspend fun fetchSchedule(termCode: String): List<NeuCourseRow> = withContext(Dispatchers.IO) {
        val primary = runCatching { fetchByCourses(termCode) }.getOrDefault(emptyList())
        val detailed = runCatching { fetchByScheduleDetail(termCode) }.getOrDefault(emptyList())
        if (primary.isEmpty() && detailed.isEmpty()) {
            throw IOException("课表为空或解析失败，请确认学期代码与登录状态。")
        }
        if (primary.isEmpty()) return@withContext mergeRows(detailed)

        val campusBySlot = detailed.filter { it.campus.isNotBlank() }
            .associateBy({ slotKey(it) }, { it.campus })
        val enriched = primary.map { row ->
            row.copy(campus = row.campus.ifBlank { campusBySlot[slotKey(row)].orEmpty() })
        }
        val experiments = detailed.filter { it.courseName.startsWith("[实]") }
        mergeRows(enriched + experiments)
    }

    suspend fun fetchTermStartDate(termCode: String): String = withContext(Dispatchers.IO) {
        val response = requestJson(
            "GET",
            "/jwapp/sys/homeapp/api/home/getTermWeeks.do?termCode=${urlEncode(termCode)}"
        )
        val raw = response.optJSONArray("datas")?.optJSONObject(0)?.optString("startDate").orEmpty()
        if (raw.isBlank()) throw IOException("未获取到学期起始日期。")
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
        val millis = formatter.parse(raw)?.time ?: throw IOException("学期起始日期解析失败：$raw")
        Instant.ofEpochMilli(millis)
            .atZone(ZoneId.of("Asia/Shanghai"))
            .toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            .toString()
    }

    private fun fetchByCourses(termCode: String): List<NeuCourseRow> {
        val response = requestJson(
            "GET",
            "/jwapp/sys/homeapp/api/home/student/courses.do?termCode=${urlEncode(termCode)}"
        )
        val list = response.optJSONArray("datas") ?: return emptyList()
        return buildList {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val courseName = item.optString("courseName").trim()
                val classDateAndPlace = item.optString("classDateAndPlace")
                if (courseName.isBlank() || classDateAndPlace.isBlank() || classDateAndPlace == "null") continue
                for (singleInfo in classDateAndPlace.split("，")) {
                    val parts = singleInfo.split("/")
                    if (parts.size < 4) continue
                    val day = DAY_OF_WEEK_MAP[stripBrackets(parts[1])] ?: continue
                    val sectionRange = parseSectionRange(stripBrackets(parts[2])) ?: continue
                    val location = parts.getOrNull(4)?.replace("*", "")?.trim().orEmpty()
                        .ifBlank { "暂未安排教室" }
                    if (location == "停课") continue
                    add(
                        NeuCourseRow(
                            courseName = courseName,
                            dayOfWeek = day,
                            beginSection = sectionRange.first,
                            endSection = sectionRange.second,
                            teacher = stripBrackets(parts[3]),
                            location = location,
                            weeks = normalizeWeeks(stripBrackets(parts[0])),
                            campus = ""
                        )
                    )
                }
            }
        }
    }

    private fun fetchByScheduleDetail(termCode: String): List<NeuCourseRow> {
        val campusResponse = runCatching {
            requestJson(
                "GET",
                "/jwapp/sys/homeapp/api/home/student/getMyScheduledCampus.do?termCode=${urlEncode(termCode)}"
            )
        }.getOrNull()
        val campusCodes = linkedSetOf("00", "01")
        campusResponse?.optJSONArray("datas")?.let { campuses ->
            for (index in 0 until campuses.length()) {
                campuses.optJSONObject(index)?.optString("id")?.takeIf { it.isNotBlank() }?.let(campusCodes::add)
            }
        }

        return buildList {
            for (campusCode in campusCodes) {
                val response = requestJson(
                    "POST",
                    "/jwapp/sys/homeapp/api/home/student/getMyScheduleDetail.do",
                    "application/x-www-form-urlencoded;charset=UTF-8",
                    encodeFormBody(
                        mapOf("termCode" to termCode, "campusCode" to campusCode, "type" to "term")
                    )
                )
                val datas = response.optJSONObject("datas")
                val arranged = datas?.optJSONArray("arrangedList")
                    ?: datas?.optJSONObject("getMyScheduleDetail")?.optJSONArray("arrangedList")
                    ?: continue
                addAll(parseArrangedList(arranged))
            }
        }
    }

    private fun parseArrangedList(arranged: JSONArray): List<NeuCourseRow> = buildList {
        for (index in 0 until arranged.length()) {
            val item = arranged.optJSONObject(index) ?: continue
            val courseName = item.optString("courseName").trim()
            val day = item.optInt("dayOfWeek", -1)
            val begin = item.optInt("beginSection", -1)
            val end = item.optInt("endSection", -1)
            if (courseName.isBlank() || day !in 1..7 || begin <= 0 || end < begin) continue
            val teacherFromHeader = stripBrackets(item.optString("weeksAndTeachers").substringAfterLast('/'))
            val campusFromItem = item.optString("campusName")
            val details = item.optJSONArray("titleDetail")
            if (details == null || details.length() <= 1) {
                add(NeuCourseRow(courseName, day, begin, end, teacherFromHeader, "暂未安排教室", "", campusFromItem))
                continue
            }
            for (detailIndex in 1 until details.length()) {
                val detail = details.optString(detailIndex).trim()
                if (detail.isEmpty() || !detail.first().isDigit()) continue
                val parts = detail.split(Regex("\\s+"))
                val campus = parts.firstOrNull { it.endsWith("校区") }.orEmpty().ifBlank { campusFromItem }
                val campusIndex = parts.indexOf(campus)
                val teacher = teacherFromHeader.ifBlank {
                    parts.getOrNull(1)?.takeUnless { it.endsWith("校区") }.orEmpty()
                }
                var location = when {
                    courseName.startsWith("[实]") && parts.lastOrNull()?.contains("实验班") == true ->
                        parts.getOrNull(parts.lastIndex - 1).orEmpty()
                    campusIndex >= 0 && campusIndex < parts.lastIndex -> parts[campusIndex + 1]
                    else -> parts.lastOrNull().orEmpty()
                }.replace("*", "").trim()
                if (location.isBlank() || location.endsWith("校区")) location = "暂未安排教室"
                if (location == "停课") continue
                add(
                    NeuCourseRow(
                        courseName, day, begin, end, teacher, location,
                        normalizeWeeks(parts.firstOrNull().orEmpty()), campus
                    )
                )
            }
        }
    }

    private fun requestJson(
        method: String,
        path: String,
        contentType: String? = null,
        body: String? = null
    ): JSONObject {
        val rawUrl = if (path.startsWith("http")) path else "$BASE_URL$path"
        val resolvedUrl = networkConfig.resolve(rawUrl)
        val connection = (URL(resolvedUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("Origin", networkConfig.requestOrigin)
            setRequestProperty("Referer", networkConfig.requestReferer)
            cookieProvider(resolvedUrl)?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Cookie", it) }
            contentType?.let { setRequestProperty("Content-Type", it) }
            if (method == "POST") {
                doOutput = true
                outputStream.use { it.write(body.orEmpty().toByteArray(StandardCharsets.UTF_8)) }
            }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val payload = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) throw IOException("教务请求失败（$code）")
        return runCatching { JSONObject(payload) }
            .getOrElse { throw IOException("教务返回的不是 JSON，登录可能已过期。") }
    }

    private fun normalizeWeeks(value: String): String = value
        .replace(",", "、")
        .replace("(", "")
        .replace(")", "")
        .replace("周", "")
        .trim('、', ' ')

    private fun stripBrackets(value: String): String = value.replace(Regex("\\[.*?]"), "").trim()

    private fun parseSectionRange(value: String): Pair<Int, Int>? {
        val parts = value.split("-")
        if (parts.size != 2) return null
        return (SECTION_MAP[parts[0]] ?: return null) to (SECTION_MAP[parts[1]] ?: return null)
    }

    private fun encodeFormBody(params: Map<String, String>): String = params.entries.joinToString("&") {
        "${urlEncode(it.key)}=${urlEncode(it.value)}"
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private data class SlotKey(
        val name: String, val day: Int, val begin: Int, val end: Int, val location: String, val weeks: String
    )

    private fun slotKey(row: NeuCourseRow) =
        SlotKey(row.courseName, row.dayOfWeek, row.beginSection, row.endSection, row.location, row.weeks)

    private fun mergeRows(rows: List<NeuCourseRow>): List<NeuCourseRow> {
        val grouped = LinkedHashMap<SlotKey, MutableList<NeuCourseRow>>()
        rows.forEach { grouped.getOrPut(slotKey(it)) { mutableListOf() }.add(it) }
        return grouped.values.map { matches ->
            val first = matches.first()
            first.copy(
                teacher = matches.flatMap { it.teacher.split(Regex("[,，、]")) }
                    .map(String::trim).filter(String::isNotBlank).distinct().joinToString("、")
                    .ifBlank { first.teacher },
                campus = matches.firstNotNullOfOrNull { it.campus.takeIf(String::isNotBlank) }.orEmpty()
            )
        }
    }

    companion object {
        private const val BASE_URL = "https://jwxt.neu.edu.cn"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        private val DAY_OF_WEEK_MAP = mapOf(
            "星期一" to 1, "星期二" to 2, "星期三" to 3, "星期四" to 4,
            "星期五" to 5, "星期六" to 6, "星期日" to 7, "星期天" to 7
        )
        private val SECTION_MAP = (1..12).associateBy(
            keySelector = { listOf("第一节", "第二节", "第三节", "第四节", "第五节", "第六节", "第七节", "第八节", "第九节", "第十节", "第十一节", "第十二节")[it - 1] },
            valueTransform = { it }
        )
    }
}
