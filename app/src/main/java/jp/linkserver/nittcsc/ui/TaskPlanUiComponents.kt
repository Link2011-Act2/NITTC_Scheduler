package jp.linkserver.nittcsc.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.PlanEntity
import jp.linkserver.nittcsc.data.TaskEntity
import jp.linkserver.nittcsc.ui.components.AppButtonGroup
import jp.linkserver.nittcsc.update.AppUpdateInfo
import jp.linkserver.nittcsc.viewmodel.SchedulerUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

internal data class NaturalLanguageModelOption(
    val displayName: String,
    val modelFile: File
)

internal fun PlanEntity.toTaskEntityLike(): TaskEntity = TaskEntity(
    id = id,
    lessonId = lessonId,
    subject = subject,
    teacher = teacher,
    title = title,
    description = description,
    dueDate = dueDate,
    dueHour = dueHour,
    dueMinute = dueMinute,
    isCompleted = isCompleted,
    completedDate = completedDate,
    createdDate = createdDate,
    priority = priority,
    useTeacherMatching = useTeacherMatching,
    calendarEventId = calendarEventId,
    reminderEnabled = reminderEnabled,
    reminderDate = reminderDate,
    reminderHour = reminderHour,
    reminderMinute = reminderMinute,
    reminderCalendarEventId = reminderCalendarEventId
)

internal fun TaskEntity.toPlanEntityLike(existing: PlanEntity? = null): PlanEntity = PlanEntity(
    id = existing?.id ?: id,
    lessonId = lessonId,
    subject = subject,
    teacher = teacher,
    title = title,
    description = description,
    dueDate = dueDate,
    dueHour = dueHour,
    dueMinute = dueMinute,
    isCompleted = existing?.isCompleted ?: isCompleted,
    completedDate = existing?.completedDate ?: completedDate,
    createdDate = existing?.createdDate ?: createdDate,
    priority = priority,
    useTeacherMatching = useTeacherMatching,
    calendarEventId = existing?.calendarEventId ?: calendarEventId,
    reminderEnabled = reminderEnabled,
    reminderDate = reminderDate,
    reminderHour = reminderHour,
    reminderMinute = reminderMinute,
    reminderCalendarEventId = existing?.reminderCalendarEventId ?: reminderCalendarEventId
)


