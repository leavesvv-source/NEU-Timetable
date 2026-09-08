package com.lingion.sleepy.ui.screen.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lingion.sleepy.R
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.ui.screen.schedule.ScheduleViewModel
import com.lingion.sleepy.ui.theme.SleepyTheme
import com.lingion.sleepy.ui.theme.noRippleClickable
import kotlinx.coroutines.launch

/** 东大专用管理页：只提供官方教务导入和当前课表必要修正。 */
@Composable
fun ManagementPage(
    onNeuImportRequested: () -> Unit,
    onEditCurrentTable: () -> Unit,
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = SleepyTheme.colors
    val table = state.currentTable
    var showMergeDialog by remember { mutableStateOf(false) }
    var mergeInProgress by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.tab_manage),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.onBackground
                    )
                    Text(
                        text = stringResource(R.string.manage_neu_only_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            item {
                ManageCard(
                    icon = Icons.Outlined.School,
                    title = stringResource(R.string.manage_neu_import),
                    subtitle = stringResource(R.string.manage_neu_import_sub),
                    emphasized = true,
                    onClick = onNeuImportRequested
                )
            }

            item {
                ManageCard(
                    icon = Icons.AutoMirrored.Outlined.MergeType,
                    title = "融合课表",
                    subtitle = if (state.tables.size >= 2) {
                        "选择两张课表，生成一张包含全部课程的新课表"
                    } else {
                        "请先分别导入本科课表和研究生课表"
                    },
                    onClick = { showMergeDialog = true }
                )
            }

            if (table != null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SleepyTheme.shapes.large)
                            .background(colors.surfaceContainer)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.manage_current_table),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.primary
                        )
                        Text(
                            text = table.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface
                        )
                        Text(
                            text = stringResource(R.string.table_info, table.startDate, state.currentWeek, state.courses.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
                item {
                    ManageCard(
                        icon = Icons.Outlined.Edit,
                        title = stringResource(R.string.manage_edit_current),
                        subtitle = stringResource(R.string.manage_edit_current_sub),
                        onClick = onEditCurrentTable
                    )
                }
            }
        }
    }

    if (showMergeDialog) {
        if (state.tables.size < 2) {
            AlertDialog(
                onDismissRequest = { showMergeDialog = false },
                title = { Text("暂时无法融合") },
                text = { Text("至少需要两张课表。请先从本科教务和研究生教务分别导入一次。") },
                confirmButton = {
                    TextButton(onClick = { showMergeDialog = false }) { Text("知道了") }
                }
            )
        } else {
            TableMergeDialog(
                tables = state.tables,
                loading = mergeInProgress,
                onDismiss = { if (!mergeInProgress) showMergeDialog = false },
                onConfirm = { firstId, secondId, name ->
                    mergeInProgress = true
                    viewModel.mergeTables(firstId, secondId, name) { result ->
                        mergeInProgress = false
                        result.onSuccess {
                            showMergeDialog = false
                            scope.launch { snackbarHostState.showSnackbar("融合完成，已切换到新课表") }
                        }.onFailure { error ->
                            scope.launch {
                                snackbarHostState.showSnackbar("融合失败：${error.message.orEmpty()}")
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun TableMergeDialog(
    tables: List<TimeTableEntity>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long, String) -> Unit
) {
    val firstDefault = tables.firstOrNull { it.name.contains("本科") } ?: tables.first()
    val secondDefault = tables.firstOrNull {
        it.id != firstDefault.id && it.name.contains("研究生")
    } ?: tables.first { it.id != firstDefault.id }
    var firstId by remember(tables) { mutableStateOf(firstDefault.id) }
    var secondId by remember(tables) { mutableStateOf(secondDefault.id) }
    var name by remember(tables) { mutableStateOf("本科 + 研究生") }
    val valid = firstId != secondId && name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("融合课表") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "原来的两张课表不会改变。新课表采用第一张课表的节次时间；开学日期不同时会自动对齐周次。",
                    style = MaterialTheme.typography.bodySmall,
                    color = SleepyTheme.colors.onSurfaceVariant
                )
                TablePicker(
                    label = "第一张课表（节次时间基准）",
                    selectedId = firstId,
                    tables = tables,
                    onSelected = { firstId = it }
                )
                TablePicker(
                    label = "第二张课表",
                    selectedId = secondId,
                    tables = tables,
                    onSelected = { secondId = it }
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("新课表名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (firstId == secondId) {
                    Text(
                        "请选择两张不同的课表",
                        style = MaterialTheme.typography.bodySmall,
                        color = SleepyTheme.colors.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(firstId, secondId, name) },
                enabled = valid && !loading
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("生成融合课表")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") }
        }
    )
}

@Composable
private fun TablePicker(
    label: String,
    selectedId: Long,
    tables: List<TimeTableEntity>,
    onSelected: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = tables.firstOrNull { it.id == selectedId }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = SleepyTheme.colors.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected?.name.orEmpty(), modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                tables.forEach { table ->
                    DropdownMenuItem(
                        text = { Text(table.name) },
                        onClick = {
                            onSelected(table.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ManageCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    emphasized: Boolean = false,
    onClick: () -> Unit
) {
    val colors = SleepyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SleepyTheme.shapes.large)
            .background(if (emphasized) colors.primaryContainer else colors.surfaceContainer)
            .noRippleClickable(onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(SleepyTheme.shapes.medium)
                .background(if (emphasized) colors.primary else colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (emphasized) colors.onPrimary else colors.onSurfaceVariant,
                modifier = Modifier.size(23.dp)
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (emphasized) colors.onPrimaryContainer else colors.onSurface
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (emphasized) colors.onPrimaryContainer.copy(alpha = 0.78f) else colors.onSurfaceVariant
            )
        }
    }
}
