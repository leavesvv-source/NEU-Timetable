# 东大课表

面向东北大学 2026 新版本科教务系统的无广告 Android 课表。

> [!IMPORTANT]
> 这是学生自用并开源的非官方项目，与东北大学及其教务系统运营方没有隶属或合作关系。

当前第一版目标：

- 校园网 / 东北大学 VPN 直连，校外自动回退学校 WebVPN
- 在东北大学官方页面完成登录，不保存密码
- 直接读取当前学期、课程、实验课、教师、教室、周次与校区
- 导入前确认开学日期以及南湖 / 浑南作息
- 周课表、今日课表、必要的课程修正与学期管理
- 桌面小组件、课程提醒、深色模式
- Anthropic 风格的暖纸色界面、陶土橙强调色和低饱和课程卡片
- 只保留东北大学官方教务导入，不提供泛用文件或文本导入
- 无广告、无跟踪、无开发者后端；课表只保存在本机 Room 数据库

## 构建

要求 JDK 17+、Android SDK 36：

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat testDebugUnitTest assembleDebug
```

主流 Android 手机测试安装包：

`app/build/outputs/apk/release/app-arm64-v8a-release.apk`

当前首版 Release 使用本机 Android 调试证书签名，仅用于安装测试；正式发布前应换成独立发布密钥。

## 隐私边界

教务登录由应用内 WebView 打开东北大学官方域名完成。网络请求仅访问：

- `jwxt.neu.edu.cn`
- `webvpn.neu.edu.cn`

本应用不要求用户把账号、密码或 Cookie 交给开发者，不接入广告或统计 SDK。

## 使用与反馈

- Android 8.0 及以上
- 首次导入时需能访问东北大学教务系统；校外环境可使用学校 VPN 或应用内 WebVPN 回退
- 教务系统页面或接口调整后，导入功能可能需要同步适配
- 欢迎通过 GitHub Issue 提交可复现的问题；请勿上传账号、密码、Cookie 或含个人信息的课表截图

## 开源许可

本项目基于 [Sleepy](https://github.com/lingion/sleepy) 的 GPL-3.0 代码，并参考、改造
[NEU_Wisedu2Wakeup_for_Android](https://github.com/Zejin-Liu2022/NEU_Wisedu2Wakeup_for_Android)
的 MIT 许可东大适配逻辑。详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

派生项目继续按 [GPL-3.0](LICENSE) 发布。