@Composable
internal fun UpdateNotificationBanner(
    updateInfo: AppUpdateInfo,
    onOpen: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 56.dp.toPx() }
    val dismissTargetPx = with(density) { 180.dp.toPx() }
    var dragOffsetY by remember(updateInfo.tagName) { mutableFloatStateOf(0f) }
    var dismissing by remember(updateInfo.tagName) { mutableStateOf(false) }
    var settleJob by remember(updateInfo.tagName) { mutableStateOf<Job?>(null) }

    fun settleBanner(dismiss: Boolean) {
        if (dismissing) return
        settleJob?.cancel()
        settleJob = scope.launch {
            if (dismiss) {
                dismissing = true
                val animation = Animatable(dragOffsetY)
                animation.animateTo(
                    targetValue = -dismissTargetPx,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                ) {
                    dragOffsetY = value
                }
                onDismiss()
            } else {
                val animation = Animatable(dragOffsetY)
                animation.animateTo(
                    targetValue = 0f,
                    animationSpec = spring()
                ) {
                    dragOffsetY = value
                }
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = dragOffsetY
                alpha = (1f - (-dragOffsetY / dismissTargetPx) * 0.45f).coerceIn(0.55f, 1f)
            }
            .pointerInput(updateInfo.tagName) {
                detectVerticalDragGestures(
                    onDragStart = {
                        if (!dismissing) settleJob?.cancel()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (!dismissing) {
                            change.consume()
                            dragOffsetY = (dragOffsetY + dragAmount).coerceAtMost(0f)
                        }
                    },
                    onDragEnd = {
                        settleBanner(dragOffsetY <= -dismissThresholdPx)
                    },
                    onDragCancel = {
                        settleBanner(dismiss = false)
                    }
                )
            }
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.update_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.update_banner_body, updateInfo.tagName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                modifier = Modifier.clickable(onClick = onDismiss),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f)
            ) {
                Box(
                    modifier = Modifier.padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.update_banner_dismiss),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
internal fun NaturalLanguageTaskAddDialog(
    modelOptions: List<NaturalLanguageModelOption>,
    onDismiss: () -> Unit,
    onCancelInference: () -> Unit,
    onCreateDraft: suspend (
        input: String,
        modelOption: NaturalLanguageModelOption,
        onStatusUpdate: (String) -> Unit
    ) -> Boolean
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val preferences = remember {
        context.getSharedPreferences("natural_language_task_ai", Context.MODE_PRIVATE)
    }
    var input by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var selectedModelFileName by rememberSaveable {
        mutableStateOf(preferences.getString("selected_model", null))
    }
    var isAnalyzing by rememberSaveable { mutableStateOf(false) }
    var analysisStatus by rememberSaveable { mutableStateOf("") }
    val selectedModel = modelOptions.firstOrNull { it.modelFile.name == selectedModelFileName }
        ?: modelOptions.firstOrNull()

    LaunchedEffect(modelOptions, selectedModelFileName) {
        if (selectedModel == null) {
            selectedModelFileName = null
        } else if (selectedModelFileName != selectedModel.modelFile.name) {
            selectedModelFileName = selectedModel.modelFile.name
            preferences.edit().putString("selected_model", selectedModel.modelFile.name).apply()
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isAnalyzing) onDismiss()
        },
        title = { Text(stringResource(R.string.dialog_natural_language_task_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.dialog_natural_language_task_add_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        showError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isAnalyzing,
                    minLines = 3,
                    label = { Text(stringResource(R.string.label_natural_language_task_input)) },
                    placeholder = { Text(stringResource(R.string.hint_natural_language_task_input)) },
                    isError = showError,
                    supportingText = {
                        if (showError) {
                            Text(stringResource(R.string.msg_natural_language_task_parse_failed))
                        }
                    }
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.label_natural_language_ai_model),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { modelMenuExpanded = true },
                            enabled = modelOptions.isNotEmpty() && !isAnalyzing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = selectedModel?.displayName
                                    ?: stringResource(R.string.msg_natural_language_no_model),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (modelOptions.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = null
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = modelMenuExpanded,
                            onDismissRequest = { modelMenuExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            modelOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.displayName) },
                                    onClick = {
                                        selectedModelFileName = option.modelFile.name
                                        preferences.edit()
                                            .putString("selected_model", option.modelFile.name)
                                            .apply()
                                        modelMenuExpanded = false
                                    },
                                    trailingIcon = {
                                        if (option.modelFile.name == selectedModel?.modelFile?.name) {
                                            Icon(Icons.Filled.Check, contentDescription = null)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    if (modelOptions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.desc_natural_language_no_model),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                if (isAnalyzing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text(
                            text = analysisStatus.ifBlank {
                                stringResource(R.string.msg_natural_language_ai_starting)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = input.isNotBlank() && selectedModel != null && !isAnalyzing,
                onClick = {
                    val model = selectedModel ?: return@Button
                    isAnalyzing = true
                    showError = false
                    analysisStatus = resources.getString(R.string.msg_natural_language_ai_starting)
                    scope.launch {
                        try {
                            val created = onCreateDraft(input, model) { status ->
                                analysisStatus = status
                            }
                            if (created) {
                                onDismiss()
                            } else {
                                showError = true
                            }
                        } catch (_: CancellationException) {
                            analysisStatus = ""
                        } catch (_: Exception) {
                            showError = true
                        } finally {
                            isAnalyzing = false
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.btn_analyze_and_create_task_draft))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (isAnalyzing) {
                        onCancelInference()
                        analysisStatus = resources.getString(R.string.msg_inference_cancelled)
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text(
                    stringResource(
                        if (isAnalyzing) R.string.notif_cancel_action else R.string.btn_cancel
                    )
                )
            }
        }
    )
}

@Composable
internal fun UnifiedTaskPlanScreen(
    modifier: Modifier = Modifier,
    uiState: SchedulerUiState,
    selectedTabIndex: Int,
    onSelectedTabIndexChange: (Int) -> Unit,
    onOpenTask: (TaskEntity) -> Unit,
    onCreateTask: () -> Unit,
    showNaturalLanguageTaskAdd: Boolean,
    onOpenNaturalLanguageTaskAdd: () -> Unit,
    onDeleteTask: (TaskEntity) -> Unit,
    onMarkTaskComplete: (TaskEntity) -> Unit,
    onMarkTaskIncomplete: (TaskEntity) -> Unit,
    onOpenPlan: (PlanEntity) -> Unit,
    onCreatePlan: () -> Unit,
    onDeletePlan: (PlanEntity) -> Unit,
    onMarkPlanComplete: (PlanEntity) -> Unit,
    onMarkPlanIncomplete: (PlanEntity) -> Unit,
    showCreateAction: Boolean = true
) {
    val tabs = listOf(
        stringResource(R.string.tab_tasks) to 0,
        stringResource(R.string.tab_plans) to 1
    )

    Column(modifier = modifier.fillMaxSize()) {
        AppButtonGroup(
            options = tabs.map { it.first },
            selectedIndex = selectedTabIndex,
            onSelectedIndexChange = onSelectedTabIndexChange,
            modifier = Modifier.fillMaxWidth()
        )

        when (selectedTabIndex) {
            0 -> TaskScreen(
                modifier = Modifier.fillMaxSize(),
                tasks = uiState.incompleteTasks,
                completedTasks = uiState.tasks.filter { it.isCompleted },
                showWeekdayOnDates = uiState.settings?.showWeekdayOnDates ?: false,
                focusTaskId = null,
                onFocusHandled = {},
                onOpenTaskEditor = { task: TaskEntity? ->
                    if (task == null) onCreateTask() else onOpenTask(task)
                },
                onDeleteTask = onDeleteTask,
                onMarkComplete = onMarkTaskComplete,
                onMarkIncomplete = onMarkTaskIncomplete,
                showNaturalLanguageTaskAdd = showNaturalLanguageTaskAdd,
                onOpenNaturalLanguageTaskAdd = onOpenNaturalLanguageTaskAdd,
                onCreateAlternateType = onCreatePlan,
                showCreateAction = showCreateAction
            )
            1 -> TaskScreen(
                modifier = Modifier.fillMaxSize(),
                tasks = uiState.incompletePlans.map { it.toTaskEntityLike() },
                completedTasks = uiState.plans.filter { it.isCompleted }.map { it.toTaskEntityLike() },
                showWeekdayOnDates = uiState.settings?.showWeekdayOnDates ?: false,
                focusTaskId = null,
                onFocusHandled = {},
                onOpenTaskEditor = { task: TaskEntity? ->
                    if (task == null) {
                        onCreatePlan()
                    } else {
                        val taskLike = task
                        uiState.plans.firstOrNull { it.id == taskLike.id }?.let { onOpenPlan(it) }
                    }
                },
                onDeleteTask = { task ->
                    uiState.plans.firstOrNull { it.id == task.id }?.let { onDeletePlan(it) }
                },
                onMarkComplete = { task ->
                    uiState.plans.firstOrNull { it.id == task.id }?.let { onMarkPlanComplete(it) }
                },
                onMarkIncomplete = { task ->
                    uiState.plans.firstOrNull { it.id == task.id }?.let { onMarkPlanIncomplete(it) }
                },
                onCreateAlternateType = onCreateTask,
                showCreateAction = showCreateAction,
                isPlan = true
            )
        }
    }
}
