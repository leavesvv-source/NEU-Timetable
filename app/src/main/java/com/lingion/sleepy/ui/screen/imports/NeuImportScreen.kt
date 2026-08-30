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
import com.lingion.sleepy.data.neu.NeuImportPayload
import com.lingion.sleepy.data.neu.NeuJwxtClient
import com.lingion.sleepy.data.neu.NeuNetworkConfig
import com.lingion.sleepy.data.neu.NeuNetworkDetector
import com.lingion.sleepy.ui.theme.SleepyTheme
import kotlinx.coroutines.launch

/** 东北大学专用：官方 WebView 登录后，直接读取新教务 API 并进入课表预览。 */
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

    val client = remember(networkConfig) {
        networkConfig?.let { config ->
            NeuJwxtClient(config) { targetUrl ->
                CookieManager.getInstance().run {
                    flush()
                    listOf(
                        getCookie(targetUrl).orEmpty(),
                        getCookie("https://jwxt.neu.edu.cn").orEmpty(),
                        getCookie("https://webvpn.neu.edu.cn").orEmpty()
                    ).filter(String::isNotBlank).distinct().joinToString("; ").ifBlank { null }
                }
            }
        }
    }

    fun detectNetwork() {
        if (loading) return
        loading = true
        scope.launch {
            runCatching { NeuNetworkDetector.detect() }
                .onSuccess { config ->
                    networkConfig = config
                    status = "已连接：${config.modeLabel}。请在下方东北大学官方页面登录。"
                    webView?.loadUrl(config.loginUrl)
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

        Card(colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(status, style = MaterialTheme.typography.bodySmall, color = colors.onSurface)
                user?.let {
                    Text("${it.userName}（${it.userId}）", style = MaterialTheme.typography.bodyMedium, color = colors.primary)
                }
                Text(
                    "登录发生在东北大学官方页面；本应用不保存密码，也不把 Cookie 或课表上传到第三方服务器。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = termCode,
            onValueChange = { termCode = it.trim() },
            label = { Text("学期代码，例如 2026-2027-1") },
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
                    val currentClient = client
                    if (currentClient == null) {
                        status = "请先完成网络检测。"
                        return@Button
                    }
                    loading = true
                    scope.launch {
                        runCatching { currentClient.fetchCurrentUser() }
                            .onSuccess {
                                user = it
                                if (termCode.isBlank()) termCode = it.defaultTermCode
                                status = "登录有效；当前学期 ${it.termName}。可以直接导入。"
                            }
                            .onFailure { status = "未检测到有效登录：${it.message}" }
                        loading = false
                    }
                },
                enabled = !loading && client != null,
                modifier = Modifier.weight(1f)
            ) { Text("检测登录") }
        }

        Button(
            onClick = {
                val currentClient = client
                val selectedTerm = termCode.trim()
                if (currentClient == null) {
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
                        val rows = currentClient.fetchSchedule(selectedTerm)
                        val mapped = NeuCourseMapper.mapRows(rows)
                        if (mapped.isEmpty()) error("课表记录存在，但周次解析后为空。")
                        val startDate = currentClient.fetchTermStartDate(selectedTerm)
                        NeuImportPayload(
                            courses = mapped,
                            startDate = startDate,
                            timeRows = NeuCourseMapper.defaultTimeRows(rows),
                            termCode = selectedTerm,
                            termName = user?.termName.orEmpty()
                        )
                    }.onSuccess {
                        status = "已读取 ${it.courses.size} 条课程，正在进入预览。"
                        onReady(it)
                    }.onFailure {
                        status = "课表读取失败：${it.message}"
                    }
                    loading = false
                }
            },
            enabled = !loading && client != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text("从东北大学教务导入") }

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
            key(config.mode) {
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
                            loadUrl(config.loginUrl)
                        }.also { webView = it }
                    }
                )
            }
        }
    }
}
