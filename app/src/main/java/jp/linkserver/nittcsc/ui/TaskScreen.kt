package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import jp.linkserver.nittcsc.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateContentSize
import jp.linkserver.nittcsc.data.TaskEntity
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.components.AppFabMenu
import jp.linkserver.nittcsc.ui.components.AppFabMenuAction
import jp.linkserver.nittcsc.ui.components.AppFloatingActionButton
import jp.linkserver.nittcsc.ui.theme.ExpressiveTaskHeroShape
import jp.linkserver.nittcsc.ui.theme.LocalUiDesignMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

private val LegacyTaskBadgeContainer = Color(0xFF93000A)
private val LegacyTaskBadgeOnContainer = Color(0xFFFFDAD6)

@Composable
fun TaskScreen(
    modifier: Modifier = Modifier,
    tasks: List<TaskEntity>,
    completedTasks: List<TaskEntity>,
    showWeekdayOnDates: Boolean = false,
    focusTaskId: Long? = null,
    onFocusHandled: () -> Unit = {},
    onOpenTaskEditor: (TaskEntity?) -> Unit,
    onDeleteTask: (TaskEntity) -> Unit,
    onMarkComplete: (TaskEntity) -> Unit,
    onMarkIncomplete: (TaskEntity) -> Unit,
    showNaturalLanguageTaskAdd: Boolean = false,
    onOpenNaturalLanguageTaskAdd: () -> Unit = {},
    onCreateAlternateType: (() -> Unit)? = null,
    showCreateAction: Boolean = true,
    isPlan: Boolean = false
) {
    var deletingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var expandCompleted by remember { mutableStateOf(false) }
    var fabMenuExpanded by rememberSaveable(isPlan) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val useExpressiveDesign = LocalUiDesignMode.current == UiDesignMode.MATERIAL_3_EXPRESSIVE
    val initialToday = remember { LocalDate.now() }
    val currentDateTime = rememberCurrentDateTime(useExpressiveDesign)
    val today = currentDateTime?.toLocalDate() ?: initialToday

    val overdueTasks = remember(tasks, today) { tasks.filter { it.dueDate.isBefore(today) } }
    val todayTasks = remember(tasks, today) { tasks.filter { it.dueDate == today } }
    val upcomingTasks = remember(tasks, today) { tasks.filter { it.dueDate.isAfter(today) } }
    val completedTasksSorted = remember(completedTasks, today) {
        completedTasks.sortedWith(
            compareBy<TaskEntity> { abs(ChronoUnit.DAYS.between(today, it.dueDate)) }
                .thenBy { it.dueDate }
                .thenBy { it.dueHour }
                .thenBy { it.dueMinute }
        )
    }
    val expressiveDeadlineTask = remember(tasks, currentDateTime) {
        val now = currentDateTime ?: return@remember null
        val overdue = tasks
            .asSequence()
            .filter { it.dueDateTime().isBefore(now) }
            .maxByOrNull { it.dueDateTime() }
        overdue ?: tasks
            .asSequence()
            .filter { !it.dueDateTime().isBefore(now) }
            .minByOrNull { it.dueDateTime() }
    }

    LaunchedEffect(focusTaskId, tasks, completedTasksSorted, expandCompleted) {
        val targetId = focusTaskId ?: return@LaunchedEffect
        val isInCompleted = completedTasksSorted.any { it.id == targetId }
        if (isInCompleted && !expandCompleted) {
            expandCompleted = true
            return@LaunchedEffect
        }

        var targetIndex = -1
        var cursor = 0

        if (tasks.isNotEmpty()) {
            cursor += 1 // incomplete section header

            if (overdueTasks.isNotEmpty()) {
                cursor += 1 // overdue subheader
                val idx = overdueTasks.indexOfFirst { it.id == targetId }
                if (idx >= 0) targetIndex = cursor + idx
                cursor += overdueTasks.size
            }

            if (targetIndex < 0 && todayTasks.isNotEmpty()) {
                cursor += 1 // today subheader
                val idx = todayTasks.indexOfFirst { it.id == targetId }
                if (idx >= 0) targetIndex = cursor + idx
                cursor += todayTasks.size
            }

            if (targetIndex < 0 && upcomingTasks.isNotEmpty()) {
                cursor += 1 // upcoming subheader
                val idx = upcomingTasks.indexOfFirst { it.id == targetId }
                if (idx >= 0) targetIndex = cursor + idx
                cursor += upcomingTasks.size
            }
        }

        if (targetIndex < 0 && completedTasksSorted.isNotEmpty()) {
            cursor += 1 // completed header
            if (expandCompleted) {
                val idx = completedTasksSorted.indexOfFirst { it.id == targetId }
                if (idx >= 0) targetIndex = cursor + idx
            }
        }

        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
            onFocusHandled()
        }
    }

    AdaptiveContentPane(
        modifier = modifier,
        maxWidth = FormContentMaxWidth
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Incomplete Tasks Section
            if (tasks.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (
                            useExpressiveDesign &&
                            !isPlan &&
                            expressiveDeadlineTask != null &&
                            currentDateTime != null
                        ) {
                            ExpressiveTaskDeadlineHero(
                                task = expressiveDeadlineTask,
                                now = currentDateTime,
                                showWeekdayOnDates = showWeekdayOnDates,
                                onOpen = { onOpenTaskEditor(expressiveDeadlineTask) },
                                onComplete = { onMarkComplete(expressiveDeadlineTask) }
                            )
                        }
                        Text(
                            text = stringResource(if (isPlan) R.string.label_incomplete_plans else R.string.label_incomplete_tasks),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                if (overdueTasks.isNotEmpty()) {
                    item {
                        TaskSubHeader(text = stringResource(R.string.section_task_overdue_count, overdueTasks.size))
                    }
                }
                items(overdueTasks) { task ->
                    TaskCard(
                        task = task,
                        showWeekdayOnDates = showWeekdayOnDates,
                        onEdit = { onOpenTaskEditor(it) },
                        onDelete = { deletingTask = it },
                        onMarkComplete = { onMarkComplete(it) },
                        showMarkComplete = true
                    )
                }

                if (todayTasks.isNotEmpty()) {
                    item {
                        TaskSubHeader(text = stringResource(R.string.section_task_today_count, todayTasks.size))
                    }
                }
                items(todayTasks) { task ->
                    TaskCard(
                        task = task,
                        showWeekdayOnDates = showWeekdayOnDates,
                        onEdit = { onOpenTaskEditor(it) },
                        onDelete = { deletingTask = it },
                        onMarkComplete = { onMarkComplete(it) },
                        showMarkComplete = true
                    )
                }

                if (upcomingTasks.isNotEmpty()) {
                    item {
                        TaskSubHeader(text = stringResource(R.string.section_task_upcoming_count, upcomingTasks.size))
                    }
                }
                items(upcomingTasks) { task ->
                    TaskCard(
                        task = task,
                        showWeekdayOnDates = showWeekdayOnDates,
                        onEdit = { onOpenTaskEditor(it) },
                        onDelete = { deletingTask = it },
                        onMarkComplete = { onMarkComplete(it) },
                        showMarkComplete = true
                    )
                }
            }

            // Completed Tasks Section
            if (completedTasksSorted.isNotEmpty()) {
                item {
                    CompletedTasksHeader(
                        isExpanded = expandCompleted,
                        onToggle = { expandCompleted = !expandCompleted },
                        count = completedTasksSorted.size,
                        isPlan = isPlan
                    )
                }

                if (expandCompleted) {
                    items(completedTasksSorted) { task ->
                        TaskCard(
                            task = task,
                            showWeekdayOnDates = showWeekdayOnDates,
                            onEdit = { onOpenTaskEditor(it) },
                            onDelete = { deletingTask = it },
                            onMarkIncomplete = { onMarkIncomplete(it) },
                            showMarkIncomplete = true,
                            isCompleted = true
                        )
                    }
                }
            }

            // Empty State
            if (tasks.isEmpty() && completedTasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(if (isPlan) R.string.msg_plan_no_plans else R.string.msg_task_no_tasks),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (showCreateAction && useExpressiveDesign && onCreateAlternateType != null) {
            val currentCreateLabel = stringResource(if (isPlan) R.string.cd_add_plan else R.string.cd_add_task)
            val alternateCreateLabel = stringResource(if (isPlan) R.string.cd_add_task else R.string.cd_add_plan)
            val naturalLanguageLabel = stringResource(R.string.btn_add_task_from_natural_language)
            AppFabMenu(
                actions = buildList {
                    add(
                        AppFabMenuAction(
                            label = currentCreateLabel,
                            icon = Icons.Filled.Add,
                            onClick = { onOpenTaskEditor(null) }
                        )
                    )
                    add(
                        AppFabMenuAction(
                            label = alternateCreateLabel,
                            icon = Icons.Filled.Edit,
                            onClick = onCreateAlternateType
                        )
                    )
                    if (showNaturalLanguageTaskAdd && !isPlan) {
                        add(
                            AppFabMenuAction(
                                label = naturalLanguageLabel,
                                icon = Icons.Filled.AutoFixHigh,
                                onClick = onOpenNaturalLanguageTaskAdd
                            )
                        )
                    }
                },
                expanded = fabMenuExpanded,
                onExpandedChange = { fabMenuExpanded = it },
                expandContentDescription = stringResource(R.string.cd_expand_create_menu),
                collapseContentDescription = stringResource(R.string.cd_collapse_create_menu),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        } else if (showCreateAction) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showNaturalLanguageTaskAdd && !isPlan) {
                    Button(onClick = onOpenNaturalLanguageTaskAdd) {
                        Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_add_task_from_natural_language))
                    }
                }
                AppFloatingActionButton(onClick = { onOpenTaskEditor(null) }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(if (isPlan) R.string.cd_add_plan else R.string.cd_add_task)
                    )
                }
            }
        }
    }

    if (deletingTask != null) {
        AlertDialog(
            onDismissRequest = { deletingTask = null },
            title = { Text(stringResource(if (isPlan) R.string.dialog_plan_delete_title else R.string.dialog_task_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.dialog_task_delete_message,
                        deletingTask?.title ?: ""
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    deletingTask?.let(onDeleteTask)
                    deletingTask = null
                }) {
                    Text(stringResource(R.string.btn_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTask = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

private fun TaskEntity.dueDateTime(): LocalDateTime =
    dueDate.atTime(dueHour.coerceIn(0, 23), dueMinute.coerceIn(0, 59))

@Composable
private fun ExpressiveTaskDeadlineHero(
    task: TaskEntity,
    now: LocalDateTime,
    showWeekdayOnDates: Boolean,
    onOpen: () -> Unit,
    onComplete: () -> Unit
) {
    val isOverdue = task.dueDateTime().isBefore(now)
    val containerColor = MaterialTheme.colorScheme.tertiaryContainer
    val contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    val statusContainerColor = if (isOverdue) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        contentColor.copy(alpha = 0.12f)
    }
    val statusContentColor = if (isOverdue) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        contentColor
    }
    val openLabel = stringResource(R.string.m3e_task_open_description)
    var completionPending by remember(task.id) { mutableStateOf(false) }
    LaunchedEffect(task.id, completionPending) {
        if (completionPending) {
            delay(1_000L)
            completionPending = false
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
            .clickable(
                onClickLabel = openLabel,
                role = Role.Button,
                onClick = onOpen
            ),
        shape = ExpressiveTaskHeroShape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusContainerColor,
                    contentColor = statusContentColor
                ) {
                    Text(
                        text = stringResource(
                            if (isOverdue) R.string.m3e_task_overdue_deadline else R.string.m3e_task_next_deadline
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                FilledIconButton(
                    onClick = {
                        if (!completionPending) {
                            completionPending = true
                            onComplete()
                        }
                    },
                    shapes = IconButtonDefaults.shapes(
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomEnd = 6.dp,
                            bottomStart = 18.dp
                        ),
                        pressedShape = RoundedCornerShape(999.dp)
                    ),
                    modifier = Modifier.size(48.dp),
                    enabled = !completionPending,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = contentColor,
                        contentColor = containerColor
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = stringResource(R.string.m3e_task_complete_description),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Text(
                text = task.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatDateTimeForDisplay(
                    task.dueDate,
                    task.dueHour,
                    task.dueMinute,
                    showWeekdayOnDates
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (task.subject.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = contentColor.copy(alpha = 0.1f),
                    contentColor = contentColor
                ) {
                    Text(
                        text = task.subject,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isOverdue) {
                Text(
                    text = stringResource(R.string.m3e_task_overdue_supporting),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun TaskSubHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskCard(
    task: TaskEntity,
    showWeekdayOnDates: Boolean,
    onEdit: (TaskEntity) -> Unit,
    onDelete: (TaskEntity) -> Unit,
    onMarkComplete: ((TaskEntity) -> Unit)? = null,
    onMarkIncomplete: ((TaskEntity) -> Unit)? = null,
    showMarkComplete: Boolean = false,
    showMarkIncomplete: Boolean = false,
    isCompleted: Boolean = false
) {
    val today = LocalDate.now()
    val currentTime = LocalTime.now()
    val dueTime = LocalTime.of(task.dueHour, task.dueMinute)
    val isOverdue = task.dueDate.isBefore(today) || 
                    (task.dueDate == today && currentTime.isAfter(dueTime))

    val dueStatusText = if (task.completedDate != null) {
        stringResource(
            R.string.label_completed_date,
            formatDateForDisplay(task.completedDate, showWeekdayOnDates)
        )
    } else {
        when {
            task.dueDate.isBefore(today) -> {
                val days = ChronoUnit.DAYS.between(task.dueDate, today)
                stringResource(R.string.label_task_due_overdue_days, days)
            }
            task.dueDate == today && currentTime.isAfter(dueTime) -> {
                // 今日で時刻が過ぎている場合は時間単位で表示
                val hours = ChronoUnit.HOURS.between(dueTime, currentTime)
                stringResource(R.string.label_task_due_overdue_hours, hours)
            }
            task.dueDate == today -> stringResource(R.string.label_task_due_today)
            else -> {
                val days = ChronoUnit.DAYS.between(today, task.dueDate)
                if (days == 1L) stringResource(R.string.label_task_due_tomorrow)
                else stringResource(R.string.label_task_due_remaining_days, days)
            }
        }
    }

    val dueStatusContainer = when {
        task.completedDate != null -> MaterialTheme.colorScheme.surfaceContainerHighest
        isOverdue -> LegacyTaskBadgeContainer
        task.dueDate == today -> LegacyTaskBadgeContainer
        else -> {
            val days = ChronoUnit.DAYS.between(today, task.dueDate)
            if (days == 1L) LegacyTaskBadgeContainer
            else MaterialTheme.colorScheme.secondaryContainer
        }
    }

    val dueStatusContent = when {
        task.completedDate != null -> MaterialTheme.colorScheme.onSurfaceVariant
        isOverdue -> LegacyTaskBadgeOnContainer
        task.dueDate == today -> LegacyTaskBadgeOnContainer
        else -> {
            val days = ChronoUnit.DAYS.between(today, task.dueDate)
            if (days == 1L) LegacyTaskBadgeOnContainer
            else MaterialTheme.colorScheme.onSecondaryContainer
        }
    }

    val priorityText = when (task.priority) {
        1 -> stringResource(R.string.label_task_priority_high)
        -1 -> stringResource(R.string.label_task_priority_low)
        else -> stringResource(R.string.label_task_priority_normal)
    }

    val priorityContainer = when (task.priority) {
        1 -> LegacyTaskBadgeContainer
        -1 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }

    val priorityContent = when (task.priority) {
        1 -> LegacyTaskBadgeOnContainer
        -1 -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val backgroundColor = if (isCompleted) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    val density = LocalDensity.current
    val actionWidth = 92.dp
    val actionWidthPx = with(density) { actionWidth.toPx() }
    val completionStrokeColor = MaterialTheme.colorScheme.outline
    val completionFillColor = MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current
    var dragOffsetPx by remember(task.id) { mutableFloatStateOf(0f) }
    var pendingComplete by remember(task.id) { mutableStateOf(false) }
    var startFadeOut by remember(task.id) { mutableStateOf(false) }
    val animatedOffsetPx by animateFloatAsState(
        targetValue = dragOffsetPx,
        animationSpec = spring(),
        label = "taskCardOffset"
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (startFadeOut) 0f else 1f,
        animationSpec = spring(),
        label = "taskCardFade"
    )
    val scope = rememberCoroutineScope()

    fun settleSwipe() {
        dragOffsetPx = if (dragOffsetPx < -actionWidthPx * 0.5f) -actionWidthPx else 0f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .width(actionWidth)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        scope.launch {
                            dragOffsetPx = 0f
                            onDelete(task)
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.btn_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffsetPx.roundToInt(), 0) }
                .alpha(cardAlpha)
                .pointerInput(task.id) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            if (pendingComplete) return@detectHorizontalDragGestures
                            dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(-actionWidthPx, 0f)
                        },
                        onDragEnd = { settleSwipe() },
                        onDragCancel = { settleSwipe() }
                    )
                }
                .clickable(enabled = !pendingComplete) { onEdit(task) }
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(16.dp)
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    val toggleComplete: () -> Unit = {
                        if (isCompleted) {
                            onMarkIncomplete?.invoke(task)
                        } else {
                            if (!pendingComplete) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                pendingComplete = true
                                scope.launch {
                                    delay(500)
                                    startFadeOut = true
                                    delay(250)
                                    onMarkComplete?.invoke(task)
                                }
                            }
                        }
                        Unit
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(enabled = !pendingComplete, onClick = toggleComplete),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = 2.5.dp.toPx()
                            if (isCompleted || pendingComplete) {
                                drawCircle(color = completionFillColor)
                            } else {
                                drawCircle(
                                    color = completionStrokeColor,
                                    style = Stroke(
                                        width = stroke,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 4f), 0f)
                                    )
                                )
                            }
                        }
                        if (isCompleted || pendingComplete) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = if (showMarkIncomplete) stringResource(R.string.btn_mark_incomplete) else stringResource(R.string.btn_mark_complete),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                            )
                            if (task.description?.isNotBlank() == true) {
                                Text(
                                    text = task.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                )
                            }
                        }
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(shape = RoundedCornerShape(8.dp), color = priorityContainer) {
                                Text(
                                    text = priorityText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = priorityContent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Text(
                        text = formatDateTimeForDisplay(task.dueDate, task.dueHour, task.dueMinute, showWeekdayOnDates),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (task.subject.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = task.subject,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (task.teacher?.isNotBlank() == true) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = task.teacher,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Surface(shape = RoundedCornerShape(999.dp), color = dueStatusContainer) {
                            Text(
                                text = dueStatusText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = dueStatusContent,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                }
            }
        }
    }
}

@Composable
private fun CompletedTasksHeader(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    count: Int,
    isPlan: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(if (isPlan) R.string.section_completed_plans_count else R.string.section_completed_tasks_count, count),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Icon(
            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null
        )
    }
}
