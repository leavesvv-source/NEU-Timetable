package com.lingion.sleepy.data.neu

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 东北大学教务网络入口。
 *
 * 逻辑源自 Zejin-Liu2022/NEU_Wisedu2Wakeup_for_Android（MIT），并在本项目中
 * 改造成课表 App 的内置导入层。这里只在校园网直连与学校 WebVPN 之间切换，
 * 不经过任何第三方服务器。
 */
enum class NeuNetworkMode { DIRECT, WEB_VPN }

data class NeuNetworkConfig(val mode: NeuNetworkMode) {
    val modeLabel: String
        get() = if (mode == NeuNetworkMode.DIRECT) "校园网 / VPN 直连" else "学校 WebVPN"

    val loginUrl: String
        get() = if (mode == NeuNetworkMode.DIRECT) {
            "https://jwxt.neu.edu.cn/jwapp/sys/homeapp/index.do"
        } else {
            NeuWebVpnMapper.map("https://jwxt.neu.edu.cn/jwapp/sys/homeapp/index.do")
        }

    val requestOrigin: String
        get() = if (mode == NeuNetworkMode.DIRECT) {
            "https://jwxt.neu.edu.cn"
        } else {
            "https://webvpn.neu.edu.cn"
        }

    val requestReferer: String
        get() = resolve("https://jwxt.neu.edu.cn/jwapp/sys/homeapp/home/index.html?av=&contextPath=/jwapp")

    fun resolve(rawUrl: String): String =
        if (mode == NeuNetworkMode.DIRECT) rawUrl else NeuWebVpnMapper.map(rawUrl)
}

object NeuNetworkDetector {
    suspend fun detect(): NeuNetworkConfig = withContext(Dispatchers.IO) {
        if (canReach("http://jwxt.neu.edu.cn") || canReach("https://jwxt.neu.edu.cn")) {
            return@withContext NeuNetworkConfig(NeuNetworkMode.DIRECT)
        }
        if (canReach("https://webvpn.neu.edu.cn")) {
            return@withContext NeuNetworkConfig(NeuNetworkMode.WEB_VPN)
        }
        throw IllegalStateException("无法访问东北大学教务系统或 WebVPN，请检查网络。")
    }

    private fun canReach(url: String): Boolean = try {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3_000
            readTimeout = 3_000
            instanceFollowRedirects = false
            requestMethod = "GET"
        }
        val code = connection.responseCode
        connection.disconnect()
        code in 200..399
    } catch (_: Exception) {
        false
    }
}

object NeuWebVpnMapper {
    private const val WEBVPN_ROOT = "https://webvpn.neu.edu.cn"
    private const val PASS_PREFIX = "62304135386136393339346365373340"
    private const val PASS_QR_PATH =
        "https://webvpn.neu.edu.cn/https/62304135386136393339346365373340a0e0b72cc4cb43c8bc1d6f66c806db"
    private const val AES_KEY = "b0A58a69394ce73@"

    fun map(rawUrl: String): String {
        val uri = URI(rawUrl)
        val scheme = uri.scheme ?: return rawUrl
        val host = uri.host ?: return rawUrl
        val path = (uri.rawPath ?: "/").trimStart('/')
        val pathWithQuery = if (uri.rawQuery.isNullOrBlank()) path else "$path?${uri.rawQuery}"

        if (pathWithQuery.contains("qyQrLogin")) {
            val suffix = if (pathWithQuery.contains("?")) "&" else "?"
            return "$scheme://$host/$pathWithQuery${suffix}service=https://webvpn.neu.edu.cn/login?cas_login=true"
        }
        if (pathWithQuery.contains("checkQRCodeScan")) {
            val parts = pathWithQuery.split("?", limit = 2)
            val updated = "${parts.firstOrNull().orEmpty()}?vpn-12-o2-pass.neu.edu.cn&${parts.getOrNull(1).orEmpty()}"
            return "$PASS_QR_PATH/$updated"
        }

        val encryptedHostHex = encryptHost(host).toHexString()
        return "$WEBVPN_ROOT/$scheme/$PASS_PREFIX$encryptedHostHex/$pathWithQuery"
    }

    private fun encryptHost(host: String): ByteArray {
        val paddedLength = (host.length / 16) * 16 + 16
        val padded = host.padEnd(paddedLength, '\u0000')
        val key = AES_KEY.toByteArray(StandardCharsets.UTF_8)
        val cipher = Cipher.getInstance("AES/CFB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(key))
        return cipher.doFinal(padded.toByteArray(StandardCharsets.UTF_8)).copyOfRange(0, host.length)
    }

    private fun ByteArray.toHexString(): String {
        val hex = "0123456789abcdef"
        return buildString(size * 2) {
            for (byte in this@toHexString) {
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }
}
