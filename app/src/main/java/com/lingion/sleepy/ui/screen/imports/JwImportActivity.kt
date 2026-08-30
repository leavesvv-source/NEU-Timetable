package com.lingion.sleepy.ui.screen.imports

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.R
import com.lingion.sleepy.data.jw.JwCourse
import com.lingion.sleepy.data.jw.JwImportViewModel
import com.lingion.sleepy.ui.component.DatePickerField
import com.lingion.sleepy.ui.component.TimeSlotEditor
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.SleepyThemeProvider
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.TimeTableUtils
import kotlinx.coroutines.launch

/** 东北大学专用教务导入，不再包含学校选择、通用协议或文件导入。 */
class JwImportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val systemDark = isSystemInDarkTheme()
            val dark = remember(systemDark) { AppPrefs.isDarkMode(this, systemDark) }
            val themeKey by AppPrefs.themeKeyFlow(this)
                .collectAsState(initial = AppPrefs.getThemeKey(this))

            SleepyThemeProvider(darkTheme = dark, themeKey = themeKey) {
                val importViewModel: JwImportViewModel = viewModel()
                val scope = rememberCoroutineScope()
                var parsedCourses by remember { mutableStateOf<List<JwCourse>>(emptyList()) }
                var startDate by remember { mutableStateOf("") }
                var timeRows by remember { mutableStateOf(emptyList<TimeTableUtils.TimeSlotRow>()) }
                var errorMsg by remember { mutableStateOf<String?>(null) }
                var statusMsg by remember { mutableStateOf<String?>(null) }
                var importFinished by remember { mutableStateOf(false) }

                if (importFinished) {
                    LaunchedEffect(Unit) { finish() }
                } else if (parsedCourses.isEmpty()) {
                    NeuImportScreen(
                        onReady = { payload ->
                            parsedCourses = payload.courses
                            startDate = payload.startDate
                            timeRows = payload.timeRows
                            statusMsg = null
                        },
                        onBack = { finish() }
                    )
                } else {
                    ImportConfirmationDialog(
                        courseCount = parsedCourses.size,
                        startDate = startDate,
                        onStartDateChange = { startDate = it },
                        timeRows = timeRows,
                        onTimeRowsChange = { timeRows = it },
                        onBack = { parsedCourses = emptyList() },
                        onConfirm = {
                            statusMsg = getString(R.string.import_parsing)
                            scope.launch {
                                runCatching {
                                    importViewModel.importAsNewTable(
                                        courses = parsedCourses,
                                        tableName = getString(R.string.jw_import_title, "东北大学"),
                                        startDate = startDate,
                                        timeJson = TimeTableUtils.buildTimeJsonFromRows(timeRows),
                                        nodesPerDay = timeRows.maxOfOrNull { it.node } ?: 0
                                    )
                                }.onSuccess { tableId ->
                                    Log.d("NeuImport", "tableId=$tableId courses=${parsedCourses.size}")
                                    importFinished = true
                                }.onFailure { error ->
                                    errorMsg = getString(R.string.jw_parse_failed, error.message.orEmpty())
                                    statusMsg = null
                                }
                            }
                        }
                    )
                }

                errorMsg?.let { message ->
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Card(colors = CardDefaults.cardColors(containerColor = SleepyTheme.colors.errorContainer)) {
                            Text(
                                text = message,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                color = SleepyTheme.colors.onErrorContainer
                            )
                        }
                    }
                }
                statusMsg?.let { message ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        Snackbar(Modifier.padding(16.dp)) { Text(message) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportConfirmationDialog(
    courseCount: Int,
    startDate: String,
    onStartDateChange: (String) -> Unit,
    timeRows: List<TimeTableUtils.TimeSlotRow>,
    onTimeRowsChange: (List<TimeTableUtils.TimeSlotRow>) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    val colors = SleepyTheme.colors
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onBack,
        title = {
            Column {
                Text("确认东北大学课表", color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text("已读取 $courseCount 条课程", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DatePickerField(
                    value = startDate,
                    onValueChange = onStartDateChange,
                    label = "第一周开始日期",
                    modifier = Modifier.fillMaxWidth(),
                    isError = validationError != null
                )
                validationError?.let {
                    Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall)
                }
                TimeSlotEditor(rows = timeRows, onRowsChange = onTimeRowsChange)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                validationError = validateImportConfig(startDate, timeRows)
                if (validationError == null) onConfirm()
            }) { Text("确认导入") }
        },
        dismissButton = { TextButton(onClick = onBack) { Text("返回") } }
    )
}

private fun validateImportConfig(
    startDate: String,
    timeRows: List<TimeTableUtils.TimeSlotRow>
): String? {
    if (!Regex("""^\d{4}-\d{2}-\d{2}$""").matches(startDate)) return "请确认第一周开始日期"
    val empty = timeRows.firstOrNull { it.start.isBlank() || it.end.isBlank() }
    if (empty != null) return "请填写第 ${empty.node} 节时间"
    val invalid = timeRows.firstOrNull {
        !Regex("""^\d{2}:\d{2}$""").matches(it.start) ||
            !Regex("""^\d{2}:\d{2}$""").matches(it.end) || it.start >= it.end
    }
    return invalid?.let { "第 ${it.node} 节时间不正确" }
}
