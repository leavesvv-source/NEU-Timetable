package com.lingion.sleepy.ui.screen.imports

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.lingion.sleepy.data.neu.NeuCourseMapper
import com.lingion.sleepy.data.neu.NeuCurrentUser
import com.lingion.sleepy.data.neu.NeuGraduateClient
import com.lingion.sleepy.data.neu.NeuGraduateTerm
import com.lingion.sleepy.data.neu.NeuImportPayload
import com.lingion.sleepy.data.neu.NeuJwxtClient
import com.lingion.sleepy.data.neu.NeuNetworkConfig
import com.lingion.sleepy.data.neu.NeuNetworkDetector
import com.lingion.sleepy.data.entity.CourseSource
import com.lingion.sleepy.ui.component.SegmentedSwitcher
import com.lingion.sleepy.ui.theme.SleepyTheme
import kotlinx.coroutines.launch

private enum class NeuImportPortal { UNDERGRADUATE, GRADUATE }

/** 东北大学专用：官方 WebView 登录后，分别读取本科或研究生课表并进入预览。 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NeuImportScreen(
    onReady: (NeuImportPayload) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val colors = SleepyTheme.colors
    var status by remember { mutableStateOf("正在检测校园网 / WebVPN…") }
    var networkConfig by remember { mutableStateOf<NeuNetworkConfig?>(null) }
    var user by remember { mutableStateOf<NeuCurrentUser?>(null) }
    var termCode by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var portal by remember { mutableStateOf(NeuImportPortal.UNDERGRADUATE) }

    fun cookieHeader(targetUrl: String): String? = CookieManager.getInstance().run {
        flush()
        listOf(
            getCookie(targetUrl).orEmpty(),
            getCookie("https://jwxt.neu.edu.cn").orEmpty(),
            getCookie("https://yjs.neu.edu.cn").orEmpty(),
            getCookie("https://pass.neu.edu.cn").orEmpty(),
            getCookie("https://webvpn.neu.edu.cn").orEmpty()
        ).filter(String::isNotBlank).distinct().joinToString("; ").ifBlank { null }
    }

    val undergraduateClient = remember(networkConfig) {
        networkConfig?.let { config ->
            NeuJwxtClient(config, ::cookieHeader)
        }
    }
    val graduateClient = remember(networkConfig) {
        networkConfig?.let { config ->
            NeuGraduateClient(config, ::cookieHeader)
        }
    }

    fun loginUrl(config: NeuNetworkConfig): String =
        if (portal == NeuImportPortal.UNDERGRADUATE) config.loginUrl else config.graduateLoginUrl

    fun detectNetwork() {
        if (loading) return
        loading = true
        scope.launch {
            runCatching { NeuNetworkDetector.detect() }
                .onSuccess { config ->
                    networkConfig = config
                    status = "已连接：${config.modeLabel}。请在下方东北大学官方页面登录。"
                    webView?.loadUrl(loginUrl(config))
                }
                .onFailure { status = "网络检测失败：${it.message}" }
            loading = false
        }
    }

    LaunchedEffect(Unit) { detectNetwork() }
    DisposableEffect(Unit) {
        CookieManager.getInstance().setAcceptCookie(true)
        onDispose { webView?.destroy() }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("东北大学教务导入", style = MaterialTheme.typography.titleLarge, color = colors.onBackground)
            TextButton(onClick = onBack) { Text("返回") }
        }

        SegmentedSwitcher(
            options = listOf(
                NeuImportPortal.UNDERGRADUATE to "本科教务",
                NeuImportPortal.GRADUATE to "研究生教务"
            ),
            selected = portal,
            onSelect = { selected ->
                if (selected != portal) {
                    portal = selected
                    user = null
                    termCode = if (selected == NeuImportPortal.GRADUATE) {
                        NeuGraduateTerm.defaultCode()
                    } else {
                        ""
                    }
                    status = if (selected == NeuImportPortal.GRADUATE) {
                        "请登录研究生教务后检测登录状态。"
                    } else {
                        "请登录本科教务后检测登录状态。"
                    }
                    networkConfig?.let { webView?.loadUrl(loginUrl(it)) }
                }
            }
        )

        Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(status, style = MaterialTheme.typography.bodySmall, color = colors.onSurface)
                user?.let {
                    Text("${it.userName}（${it.userId}）", style = MaterialTheme.typography.bodyMedium, color = colors.primary)
                }
                Text(
                    if (portal == NeuImportPortal.GRADUATE) {
                        "研究生课程来自东北大学研究生教务；与本科课表融合后，时间冲突课程会在网格中并排显示。"
                    } else {
                        "登录发生在东北大学官方页面；本应用不保存密码，也不把 Cookie 或课表上传到第三方服务器。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = termCode,
            onValueChange = { termCode = it.trim() },
            label = {
                Text(
                    if (portal == NeuImportPortal.UNDERGRADUATE) "学期代码，例如 2026-2027-1"
                    else "研究生学期代码，例如 20261"
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { detectNetwork() },
                enabled = !loading,
                modifier = Modifier.weight(1f)
            ) { Text("重测网络") }
            Button(
                onClick = {
                    if (networkConfig == null) {
                        status = "请先完成网络检测。"
                        return@Button
                    }
                    loading = true
                    scope.launch {
                        runCatching {
                            when (portal) {
                                NeuImportPortal.UNDERGRADUATE ->
                                    requireNotNull(undergraduateClient).fetchCurrentUser()
                                NeuImportPortal.GRADUATE ->
                                    requireNotNull(graduateClient).fetchCurrentUser()
                            }
                        }
                            .onSuccess {
                                user = it
                                if (termCode.isBlank()) termCode = it.defaultTermCode
                                status = "登录有效；当前学期 ${it.termName}。可以直接导入。"
                            }
                            .onFailure { status = "未检测到有效登录：${it.message}" }
                        loading = false
                    }
                },
                enabled = !loading && networkConfig != null,
                modifier = Modifier.weight(1f)
            ) { Text("检测登录") }
        }

        Button(
            onClick = {
                val selectedTerm = termCode.trim()
                if (networkConfig == null) {
                    status = "请先完成网络检测。"
                    return@Button
                }
                if (selectedTerm.isBlank()) {
                    status = "请先检测登录状态，或填写学期代码。"
                    return@Button
                }
                loading = true
                scope.launch {
                    runCatching {
                        when (portal) {
                            NeuImportPortal.UNDERGRADUATE -> {
                                val currentClient = requireNotNull(undergraduateClient)
                                val rows = currentClient.fetchSchedule(selectedTerm)
                                val mapped = NeuCourseMapper.mapRows(rows, CourseSource.UNDERGRADUATE)
                                if (mapped.isEmpty()) error("课表记录存在，但周次解析后为空。")
                                NeuImportPayload(
                                    courses = mapped,
                                    startDate = currentClient.fetchTermStartDate(selectedTerm),
                                    timeRows = NeuCourseMapper.defaultTimeRows(rows),
                                    termCode = selectedTerm,
                                    termName = user?.termName.orEmpty()
                                )
                            }
                            NeuImportPortal.GRADUATE -> {
                                val currentClient = requireNotNull(graduateClient)
                                val normalizedTerm = NeuGraduateTerm.normalize(selectedTerm)
                                    ?: error("研究生学期代码应为 20261，或 2026-2027-1。")
                                val schedule = currentClient.fetchSchedule(normalizedTerm)
                                val mapped = NeuCourseMapper.mapRows(schedule.rows, CourseSource.GRADUATE)
                                if (mapped.isEmpty()) error("研究生课表记录存在，但周次解析后为空。")
                                val resolvedStartDate = runCatching {
                                    currentClient.fetchTermStartDate(normalizedTerm)
                                }.getOrElse { NeuGraduateTerm.estimatedStartDate(normalizedTerm) }
                                NeuImportPayload(
                                    courses = mapped,
                                    startDate = resolvedStartDate,
                                    timeRows = schedule.timeRows.ifEmpty {
                                        NeuCourseMapper.defaultTimeRows(schedule.rows)
                                    },
                                    termCode = normalizedTerm,
                                    termName = NeuGraduateTerm.displayName(normalizedTerm)
                                )
                            }
                        }
                    }.onSuccess {
                        status = "已读取 ${it.courses.size} 条课程，正在进入预览。"
                        onReady(it)
                    }.onFailure {
                        status = "课表读取失败：${it.message}"
                    }
                    loading = false
                }
            },
            enabled = !loading && networkConfig != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (portal == NeuImportPortal.UNDERGRADUATE) "从本科教务导入"
                else "从研究生教务导入"
            )
        }

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        val config = networkConfig
        if (config == null) {
            Card(colors = CardDefaults.cardColors(containerColor = colors.errorContainer)) {
                Text(
                    "登录页尚未就绪。可连接校园网、东北大学 VPN，或确认 WebVPN 可访问后重试。",
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    color = colors.onErrorContainer
                )
            }
        } else {
            key(config.mode, portal) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 260.dp),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            webViewClient = WebViewClient()
                            webChromeClient = WebChromeClient()
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            loadUrl(loginUrl(config))
                        }.also { webView = it }
                    }
                )
            }
        }
    }
}
