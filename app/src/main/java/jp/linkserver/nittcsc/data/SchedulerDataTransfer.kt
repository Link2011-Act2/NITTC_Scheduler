package jp.linkserver.nittcsc.data

import androidx.room.withTransaction
import jp.linkserver.nittcsc.InternalFeatureFlags
import jp.linkserver.nittcsc.logic.PeriodLabelStyle
import jp.linkserver.nittcsc.logic.TimetableTerm
import jp.linkserver.nittcsc.logic.importedExamTimetableEnabled
import jp.linkserver.nittcsc.logic.academicYearForDate
import java.time.DayOfWeek
import java.time.LocalDate

internal class SchedulerDataTransfer(
    private val repository: SchedulerRepository,
    private val db: AppDatabase
) {
    private val dao: SchedulerDao = db.schedulerDao()

    private companion object {
        const val CURRENT_EXPORT_VERSION = 15
        const val MIN_SUPPORTED_IMPORT_VERSION = 1
        const val DATASET_TASKS = SchedulerRepository.DATASET_TASKS
        const val DATASET_PLANS = SchedulerRepository.DATASET_PLANS
        const val DATASET_LESSONS = SchedulerRepository.DATASET_LESSONS
        const val DATASET_DAY_TYPES = SchedulerRepository.DATASET_DAY_TYPES
        const val DATASET_LONG_BREAKS = SchedulerRepository.DATASET_LONG_BREAKS
        const val DATASET_CANCELLED_LESSONS = SchedulerRepository.DATASET_CANCELLED_LESSONS
        const val DATASET_CHANGED_LESSONS = SchedulerRepository.DATASET_CHANGED_LESSONS
        const val DATASET_LESSON_NOTES = SchedulerRepository.DATASET_LESSON_NOTES
        const val DATASET_EXAM_TIMETABLES = SchedulerRepository.DATASET_EXAM_TIMETABLES
        val SYNC_DATASET_KEYS = SchedulerRepository.SYNC_DATASET_KEYS
    }

    suspend fun exportAllData(): String {
        val settings = dao.getSettings()
        val lessons = dao.getLessonsOnce()
        val longBreaks = dao.getLongBreaksOnce()
        val dayTypes = dao.getDayTypesOnce()
        val tasks = dao.getTasksOnce()
        val plans = dao.getPlansOnce()
        val cancelledLessons = dao.getCancelledLessonsOnce()
        val changedLessons = dao.getChangedLessonsOnce()
        val lessonNotes = dao.getLessonNotesOnce()
        val examDaySchedules = dao.getExamDaySchedulesOnce()
        val examLessons = dao.getExamLessonsOnce()
        val lessonNotificationExclusions = dao.getLessonNotificationExclusionsOnce()

        val root = org.json.JSONObject()
        root.put("version", CURRENT_EXPORT_VERSION)
        root.put("exportedAt", LocalDate.now().toString())
        root.put("schema", "nittc-scheduler")

        if (settings != null) {
            root.put("settings", org.json.JSONObject().also { s ->
                s.put("termStart", settings.termStart.toString())
                s.put("termEnd", settings.termEnd.toString())
                s.put("activeAcademicYear", settings.activeAcademicYear)
                s.put("enableLocalAi", settings.enableLocalAi)
                s.put(
                    "enableNaturalLanguageTaskAdd",
                    settings.enableNaturalLanguageTaskAdd &&
                        InternalFeatureFlags.NATURAL_LANGUAGE_TASK_ADD
                )
                s.put("enableLessonNotes", true)
                s.put("hfToken", settings.hfToken)
                s.put("periodsPerDay", settings.periodsPerDay)
                s.put("periodDurationMin", settings.periodDurationMin)
                s.put("breakBetweenPeriodsMin", settings.breakBetweenPeriodsMin)
                s.put("lunchBreakMin", settings.lunchBreakMin)
                s.put("lunchAfterPeriod", settings.lunchAfterPeriod)
                s.put("firstPeriodStartHour", settings.firstPeriodStartHour)
                s.put("firstPeriodStartMinute", settings.firstPeriodStartMinute)
                s.put("useKosenMode", settings.useKosenMode)
                s.put("periodLabelStyle", settings.periodLabelStyle.name)
                s.put("enableSemesterTimetables", settings.enableSemesterTimetables)
                s.put("useDrawerNavigation", settings.useDrawerNavigation)
                s.put("addTasksToCalendar", settings.addTasksToCalendar)
                s.put("showCurrentTimeMarker", settings.showCurrentTimeMarker)
                s.put("arrivalHour", settings.arrivalHour)
                s.put("arrivalMinute", settings.arrivalMinute)
                s.put("departureHour", settings.departureHour)
                s.put("departureMinute", settings.departureMinute)
                s.put("unifyTaskPlanView", settings.unifyTaskPlanView)
                s.put("showWeekdayOnDates", settings.showWeekdayOnDates)
                s.put("enableTlsSync", settings.enableTlsSync)
                s.put("useAdvancedTimeSettingsUi", settings.useAdvancedTimeSettingsUi)
                s.put("lessonStartNotificationEnabled", settings.lessonStartNotificationEnabled)
                s.put("lessonStartNotificationMinutesBefore", settings.lessonStartNotificationMinutesBefore)
                s.put("lessonStartNotificationLiveUpdatesEnabled", settings.lessonStartNotificationLiveUpdatesEnabled)
                s.put("lessonStartNotificationProgressCountsDown", settings.lessonStartNotificationProgressCountsDown)
                s.put("lessonStartNotificationLiveUpdateEarlyMinutes", settings.lessonStartNotificationLiveUpdateEarlyMinutes)
                s.put("lessonStartNotificationChipMode", settings.lessonStartNotificationChipMode.name)
                s.put("syncLessonsToCalendar", settings.syncLessonsToCalendar)
                if (settings.lessonCalendarSyncStart != null) s.put("lessonCalendarSyncStart", settings.lessonCalendarSyncStart.toString())
                if (settings.lessonCalendarSyncEnd != null) s.put("lessonCalendarSyncEnd", settings.lessonCalendarSyncEnd.toString())
                s.put("enableAbTimetable", settings.enableAbTimetable)
                s.put("enableExamTimetable", settings.enableExamTimetable)
                s.put("examPeriodsPerDay", settings.examPeriodsPerDay)
                s.put("examPeriodDurationMin", settings.examPeriodDurationMin)
                s.put("examBreakBetweenPeriodsMin", settings.examBreakBetweenPeriodsMin)
                s.put("examLunchBreakMin", settings.examLunchBreakMin)
                s.put("examLunchAfterPeriod", settings.examLunchAfterPeriod)
                s.put("examFirstPeriodStartHour", settings.examFirstPeriodStartHour)
                s.put("examFirstPeriodStartMinute", settings.examFirstPeriodStartMinute)
                s.put("examArrivalHour", settings.examArrivalHour)
                s.put("examArrivalMinute", settings.examArrivalMinute)
            })
        }

        root.put("lessons", org.json.JSONArray().also { arr ->
            lessons.forEach { lesson ->
                arr.put(org.json.JSONObject().also { obj ->
                    obj.put("academicYear", lesson.academicYear)
                    obj.put("timetableTerm", lesson.timetableTerm.name)
                    obj.put("dayOfWeek", lesson.dayOfWeek)
                    obj.put("slotIndex", lesson.slotIndex)
                    obj.put("mode", lesson.mode.name)
                    obj.put("weeklySubject", lesson.weeklySubject)
                    obj.put("weeklyTeacher", lesson.weeklyTeacher)
                    if (lesson.weeklyLocation != null) obj.put("weeklyLocation", lesson.weeklyLocation)
                    obj.put("aSubject", lesson.aSubject)
                    obj.put("aTeacher", lesson.aTeacher)
                    if (lesson.aLocation != null) obj.put("aLocation", lesson.aLocation)
                    obj.put("bSubject", lesson.bSubject)
                    obj.put("bTeacher", lesson.bTeacher)
                    if (lesson.bLocation != null) obj.put("bLocation", lesson.bLocation)
                })
            }
        })

        root.put("longBreaks", org.json.JSONArray().also { arr ->
            longBreaks.forEach { lb ->
                arr.put(org.json.JSONObject().also { obj ->
                    obj.put("name", lb.name)
                    obj.put("startDate", lb.startDate.toString())
                    obj.put("endDate", lb.endDate.toString())
                })
            }
        })

        root.put("dayTypes", org.json.JSONArray().also { arr ->
            dayTypes.forEach { dt ->
                arr.put(org.json.JSONObject().also { obj ->
                    obj.put("date", dt.date.toString())
                    obj.put("dayType", dt.dayType.name)
                    if (dt.overrideLessonDayOfWeek != null) obj.put("overrideLessonDayOfWeek", dt.overrideLessonDayOfWeek)
                    if (dt.overrideLessonDayType != null) obj.put("overrideLessonDayType", dt.overrideLessonDayType.name)
                    if (dt.holidaySpecialLabel != null) obj.put("holidaySpecialLabel", dt.holidaySpecialLabel.name)
                })
            }
        })

        root.put("tasks", org.json.JSONArray().also { arr ->
            tasks.forEach { task ->
                arr.put(org.json.JSONObject().also { obj ->
                    if (task.lessonId != null) obj.put("lessonId", task.lessonId)
                    obj.put("subject", task.subject)
                    if (task.teacher != null) obj.put("teacher", task.teacher)
                    obj.put("title", task.title)
                    if (task.description != null) obj.put("description", task.description)
                    obj.put("dueDate", task.dueDate.toString())
                    obj.put("dueHour", task.dueHour)
                    obj.put("dueMinute", task.dueMinute)
                    obj.put("isCompleted", task.isCompleted)
                    if (task.completedDate != null) obj.put("completedDate", task.completedDate.toString())
                    obj.put("createdDate", task.createdDate.toString())
                    obj.put("priority", task.priority)
                    obj.put("useTeacherMatching", task.useTeacherMatching)
                    obj.put("reminderEnabled", task.reminderEnabled)
                    if (task.reminderDate != null) obj.put("reminderDate", task.reminderDate.toString())
                    obj.put("reminderHour", task.reminderHour)
                    obj.put("reminderMinute", task.reminderMinute)
                })
            }
        })

        root.put("plans", org.json.JSONArray().also { arr ->
            plans.forEach { plan ->
                arr.put(org.json.JSONObject().also { obj ->
                    if (plan.lessonId != null) obj.put("lessonId", plan.lessonId)
                    obj.put("subject", plan.subject)
                    if (plan.teacher != null) obj.put("teacher", plan.teacher)
                    obj.put("title", plan.title)
                    if (plan.description != null) obj.put("description", plan.description)
                    obj.put("dueDate", plan.dueDate.toString())
                    obj.put("dueHour", plan.dueHour)
                    obj.put("dueMinute", plan.dueMinute)
                    obj.put("isCompleted", plan.isCompleted)
                    if (plan.completedDate != null) obj.put("completedDate", plan.completedDate.toString())
                    obj.put("createdDate", plan.createdDate.toString())
                    obj.put("priority", plan.priority)
                    obj.put("useTeacherMatching", plan.useTeacherMatching)
                    obj.put("reminderEnabled", plan.reminderEnabled)
                    if (plan.reminderDate != null) obj.put("reminderDate", plan.reminderDate.toString())
                    obj.put("reminderHour", plan.reminderHour)
                    obj.put("reminderMinute", plan.reminderMinute)
                })
            }
        })

        root.put("cancelledLessons", org.json.JSONArray().also { arr ->
            cancelledLessons.forEach { cl ->
                arr.put(org.json.JSONObject().also { obj ->
                    obj.put("date", cl.date.toString())
                    obj.put("slotIndex", cl.slotIndex)
                    obj.put("createdAt", cl.createdAt)
                })
            }
        })

        root.put("changedLessons", org.json.JSONArray().also { arr ->
            changedLessons.forEach { changed ->
                arr.put(org.json.JSONObject().also { obj ->
                    obj.put("date", changed.date.toString())
                    obj.put("slotIndex", changed.slotIndex)
                    obj.put("subject", changed.subject)
                    obj.put("teacher", changed.teacher)
                    if (changed.location != null) obj.put("location", changed.location)
                    obj.put("createdAt", changed.createdAt)
                })
            }
        })

        root.put("lessonNotes", org.json.JSONArray().also { arr ->
            lessonNotes.forEach { note ->
                arr.put(org.json.JSONObject().also { obj ->
                    obj.put("date", note.date.toString())
                    obj.put("slotIndex", note.slotIndex)
                    obj.put("text", note.text)
                    obj.put("updatedAt", note.updatedAt)
                })
            }
        })

        root.put("examDaySchedules", org.json.JSONArray().also { arr ->
            examDaySchedules.forEach { schedule ->
                arr.put(org.json.JSONObject().also { obj ->
                    obj.put("date", schedule.date.toString())
                    obj.put("arrivalHour", schedule.arrivalHour)
                    obj.put("arrivalMinute", schedule.arrivalMinute)
                    obj.put("examName", schedule.examName)
                    obj.put("updatedAt", schedule.updatedAt)
                })
            }
        })

        root.put("examLessons", org.json.JSONArray().also { arr ->
            examLessons.forEach { lesson ->
                arr.put(examLessonToJson(lesson))
            }
        })

        root.put("lessonNotificationExclusions", org.json.JSONArray().also { arr ->
            lessonNotificationExclusions.forEach { exclusion ->
                arr.put(org.json.JSONObject().also { obj ->
                    obj.put("subject", exclusion.subject)
                    if (exclusion.teacher != null) obj.put("teacher", exclusion.teacher)
                    obj.put("matchTeacher", exclusion.matchTeacher)
                    obj.put("createdAt", exclusion.createdAt)
                })
            }
        })

        return root.toString(2)
    }

    suspend fun exportSyncPayload(): org.json.JSONObject {
        val lessons = dao.getLessonsOnce()
        val longBreaks = dao.getLongBreaksOnce()
        val dayTypes = dao.getDayTypesOnce()
        val tasks = dao.getTasksOnce()
        val plans = dao.getPlansOnce()
        val changedLessons = dao.getChangedLessonsOnce()
        val lessonNotes = dao.getLessonNotesOnce()
        val examDaySchedules = dao.getExamDaySchedulesOnce()
        val examLessons = dao.getExamLessonsOnce()
        val profile = dao.getSyncProfile()
        val now = System.currentTimeMillis()
        val datasetMetaByKey = dao.getAllSyncDatasetMeta().associateBy { it.datasetKey }

        val root = org.json.JSONObject().putCurrentSyncProtocolVersion()
        root.put("device", org.json.JSONObject().also { d ->
            d.put("deviceId", profile?.deviceId ?: "")
            d.put("deviceName", profile?.deviceName ?: "")
        })

        val meta = org.json.JSONObject()
        SYNC_DATASET_KEYS.forEach { key ->
            val datasetMeta = datasetMetaByKey[key]
            meta.put(key, org.json.JSONObject().also { m ->
                m.put("updatedAt", datasetMeta?.lastUpdatedAt?.takeIf { it > 0L } ?: now)
                m.put("updatedByDeviceId", datasetMeta?.lastUpdatedByDeviceId ?: (profile?.deviceId ?: ""))
            })
        }
        root.put("metadata", meta)

        root.put(DATASET_LESSONS, org.json.JSONArray().also { arr ->
            lessons.forEach { lesson ->
                arr.put(org.json.JSONObject().also { obj ->
                    obj.put("academicYear", lesson.academicYear)
                    obj.put("timetableTerm", lesson.timetableTerm.name)
                    obj.put("dayOfWeek", lesson.dayOfWeek)
                    obj.put("slotIndex", lesson.slotIndex)
                    obj.put("mode", lesson.mode.name)
                    obj.put("weeklySubject", lesson.weeklySubject)
                    obj.put("weeklyTeacher", lesson.weeklyTeacher)
                    if (lesson.weeklyLocation != null) obj.put("weeklyLocation", lesson.weeklyLocation)
                    obj.put("aSubject", lesson.aSubject)
                    obj.put("aTeacher", lesson.aTeacher)
                    if (lesson.aLocation != null) obj.put("aLocation", lesson.aLocation)
                    obj.put("bSubject", lesson.bSubject)
                    obj.put("bTeacher", lesson.bTeacher)
                    if (lesson.bLocation != null) obj.put("bLocation", lesson.bLocation)
                })
            }
        })

        root.put(DATASET_LONG_BREAKS, org.json.JSONArray().also { arr ->
            longBreaks.forEach { lb ->
                arr.put(org.json.JSONObject().also { obj ->
                    obj.put("name", lb.name)
                    obj.put("startDate", lb.startDate.toString())
                    obj.put("endDate", lb.endDate.toString())
                })
            }
        })

        root.put(DATASET_DAY_TYPES, org.json.JSONArray().also { arr ->
            dayTypes.forEach { dt ->
                arr.put(org.json.JSONObject().also { obj ->
                    obj.put("date", dt.date.toString())
                    obj.put("dayType", dt.dayType.name)
                    if (dt.overrideLessonDayOfWeek != null) obj.put("overrideLessonDayOfWeek", dt.overrideLessonDayOfWeek)
                    if (dt.overrideLessonDayType != null) obj.put("overrideLessonDayType", dt.overrideLessonDayType.name)
                    if (dt.holidaySpecialLabel != null) obj.put("holidaySpecialLabel", dt.holidaySpecialLabel.name)
                })
            }
        })

        root.put(DATASET_TASKS, org.json.JSONArray().also { arr ->
            tasks.forEach { task ->
                arr.put(org.json.JSONObject().also { obj ->
                    if (task.lessonId != null) obj.put("lessonId", task.lessonId)
                    obj.put("subject", task.subject)
                    if (task.teacher != null) obj.put("teacher", task.teacher)
                    obj.put("title", task.title)
                    if (task.description != null) obj.put("description", task.description)
                    obj.put("dueDate", task.dueDate.toString())
                    obj.put("dueHour", task.dueHour)
                    obj.put("dueMinute", task.dueMinute)
                    obj.put("isCompleted", task.isCompleted)
                    if (task.completedDate != null) obj.put("completedDate", task.completedDate.toString())
                    obj.put("createdDate", task.createdDate.toString())
                    obj.put("priority", task.priority)
                    obj.put("useTeacherMatching", task.useTeacherMatching)
                    obj.put("reminderEnabled", task.reminderEnabled)
                    if (task.reminderDate != null) obj.put("reminderDate", task.reminderDate.toString())
                    obj.put("reminderHour", task.reminderHour)
                    obj.put("reminderMinute", task.reminderMinute)
                })
            }
        })

        root.put(DATASET_PLANS, org.json.JSONArray().also { arr ->
            plans.forEach { plan ->
                arr.put(org.json.JSONObject().also { obj ->
                    if (plan.lessonId != null) obj.put("lessonId", plan.lessonId)
                    obj.put("subject", plan.subject)
                    if (plan.teacher != null) obj.put("teacher", plan.teacher)
                    obj.put("title", plan.title)
                    if (plan.description != null) obj.put("description", plan.description)
                    obj.put("dueDate", plan.dueDate.toString())
                    obj.put("dueHour", plan.dueHour)
                    obj.put("dueMinute", plan.dueMinute)
                    obj.put("isCompleted", plan.isCompleted)
                    if (plan.completedDate != null) obj.put("completedDate", plan.completedDate.toString())
                    obj.put("createdDate", plan.createdDate.toString())
                    obj.put("priority", plan.priority)
                    obj.put("useTeacherMatching", plan.useTeacherMatching)
                    obj.put("reminderEnabled", plan.reminderEnabled)
                    if (plan.reminderDate != null) obj.put("reminderDate", plan.reminderDate.toString())
                    obj.put("reminderHour", plan.reminderHour)
                    obj.put("reminderMinute", plan.reminderMinute)
                })
            }
        })

        val cancelledLessons = dao.getCancelledLessonsOnce()
        root.put(DATASET_CANCELLED_LESSONS, org.json.JSONArray().also { arr ->
            cancelledLessons.forEach { cl ->
                arr.put(org.json.JSONObject().also { obj ->
                    obj.put("date", cl.date.toString())
                    obj.put("slotIndex", cl.slotIndex)
                    obj.put("createdAt", cl.createdAt)
                })
            }
        })

        root.put(DATASET_CHANGED_LESSONS, org.json.JSONArray().also { arr ->
            changedLessons.forEach { changed ->
                arr.put(org.json.JSONObject().also { obj ->
                    obj.put("date", changed.date.toString())
                    obj.put("slotIndex", changed.slotIndex)
                    obj.put("subject", changed.subject)
                    obj.put("teacher", changed.teacher)
                    if (changed.location != null) obj.put("location", changed.location)
                    obj.put("createdAt", changed.createdAt)
                })
            }
        })

        root.put(DATASET_LESSON_NOTES, org.json.JSONArray().also { arr ->
            lessonNotes.forEach { note ->
                arr.put(org.json.JSONObject().also { obj ->
                    obj.put("date", note.date.toString())
                    obj.put("slotIndex", note.slotIndex)
                    obj.put("text", note.text)
                    obj.put("updatedAt", note.updatedAt)
                })
            }
        })

        root.put(DATASET_EXAM_TIMETABLES, org.json.JSONObject().also { exam ->
            exam.put("days", org.json.JSONArray().also { arr ->
                examDaySchedules.forEach { schedule ->
                    arr.put(org.json.JSONObject().also { obj ->
                        obj.put("date", schedule.date.toString())
                        obj.put("arrivalHour", schedule.arrivalHour)
                        obj.put("arrivalMinute", schedule.arrivalMinute)
                        obj.put("examName", schedule.examName)
                        obj.put("updatedAt", schedule.updatedAt)
                    })
                }
            })
            exam.put("lessons", org.json.JSONArray().also { arr ->
                examLessons.forEach { lesson -> arr.put(examLessonToJson(lesson)) }
            })
        })

        return root
    }

    suspend fun applySyncPayload(payload: org.json.JSONObject) {
        requireCurrentSyncProtocol(payload)
        val touchedDatasets = mutableSetOf<String>()

        payload.optJSONArray(DATASET_LESSONS)?.let { arr ->
            touchedDatasets += DATASET_LESSONS
            dao.deleteAllLessons()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val academicYear = obj.optInt("academicYear").takeIf { it > 0 } ?: continue
                dao.upsertLesson(LessonEntity(
                    academicYear = academicYear,
                    timetableTerm = runCatching {
                        TimetableTerm.valueOf(obj.optString("timetableTerm", TimetableTerm.FIRST.name))
                    }.getOrDefault(TimetableTerm.FIRST),
                    dayOfWeek = obj.optInt("dayOfWeek"),
                    slotIndex = obj.optInt("slotIndex"),
                    mode = runCatching { LessonMode.valueOf(obj.optString("mode", "WEEKLY")) }.getOrElse { LessonMode.WEEKLY },
                    weeklySubject = obj.optString("weeklySubject", ""),
                    weeklyTeacher = obj.optString("weeklyTeacher", ""),
                    weeklyLocation = obj.optString("weeklyLocation", "").takeIf { it.isNotBlank() },
                    aSubject = obj.optString("aSubject", ""),
                    aTeacher = obj.optString("aTeacher", ""),
                    aLocation = obj.optString("aLocation", "").takeIf { it.isNotBlank() },
                    bSubject = obj.optString("bSubject", ""),
                    bTeacher = obj.optString("bTeacher", ""),
                    bLocation = obj.optString("bLocation", "").takeIf { it.isNotBlank() }
                ))
            }
        }

        payload.optJSONArray(DATASET_LONG_BREAKS)?.let { arr ->
            touchedDatasets += DATASET_LONG_BREAKS
            dao.deleteAllLongBreaks()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val startDate = runCatching { java.time.LocalDate.parse(obj.optString("startDate")) }.getOrNull() ?: continue
                val endDate = runCatching { java.time.LocalDate.parse(obj.optString("endDate")) }.getOrNull() ?: continue
                dao.upsertLongBreak(LongBreakEntity(name = obj.optString("name", ""), startDate = startDate, endDate = endDate))
            }
        }

        payload.optJSONArray(DATASET_DAY_TYPES)?.let { arr ->
            touchedDatasets += DATASET_DAY_TYPES
            dao.deleteAllDayTypes()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val date = runCatching { java.time.LocalDate.parse(obj.optString("date")) }.getOrNull() ?: continue
                val dayType = runCatching { DayType.valueOf(obj.optString("dayType", "A")) }.getOrElse { DayType.A }
                val overrideDow = if (obj.has("overrideLessonDayOfWeek")) obj.optInt("overrideLessonDayOfWeek") else null
                val overrideDt = if (obj.has("overrideLessonDayType")) runCatching { DayType.valueOf(obj.optString("overrideLessonDayType")) }.getOrNull() else null
                val holidaySpecialLabel = if (obj.has("holidaySpecialLabel")) runCatching {
                    HolidaySpecialLabel.valueOf(obj.optString("holidaySpecialLabel"))
                }.getOrNull() else null
                dao.upsertDayType(
                    DayTypeEntity(
                        date = date,
                        dayType = dayType,
                        overrideLessonDayOfWeek = overrideDow,
                        overrideLessonDayType = overrideDt,
                        holidaySpecialLabel = if (dayType == DayType.HOLIDAY) holidaySpecialLabel else null
                    )
                )
            }
        }

        payload.optJSONArray(DATASET_TASKS)?.let { arr ->
            touchedDatasets += DATASET_TASKS
            dao.deleteAllTasks()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val dueDate = runCatching { java.time.LocalDate.parse(obj.optString("dueDate")) }.getOrNull() ?: continue
                val createdDate = runCatching { java.time.LocalDate.parse(obj.optString("createdDate")) }.getOrElse { LocalDate.now() }
                val completedDate = if (obj.has("completedDate")) runCatching { java.time.LocalDate.parse(obj.optString("completedDate")) }.getOrNull() else null
                val reminderDate = if (obj.has("reminderDate")) runCatching { java.time.LocalDate.parse(obj.optString("reminderDate")) }.getOrNull() else null
                dao.upsertTask(TaskEntity(
                    lessonId = if (obj.has("lessonId")) obj.optLong("lessonId") else null,
                    subject = obj.optString("subject", ""),
                    teacher = obj.optString("teacher", "").takeIf { it.isNotBlank() },
                    title = obj.optString("title", ""),
                    description = obj.optString("description", "").takeIf { it.isNotBlank() },
                    dueDate = dueDate,
                    dueHour = obj.optInt("dueHour", 23),
                    dueMinute = obj.optInt("dueMinute", 59),
                    isCompleted = obj.optBoolean("isCompleted", false),
                    completedDate = completedDate,
                    createdDate = createdDate,
                    updatedAt = System.currentTimeMillis(),
                    priority = obj.optInt("priority", 0),
                    useTeacherMatching = obj.optBoolean("useTeacherMatching", false),
                    reminderEnabled = obj.optBoolean("reminderEnabled", false),
                    reminderDate = reminderDate,
                    reminderHour = obj.optInt("reminderHour", 20),
                    reminderMinute = obj.optInt("reminderMinute", 0),
                    reminderCalendarEventId = null
                ))
            }
        }

        payload.optJSONArray(DATASET_PLANS)?.let { arr ->
            touchedDatasets += DATASET_PLANS
            dao.deleteAllPlans()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val dueDate = runCatching { java.time.LocalDate.parse(obj.optString("dueDate")) }.getOrNull() ?: continue
                val createdDate = runCatching { java.time.LocalDate.parse(obj.optString("createdDate")) }.getOrElse { LocalDate.now() }
                val completedDate = if (obj.has("completedDate")) runCatching { java.time.LocalDate.parse(obj.optString("completedDate")) }.getOrNull() else null
                val reminderDate = if (obj.has("reminderDate")) runCatching { java.time.LocalDate.parse(obj.optString("reminderDate")) }.getOrNull() else null
                dao.upsertPlan(PlanEntity(
                    lessonId = if (obj.has("lessonId")) obj.optLong("lessonId") else null,
                    subject = obj.optString("subject", ""),
                    teacher = obj.optString("teacher", "").takeIf { it.isNotBlank() },
                    title = obj.optString("title", ""),
                    description = obj.optString("description", "").takeIf { it.isNotBlank() },
                    dueDate = dueDate,
                    dueHour = obj.optInt("dueHour", 23),
                    dueMinute = obj.optInt("dueMinute", 59),
                    isCompleted = obj.optBoolean("isCompleted", false),
                    completedDate = completedDate,
                    createdDate = createdDate,
                    updatedAt = System.currentTimeMillis(),
                    priority = obj.optInt("priority", 0),
                    useTeacherMatching = obj.optBoolean("useTeacherMatching", false),
                    reminderEnabled = obj.optBoolean("reminderEnabled", false),
                    reminderDate = reminderDate,
                    reminderHour = obj.optInt("reminderHour", 20),
                    reminderMinute = obj.optInt("reminderMinute", 0),
                    reminderCalendarEventId = null
                ))
            }
        }

        payload.optJSONArray(DATASET_CANCELLED_LESSONS)?.let { arr ->
            touchedDatasets += DATASET_CANCELLED_LESSONS
            dao.deleteAllCancelledLessons()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val date = runCatching { java.time.LocalDate.parse(obj.optString("date")) }.getOrNull() ?: continue
                val slotIndex = obj.optInt("slotIndex", -1)
                if (slotIndex < 0) continue
                dao.upsertCancelledLesson(CancelledLessonEntity(
                    date = date,
                    slotIndex = slotIndex,
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                ))
            }
        }

        payload.optJSONArray(DATASET_CHANGED_LESSONS)?.let { arr ->
            touchedDatasets += DATASET_CHANGED_LESSONS
            dao.deleteAllChangedLessons()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val date = runCatching { java.time.LocalDate.parse(obj.optString("date")) }.getOrNull() ?: continue
                val slotIndex = obj.optInt("slotIndex", -1)
                val subject = obj.optString("subject", "").trim()
                if (slotIndex < 0 || subject.isBlank()) continue
                dao.upsertChangedLesson(
                    ChangedLessonEntity(
                        date = date,
                        slotIndex = slotIndex,
                        subject = subject,
                        teacher = obj.optString("teacher", "").trim(),
                        location = obj.optString("location", "").takeIf { it.isNotBlank() },
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        payload.optJSONArray(DATASET_LESSON_NOTES)?.let { arr ->
            touchedDatasets += DATASET_LESSON_NOTES
            dao.deleteAllLessonNotes()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val date = runCatching { java.time.LocalDate.parse(obj.optString("date")) }.getOrNull() ?: continue
                val slotIndex = obj.optInt("slotIndex", -1)
                val text = obj.optString("text", "").trim()
                if (slotIndex < 0 || text.isBlank()) continue
                dao.upsertLessonNote(
                    LessonNoteEntity(
                        date = date,
                        slotIndex = slotIndex,
                        text = text,
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }

        payload.optJSONObject(DATASET_EXAM_TIMETABLES)?.let { exam ->
            touchedDatasets += DATASET_EXAM_TIMETABLES
            dao.deleteAllExamLessons()
            dao.deleteAllExamDaySchedules()
            exam.optJSONArray("days")?.let { days ->
                for (i in 0 until days.length()) {
                    val obj = days.optJSONObject(i) ?: continue
                    val date = runCatching { LocalDate.parse(obj.optString("date")) }.getOrNull() ?: continue
                    dao.upsertExamDaySchedule(
                        ExamDayScheduleEntity(
                            date = date,
                            arrivalHour = obj.optInt("arrivalHour", 8).coerceIn(0, 23),
                            arrivalMinute = obj.optInt("arrivalMinute", 30).coerceIn(0, 59),
                            examName = obj.optString("examName", "").trim(),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            }
            val importedLessons = mutableListOf<ExamLessonEntity>()
            exam.optJSONArray("lessons")?.let { lessons ->
                for (i in 0 until lessons.length()) {
                    examLessonFromJson(lessons.optJSONObject(i))?.let(importedLessons::add)
                }
            }
            if (importedLessons.isNotEmpty()) {
                dao.upsertExamLessons(importedLessons)
            }
        }

        val now = System.currentTimeMillis()
        val metadata = payload.optJSONObject("metadata")
        if (metadata != null && touchedDatasets.isNotEmpty()) {
            dao.upsertSyncDatasetMetaList(
                touchedDatasets.map { key ->
                    val entry = metadata.optJSONObject(key)
                    val incomingUpdatedAt = entry?.optLong("updatedAt", now) ?: now
                    SyncDatasetMetaEntity(
                        datasetKey = key,
                        lastUpdatedAt = repository.clampFutureMetaTimestamp(incomingUpdatedAt, now),
                        lastUpdatedByDeviceId = entry?.optString("updatedByDeviceId", "") ?: ""
                    )
                }
            )
        } else if (touchedDatasets.isNotEmpty()) {
            repository.touchSyncDatasetMeta(*touchedDatasets.toTypedArray())
        }
    }

    suspend fun importAllData(json: String, requireSettings: Boolean = false) {
        val root = org.json.JSONObject(json)

        val importVersion = if (root.has("version") && !root.isNull("version")) {
            root.optInt("version", -1)
        } else {
            1
        }

        when {
            importVersion == -1 -> throw IllegalArgumentException("versionフィールドの形式が不正です")
            importVersion < MIN_SUPPORTED_IMPORT_VERSION -> throw IllegalArgumentException("古すぎるJSON形式のためインポートできません")
            importVersion > CURRENT_EXPORT_VERSION -> throw IllegalArgumentException("このアプリでは新しすぎるJSON形式です")
        }

        val normalizedRoot = normalizeImportRoot(root, importVersion)
        require(!requireSettings || normalizedRoot.optJSONObject("settings") != null)

        val settingsEntity = normalizedRoot.optJSONObject("settings")?.let { s ->
            val termStart = LocalDate.parse(s.getString("termStart"))
            SettingsEntity(
                id = 1,
                termStart = termStart,
                termEnd = LocalDate.parse(s.getString("termEnd")),
                activeAcademicYear = s.optInt("activeAcademicYear", 0)
                    .takeIf { it > 0 } ?: academicYearForDate(termStart),
                enableLocalAi = s.optBoolean("enableLocalAi", false),
                enableNaturalLanguageTaskAdd =
                    InternalFeatureFlags.NATURAL_LANGUAGE_TASK_ADD &&
                        s.optBoolean("enableNaturalLanguageTaskAdd", false),
                enableLessonNotes = true,
                hfToken = if (s.has("hfToken") && !s.isNull("hfToken")) s.getString("hfToken") else null,
                periodsPerDay = s.optInt("periodsPerDay", 4),
                periodDurationMin = s.optInt("periodDurationMin", 90),
                breakBetweenPeriodsMin = s.optInt("breakBetweenPeriodsMin", 10),
                lunchBreakMin = s.optInt("lunchBreakMin", 60),
                lunchAfterPeriod = s.optInt("lunchAfterPeriod", 2),
                firstPeriodStartHour = s.optInt("firstPeriodStartHour", 8),
                firstPeriodStartMinute = s.optInt("firstPeriodStartMinute", 40),
                useKosenMode = s.optBoolean("useKosenMode", true),
                periodLabelStyle = runCatching {
                    PeriodLabelStyle.valueOf(s.getString("periodLabelStyle"))
                }.getOrElse {
                    if (s.optBoolean("useKosenMode", true)) {
                        PeriodLabelStyle.PAIR_KOSHI
                    } else {
                        PeriodLabelStyle.SINGLE_KOSHI
                    }
                },
                enableSemesterTimetables = s.optBoolean("enableSemesterTimetables", true),
                useDrawerNavigation = s.optBoolean("useDrawerNavigation", false),
                addTasksToCalendar = s.optBoolean("addTasksToCalendar", false),
                showCurrentTimeMarker = s.optBoolean("showCurrentTimeMarker", false),
                arrivalHour = s.optInt("arrivalHour", 8),
                arrivalMinute = s.optInt("arrivalMinute", 30),
                departureHour = s.optInt("departureHour", -1),
                departureMinute = s.optInt("departureMinute", -1),
                unifyTaskPlanView = s.optBoolean("unifyTaskPlanView", false),
                showWeekdayOnDates = s.optBoolean("showWeekdayOnDates", false),
                enableTlsSync = s.optBoolean("enableTlsSync", false),
                useAdvancedTimeSettingsUi = s.optBoolean("useAdvancedTimeSettingsUi", false),
                lessonStartNotificationEnabled = s.optBoolean("lessonStartNotificationEnabled", false),
                lessonStartNotificationMinutesBefore = s.optInt("lessonStartNotificationMinutesBefore", 10),
                lessonStartNotificationLiveUpdatesEnabled = s.optBoolean("lessonStartNotificationLiveUpdatesEnabled", true),
                lessonStartNotificationProgressCountsDown = s.optBoolean("lessonStartNotificationProgressCountsDown", false),
                lessonStartNotificationLiveUpdateEarlyMinutes = s.optInt("lessonStartNotificationLiveUpdateEarlyMinutes", 1).coerceIn(0, 5),
                lessonStartNotificationChipMode = runCatching {
                    LessonStartNotificationChipMode.valueOf(
                        s.optString(
                            "lessonStartNotificationChipMode",
                            LessonStartNotificationChipMode.MINUTE_TEXT.name
                        )
                    )
                }.getOrDefault(LessonStartNotificationChipMode.MINUTE_TEXT),
                syncLessonsToCalendar = s.optBoolean("syncLessonsToCalendar", false),
                lessonCalendarSyncStart = s.optString("lessonCalendarSyncStart", "").takeIf { it.isNotBlank() }?.let(LocalDate::parse),
                lessonCalendarSyncEnd = s.optString("lessonCalendarSyncEnd", "").takeIf { it.isNotBlank() }?.let(LocalDate::parse),
                enableAbTimetable = s.optBoolean("enableAbTimetable", true),
                initialSetupCompleted = true,
                enableExamTimetable = importedExamTimetableEnabled(importVersion, s.optBoolean("enableExamTimetable", true)),
                examPeriodsPerDay = s.optInt("examPeriodsPerDay", 4).coerceIn(1, 12),
                examPeriodDurationMin = s.optInt("examPeriodDurationMin", 50).coerceIn(10, 180),
                examBreakBetweenPeriodsMin = s.optInt("examBreakBetweenPeriodsMin", 20).coerceIn(0, 120),
                examLunchBreakMin = s.optInt("examLunchBreakMin", 50).coerceIn(0, 180),
                examLunchAfterPeriod = s.optInt("examLunchAfterPeriod", 3).coerceIn(
                    0,
                    s.optInt("examPeriodsPerDay", 4).coerceIn(1, 12)
                ),
                examFirstPeriodStartHour = s.optInt("examFirstPeriodStartHour", 8).coerceIn(0, 23),
                examFirstPeriodStartMinute = s.optInt("examFirstPeriodStartMinute", 50).coerceIn(0, 59),
                examArrivalHour = s.optInt("examArrivalHour", 8).coerceIn(0, 23),
                examArrivalMinute = s.optInt("examArrivalMinute", 30).coerceIn(0, 59)
            )
        }

        val importedAcademicYear = settingsEntity?.activeAcademicYear
            ?: academicYearForDate(LocalDate.now())
        val lessonEntities = mutableListOf<LessonEntity>()
        normalizedRoot.optJSONArray("lessons")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val dayOfWeek = obj.getInt("dayOfWeek")
                val slotIndex = obj.getInt("slotIndex")
                lessonEntities += LessonEntity(
                    id = 0,
                    academicYear = obj.optInt("academicYear", importedAcademicYear)
                        .takeIf { it > 0 } ?: importedAcademicYear,
                    timetableTerm = runCatching {
                        TimetableTerm.valueOf(
                            obj.optString("timetableTerm", TimetableTerm.FIRST.name)
                        )
                    }.getOrDefault(TimetableTerm.FIRST),
                    dayOfWeek = dayOfWeek,
                    slotIndex = slotIndex,
                    mode = try {
                        LessonMode.valueOf(obj.optString("mode", "WEEKLY"))
                    } catch (_: Exception) { LessonMode.WEEKLY },
                    weeklySubject = obj.optString("weeklySubject", ""),
                    weeklyTeacher = obj.optString("weeklyTeacher", ""),
                    weeklyLocation = obj.optString("weeklyLocation", "").takeIf { it.isNotEmpty() },
                    aSubject = obj.optString("aSubject", ""),
                    aTeacher = obj.optString("aTeacher", ""),
                    aLocation = obj.optString("aLocation", "").takeIf { it.isNotEmpty() },
                    bSubject = obj.optString("bSubject", ""),
                    bTeacher = obj.optString("bTeacher", ""),
                    bLocation = obj.optString("bLocation", "").takeIf { it.isNotEmpty() }
                )
            }
        }

        val longBreakEntities = mutableListOf<LongBreakEntity>()
        normalizedRoot.optJSONArray("longBreaks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                longBreakEntities += LongBreakEntity(
                    id = 0,
                    name = obj.getString("name"),
                    startDate = LocalDate.parse(obj.getString("startDate")),
                    endDate = LocalDate.parse(obj.getString("endDate"))
                )
            }
        }

        val dayTypeEntities = mutableListOf<DayTypeEntity>()
        normalizedRoot.optJSONArray("dayTypes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                try {
                    dayTypeEntities += DayTypeEntity(
                        date = LocalDate.parse(obj.getString("date")),
                        dayType = DayType.valueOf(obj.getString("dayType")),
                        overrideLessonDayOfWeek = obj.optInt("overrideLessonDayOfWeek", -1).takeIf { it in 1..5 },
                        overrideLessonDayType = obj.optString("overrideLessonDayType", "")
                            .takeIf { it.isNotBlank() }
                            ?.let { DayType.valueOf(it) },
                        holidaySpecialLabel = obj.optString("holidaySpecialLabel", "")
                            .takeIf { it.isNotBlank() }
                            ?.let { HolidaySpecialLabel.valueOf(it) }
                    )
                } catch (_: Exception) { }
            }
        }

        val taskEntities = mutableListOf<TaskEntity>()
        normalizedRoot.optJSONArray("tasks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                try {
                    taskEntities += TaskEntity(
                        id = 0,
                        lessonId = if (obj.has("lessonId") && !obj.isNull("lessonId")) obj.getLong("lessonId") else null,
                        subject = obj.optString("subject", ""),
                        teacher = if (obj.has("teacher") && !obj.isNull("teacher")) obj.getString("teacher") else null,
                        title = obj.optString("title", ""),
                        description = if (obj.has("description") && !obj.isNull("description")) obj.getString("description") else null,
                        dueDate = LocalDate.parse(obj.getString("dueDate")),
                        dueHour = obj.optInt("dueHour", 23),
                        dueMinute = obj.optInt("dueMinute", 59),
                        isCompleted = obj.optBoolean("isCompleted", false),
                        completedDate = if (obj.has("completedDate") && !obj.isNull("completedDate")) LocalDate.parse(obj.getString("completedDate")) else null,
                        createdDate = LocalDate.parse(obj.getString("createdDate")),
                        priority = obj.optInt("priority", 0),
                        useTeacherMatching = obj.optBoolean("useTeacherMatching", false),
                        calendarEventId = null,
                        reminderEnabled = obj.optBoolean("reminderEnabled", false),
                        reminderDate = if (obj.has("reminderDate") && !obj.isNull("reminderDate")) LocalDate.parse(obj.getString("reminderDate")) else null,
                        reminderHour = obj.optInt("reminderHour", 20),
                        reminderMinute = obj.optInt("reminderMinute", 0),
                        reminderCalendarEventId = null
                    )
                } catch (_: Exception) { }
            }
        }

        val planEntities = mutableListOf<PlanEntity>()
        normalizedRoot.optJSONArray("plans")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                try {
                    planEntities += PlanEntity(
                        id = 0,
                        lessonId = if (obj.has("lessonId") && !obj.isNull("lessonId")) obj.getLong("lessonId") else null,
                        subject = obj.optString("subject", ""),
                        teacher = if (obj.has("teacher") && !obj.isNull("teacher")) obj.getString("teacher") else null,
                        title = obj.optString("title", ""),
                        description = if (obj.has("description") && !obj.isNull("description")) obj.getString("description") else null,
                        dueDate = LocalDate.parse(obj.getString("dueDate")),
                        dueHour = obj.optInt("dueHour", 23),
                        dueMinute = obj.optInt("dueMinute", 59),
                        isCompleted = obj.optBoolean("isCompleted", false),
                        completedDate = if (obj.has("completedDate") && !obj.isNull("completedDate")) LocalDate.parse(obj.getString("completedDate")) else null,
                        createdDate = LocalDate.parse(obj.getString("createdDate")),
                        priority = obj.optInt("priority", 0),
                        useTeacherMatching = obj.optBoolean("useTeacherMatching", false),
                        calendarEventId = null,
                        reminderEnabled = obj.optBoolean("reminderEnabled", false),
                        reminderDate = if (obj.has("reminderDate") && !obj.isNull("reminderDate")) LocalDate.parse(obj.getString("reminderDate")) else null,
                        reminderHour = obj.optInt("reminderHour", 20),
                        reminderMinute = obj.optInt("reminderMinute", 0),
                        reminderCalendarEventId = null
                    )
                } catch (_: Exception) { }
            }
        }

        val cancelledLessonEntities = mutableListOf<CancelledLessonEntity>()
        normalizedRoot.optJSONArray("cancelledLessons")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                try {
                    val date = LocalDate.parse(obj.getString("date"))
                    val slotIndex = obj.getInt("slotIndex")
                    if (slotIndex < 0) continue
                    cancelledLessonEntities += CancelledLessonEntity(
                        date = date,
                        slotIndex = slotIndex,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                } catch (_: Exception) { }
            }
        }

        val changedLessonEntities = mutableListOf<ChangedLessonEntity>()
        normalizedRoot.optJSONArray("changedLessons")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                try {
                    val date = LocalDate.parse(obj.getString("date"))
                    val slotIndex = obj.getInt("slotIndex")
                    val subject = obj.optString("subject", "").trim()
                    if (slotIndex < 0 || subject.isBlank()) continue
                    changedLessonEntities += ChangedLessonEntity(
                        date = date,
                        slotIndex = slotIndex,
                        subject = subject,
                        teacher = obj.optString("teacher", "").trim(),
                        location = obj.optString("location", "").takeIf { it.isNotEmpty() },
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                } catch (_: Exception) { }
            }
        }

        val lessonNoteEntities = mutableListOf<LessonNoteEntity>()
        normalizedRoot.optJSONArray("lessonNotes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                try {
                    val date = LocalDate.parse(obj.getString("date"))
                    val slotIndex = obj.getInt("slotIndex")
                    val text = obj.optString("text", "").trim()
                    if (slotIndex < 0 || text.isBlank()) continue
                    lessonNoteEntities += LessonNoteEntity(
                        date = date,
                        slotIndex = slotIndex,
                        text = text,
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                } catch (_: Exception) { }
            }
        }

        val examDayScheduleEntities = mutableListOf<ExamDayScheduleEntity>()
        normalizedRoot.optJSONArray("examDaySchedules")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val date = runCatching { LocalDate.parse(obj.optString("date")) }.getOrNull() ?: continue
                examDayScheduleEntities += ExamDayScheduleEntity(
                    date = date,
                    arrivalHour = obj.optInt("arrivalHour", 8).coerceIn(0, 23),
                    arrivalMinute = obj.optInt("arrivalMinute", 30).coerceIn(0, 59),
                    examName = obj.optString("examName", "").trim(),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            }
        }

        val examLessonEntities = mutableListOf<ExamLessonEntity>()
        normalizedRoot.optJSONArray("examLessons")?.let { arr ->
            for (i in 0 until arr.length()) {
                examLessonFromJson(arr.optJSONObject(i))?.let(examLessonEntities::add)
            }
        }

        val lessonNotificationExclusionEntities = mutableListOf<LessonNotificationExclusionEntity>()
        normalizedRoot.optJSONArray("lessonNotificationExclusions")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val subject = obj.optString("subject", "").trim()
                if (subject.isBlank()) continue
                lessonNotificationExclusionEntities += LessonNotificationExclusionEntity(
                    subject = subject,
                    teacher = obj.optString("teacher", "").trim().takeIf { it.isNotBlank() },
                    matchTeacher = obj.optBoolean("matchTeacher", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            }
        }

        db.withTransaction {
            settingsEntity?.let { dao.upsertSettings(it) }

            dao.deleteAllLessons()
            if (lessonEntities.isNotEmpty()) {
                lessonEntities.forEach { dao.upsertLesson(it) }
            }
            repository.ensureLessonRows()

            dao.deleteAllLongBreaks()
            if (longBreakEntities.isNotEmpty()) {
                longBreakEntities.forEach { dao.upsertLongBreak(it) }
            }

            dao.deleteAllDayTypes()
            if (dayTypeEntities.isNotEmpty()) dao.upsertDayTypes(dayTypeEntities)

            dao.deleteAllTasks()
            if (taskEntities.isNotEmpty()) dao.upsertTasks(taskEntities)

            dao.deleteAllPlans()
            if (planEntities.isNotEmpty()) dao.upsertPlans(planEntities)

            dao.deleteAllCancelledLessons()
            if (cancelledLessonEntities.isNotEmpty()) {
                cancelledLessonEntities.forEach { dao.upsertCancelledLesson(it) }
            }

            dao.deleteAllChangedLessons()
            if (changedLessonEntities.isNotEmpty()) {
                changedLessonEntities.forEach { dao.upsertChangedLesson(it) }
            }

            dao.deleteAllLessonNotes()
            if (lessonNoteEntities.isNotEmpty()) {
                lessonNoteEntities.forEach { dao.upsertLessonNote(it) }
            }

            dao.deleteAllExamLessons()
            dao.deleteAllExamDaySchedules()
            if (examDayScheduleEntities.isNotEmpty()) {
                examDayScheduleEntities.forEach { dao.upsertExamDaySchedule(it) }
            }
            if (examLessonEntities.isNotEmpty()) {
                dao.upsertExamLessons(examLessonEntities)
            }

            dao.deleteAllLessonNotificationExclusions()
            if (lessonNotificationExclusionEntities.isNotEmpty()) {
                lessonNotificationExclusionEntities.forEach { dao.upsertLessonNotificationExclusion(it) }
            }

            repository.syncDayTypes()
            repository.touchSyncDatasetMeta(
                DATASET_LESSONS,
                DATASET_LONG_BREAKS,
                DATASET_DAY_TYPES,
                DATASET_TASKS,
                DATASET_PLANS,
                DATASET_CANCELLED_LESSONS,
                DATASET_CHANGED_LESSONS,
                DATASET_LESSON_NOTES,
                DATASET_EXAM_TIMETABLES
            )
            repository.refreshAcademicYear()
        }
    }

    private fun examLessonToJson(lesson: ExamLessonEntity): org.json.JSONObject {
        return org.json.JSONObject().also { obj ->
            obj.put("date", lesson.date.toString())
            obj.put("slotIndex", lesson.slotIndex)
            obj.put("startHour", lesson.startHour)
            obj.put("startMinute", lesson.startMinute)
            obj.put("endHour", lesson.endHour)
            obj.put("endMinute", lesson.endMinute)
            obj.put("subject", lesson.subject)
            obj.put("teacher", lesson.teacher)
            obj.put("location", lesson.location)
            obj.put("memo", lesson.memo)
            obj.put("updatedAt", lesson.updatedAt)
        }
    }

    private fun examLessonFromJson(obj: org.json.JSONObject?): ExamLessonEntity? {
        obj ?: return null
        val date = runCatching { LocalDate.parse(obj.optString("date")) }.getOrNull() ?: return null
        val slotIndex = obj.optInt("slotIndex", -1)
        if (slotIndex !in 0..11) return null
        val startHour = obj.optInt("startHour", 8).coerceIn(0, 23)
        val startMinute = obj.optInt("startMinute", 50).coerceIn(0, 59)
        val endHour = obj.optInt("endHour", 9).coerceIn(0, 23)
        val endMinute = obj.optInt("endMinute", 40).coerceIn(0, 59)
        if (endHour * 60 + endMinute <= startHour * 60 + startMinute) return null
        return ExamLessonEntity(
            date = date,
            slotIndex = slotIndex,
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute,
            subject = obj.optString("subject", "").trim(),
            teacher = obj.optString("teacher", "").trim(),
            location = obj.optString("location", "").trim(),
            memo = obj.optString("memo", "").trim(),
            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
        )
    }

    private fun normalizeImportRoot(root: org.json.JSONObject, importVersion: Int): org.json.JSONObject {
        val normalized = org.json.JSONObject(root.toString())
        if (importVersion == 1) {
            normalizeV1ToV2InPlace(normalized)
        }
        normalized.optJSONObject("settings")?.let { settings ->
            if (!settings.has("periodLabelStyle")) {
                settings.put(
                    "periodLabelStyle",
                    if (settings.optBoolean("useKosenMode", true)) {
                        PeriodLabelStyle.PAIR_KOSHI.name
                    } else {
                        PeriodLabelStyle.SINGLE_KOSHI.name
                    }
                )
            }
        }
        normalized.put("version", CURRENT_EXPORT_VERSION)
        normalized.put("schema", "nittc-scheduler")
        return normalized
    }

    private fun normalizeV1ToV2InPlace(root: org.json.JSONObject) {
        root.optJSONObject("settings")?.let { s ->
            if (!s.has("useDrawerNavigation") && s.has("useHamburgerNavigation")) {
                s.put("useDrawerNavigation", s.optBoolean("useHamburgerNavigation", false))
            }
            if (!s.has("addTasksToCalendar")) {
                s.put("addTasksToCalendar", false)
            }
            if (!s.has("showCurrentTimeMarker")) {
                s.put("showCurrentTimeMarker", false)
            }
            if (!s.has("unifyTaskPlanView")) {
                s.put("unifyTaskPlanView", false)
            }
            if (!s.has("showWeekdayOnDates")) {
                s.put("showWeekdayOnDates", false)
            }
            if (!s.has("useAdvancedTimeSettingsUi")) {
                s.put("useAdvancedTimeSettingsUi", false)
            }
            s.put("enableLessonNotes", true)
            if (!s.has("lessonStartNotificationEnabled")) {
                s.put("lessonStartNotificationEnabled", false)
            }
            if (!s.has("lessonStartNotificationMinutesBefore")) {
                s.put("lessonStartNotificationMinutesBefore", 10)
            }
            if (!s.has("lessonStartNotificationLiveUpdatesEnabled")) {
                s.put("lessonStartNotificationLiveUpdatesEnabled", true)
            }
            if (!s.has("lessonStartNotificationProgressCountsDown")) {
                s.put("lessonStartNotificationProgressCountsDown", false)
            }
            if (!s.has("lessonStartNotificationLiveUpdateEarlyMinutes")) {
                s.put("lessonStartNotificationLiveUpdateEarlyMinutes", 1)
            }
            if (!s.has("lessonStartNotificationChipMode")) {
                s.put("lessonStartNotificationChipMode", LessonStartNotificationChipMode.MINUTE_TEXT.name)
            }
            if (!s.has("syncLessonsToCalendar")) {
                s.put("syncLessonsToCalendar", false)
            }
        }

        root.optJSONArray("lessons")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (!obj.has("mode") && obj.has("alternationMode")) {
                    obj.put("mode", obj.optString("alternationMode", "WEEKLY"))
                }
            }
        }

        root.optJSONArray("tasks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue

                if (!obj.has("isCompleted") && obj.has("completed")) {
                    obj.put("isCompleted", obj.optBoolean("completed", false))
                }

                if (!obj.has("dueDate") && obj.has("deadlineDate")) {
                    obj.put("dueDate", obj.optString("deadlineDate", ""))
                }
                if (!obj.has("dueHour") && obj.has("deadlineHour")) {
                    obj.put("dueHour", obj.optInt("deadlineHour", 23))
                }
                if (!obj.has("dueMinute") && obj.has("deadlineMinute")) {
                    obj.put("dueMinute", obj.optInt("deadlineMinute", 59))
                }

                if (!obj.has("useTeacherMatching")) {
                    obj.put("useTeacherMatching", false)
                }
                if (!obj.has("reminderEnabled")) {
                    obj.put("reminderEnabled", false)
                }
                if (!obj.has("reminderHour")) {
                    obj.put("reminderHour", 20)
                }
                if (!obj.has("reminderMinute")) {
                    obj.put("reminderMinute", 0)
                }
            }
        }

        if (!root.has("plans")) {
            root.put("plans", org.json.JSONArray())
        }
        if (!root.has("lessonNotificationExclusions")) {
            root.put("lessonNotificationExclusions", org.json.JSONArray())
        }
    }
}
