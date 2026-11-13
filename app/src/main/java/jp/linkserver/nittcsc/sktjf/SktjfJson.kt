package jp.linkserver.nittcsc.sktjf

import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

private val classPattern = Regex("^(1-[1-4]|[2-5][MEIB]|AC-[A-Z0-9]+)$")
private val idPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
private val hashPattern = Regex("^[a-f0-9]{64}$")
private val revisionPattern = Regex("^(\\d{4})\\.([SF])\\.(0|[1-9][0-9]*)$")
private val clockPattern = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")
private const val MAX_MANIFEST_BYTES = 512 * 1024
private const val MAX_DOCUMENT_BYTES = 4 * 1024 * 1024

/** Parsing does not touch Room or change existing timetable data. */
internal object SktjfJson {
    fun manifest(bytes: ByteArray, repository: String): SktjfManifest {
        val root = strictSktjfObject(bytes, MAX_MANIFEST_BYTES)
        root.fields(setOf("format", "manifestVersion", "schoolId", "academicYear", "term", "revision", "publishedAt", "dataFormatVersion", "classIds", "assets"))
        require(root.text("format") == "SKTJF-MANIFEST" && root.integer("manifestVersion", 1..1) == 1)
        require(root.text("schoolId") == "nittc" && root.integer("dataFormatVersion", 1..1) == 1)
        val year = root.integer("academicYear", 1900..9998)
        val term = root.text("term").also { require(it in setOf("S", "F")) }
        val revision = revision(root.text("revision"), year, term)
        val published = root.text("publishedAt", 64).also { require(it.endsWith('Z')) }.let(Instant::parse)
        val classes = root.getJSONArray("classIds").strings(128, 128)
        require(classes.isNotEmpty() && classes.distinct().size == classes.size && classes.all(classPattern::matches))
        val assets = root.getJSONArray("assets").objects(128).map { obj ->
            obj.fields(setOf("classId", "name", "url", "sha256", "dataFormatVersion"))
            require(obj.integer("dataFormatVersion", 1..1) == 1)
            val id = obj.text("classId", 128).also { require(classPattern.matches(it)) }
            val name = obj.text("name", 160).also { require(it == "$id.sktjf.json") }
            val url = obj.text("url", 2048).also { trustedAssetUrl(it, repository, revision, name) }
            val sha = obj.text("sha256", 64).also { require(hashPattern.matches(it)) }
            SktjfAsset(id, name, url, sha)
        }
        require(assets.size == classes.size && assets.map { it.classId }.toSet() == classes.toSet())
        require(assets.map { it.classId }.distinct().size == assets.size && assets.map { it.name }.distinct().size == assets.size)
        return SktjfManifest(year, term, revision, published, assets)
    }

    fun verifiedDocument(bytes: ByteArray, manifest: SktjfManifest, asset: SktjfAsset): SktjfDocument {
        require(bytes.size in 1..MAX_DOCUMENT_BYTES && manifest.assets.contains(asset))
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(MessageDigest.isEqual(digest.toByteArray(Charsets.US_ASCII), asset.sha256.toByteArray(Charsets.US_ASCII)))
        val document = document(strictSktjfObject(bytes, MAX_DOCUMENT_BYTES))
        require(document.academicYear == manifest.academicYear && document.term == manifest.term &&
            document.revision == manifest.revision && document.classId == asset.classId)
        return document
    }

    private fun document(root: JSONObject): SktjfDocument {
        root.fields(setOf("format", "schemaVersion", "schoolId", "academicYear", "term", "classId", "revision", "sources", "periods", "slotCoverage", "lessons", "calendar"))
        require(root.text("format") == "SKTJF" && root.integer("schemaVersion", 1..1) == 1 && root.text("schoolId") == "nittc")
        val year = root.integer("academicYear", 1900..9998)
        val termObject = root.getJSONObject("term"); termObject.fields(setOf("code", "start", "end"))
        val term = termObject.text("code").also { require(it in setOf("S", "F")) }
        val start = date(termObject.text("start")); val end = date(termObject.text("end"))
        require(start <= end && start >= LocalDate.of(year, 4, 1) && end <= LocalDate.of(year + 1, 3, 31))
        val classId = root.text("classId", 128).also { require(classPattern.matches(it)) }
        val revision = revision(root.text("revision"), year, term)
        val sources = root.getJSONArray("sources").objects(128).map { obj ->
            obj.fields(setOf("id", "name", "sha256"), setOf("url"))
            val id = id(obj.text("id", 128)); val sha = obj.text("sha256", 64).also { require(hashPattern.matches(it)) }
            val url = obj.optionalText("url", 2048)?.also(::httpsUrl)
            SktjfSource(id, obj.text("name", 512), sha, url)
        }
        require(sources.isNotEmpty() && sources.map { it.id }.distinct().size == sources.size)
        val sourceIds = sources.map { it.id }.toSet()
        fun references(obj: JSONObject): List<SktjfReference> = obj.getJSONArray("sources").objects(128).map { ref ->
            ref.fields(setOf("sourceId", "page"), setOf("note"))
            SktjfReference(id(ref.text("sourceId", 128)).also { require(it in sourceIds) }, ref.integer("page", 1..100_000), ref.optionalText("note"))
        }.also { require(it.isNotEmpty()) }
        val periods = root.getJSONArray("periods").objects(12).map { obj ->
            obj.fields(setOf("number", "start", "end"))
            val first = time(obj.text("start")); val last = time(obj.text("end")); require(first < last)
            SktjfPeriod(obj.integer("number", 1..12), first, last)
        }.sortedBy { it.number }
        require(periods.isNotEmpty() && periods.map { it.number }.distinct().size == periods.size)
        require(periods.zipWithNext().all { (a, b) -> a.end <= b.start })
        val periodIds = periods.map { it.number }.toSet()
        val slots = root.getJSONArray("slotCoverage").objects(84).map { obj ->
            obj.fields(setOf("dayOfWeek", "period", "state", "sources"), setOf("containsExcluded"))
            val day = obj.integer("dayOfWeek", 1..7); val period = obj.integer("period", 1..12).also { require(it in periodIds) }
            val state = SktjfSlotState.valueOf(obj.text("state")); val excluded = if (obj.has("containsExcluded")) obj.boolean("containsExcluded") else false
            require(!(state == SktjfSlotState.EMPTY && excluded))
            SktjfSlot(day, period, state, excluded, references(obj))
        }
        require(slots.isNotEmpty() && slots.map { it.dayOfWeek to it.period }.distinct().size == slots.size)
        val lessons = root.getJSONArray("lessons").objects(1000).map { obj ->
            obj.fields(setOf("id", "dayOfWeek", "period", "cycle", "subject", "teachers", "rooms", "conditions", "kind", "sources"), setOf("remote"))
            val conditions = obj.getJSONObject("conditions"); conditions.fields(setOf("all", "none"))
            val all = conditions.getJSONArray("all").strings(64, 128); val none = conditions.getJSONArray("none").strings(64, 128)
            require(all.all(idPattern::matches) && none.all(idPattern::matches) && all.distinct().size == all.size && none.distinct().size == none.size && all.none { it in none })
            val subject = obj.text("subject", 1000); val kind = SktjfLessonKind.valueOf(obj.text("kind"))
            require((subject == "補講") == (kind == SktjfLessonKind.MAKEUP))
            require((subject == "延長枠") == (kind == SktjfLessonKind.EXTENSION))
            require(!Regex("※\\s*[23２３]").containsMatchIn(subject))
            SktjfLesson(id(obj.text("id", 128)), obj.integer("dayOfWeek", 1..7), obj.integer("period", 1..12).also { require(it in periodIds) },
                SktjfCycle.valueOf(obj.text("cycle")), subject, obj.getJSONArray("teachers").strings(64), obj.getJSONArray("rooms").strings(64),
                SktjfConditions(all.toSet(), none.toSet()), kind, if (obj.has("remote")) obj.boolean("remote") else null, references(obj))
        }
        require(lessons.map { it.id }.distinct().size == lessons.size)
        val bySlot = lessons.groupBy { it.dayOfWeek to it.period }
        val slotKeys = slots.map { it.dayOfWeek to it.period }.toSet()
        require(bySlot.keys.all { it in slotKeys })
        require(slots.all { (it.state == SktjfSlotState.PRESENT) == !bySlot[it.dayOfWeek to it.period].isNullOrEmpty() })
        // Production exports cover all weekday slots, including explicitly empty/excluded ones.
        require((1..5).all { day -> periods.all { (day to it.number) in slotKeys } })
        val calendar = root.getJSONArray("calendar").objects(370).map { obj ->
            obj.fields(setOf("date", "dayType", "sources"), setOf("overrideDayOfWeek", "overrideDayType", "event"))
            val date = date(obj.text("date")).also { require(it in start..end) }
            val type = if (obj.isNull("dayType")) null else SktjfDayType.valueOf(obj.text("dayType"))
            val dow = if (!obj.has("overrideDayOfWeek") || obj.isNull("overrideDayOfWeek")) null else obj.integer("overrideDayOfWeek", 1..7)
            val override = if (!obj.has("overrideDayType") || obj.isNull("overrideDayType")) null else SktjfDayType.valueOf(obj.text("overrideDayType")).also { require(it != SktjfDayType.HOLIDAY) }
            require(override == null || dow != null); require(type != SktjfDayType.HOLIDAY || (dow == null && override == null))
            val event = if (!obj.has("event") || obj.isNull("event")) null else obj.getJSONObject("event").let { e ->
                e.fields(setOf("name"), setOf("kind")); SktjfEvent(e.text("name", 1000), e.optionalText("kind")?.let(SktjfEventKind::valueOf))
            }
            require(type != null || event != null)
            SktjfCalendarDay(date, type, dow, override, event, references(obj))
        }
        require(calendar.isNotEmpty() && calendar.map { it.date }.distinct().size == calendar.size)
        val dates = calendar.map { it.date }.toSet(); var cursor = start
        while (cursor <= end) { if (cursor.dayOfWeek.value <= 5) require(cursor in dates); cursor = cursor.plusDays(1) }
        return SktjfDocument(year, term, start, end, classId, revision, sources, periods, slots, lessons, calendar)
    }
}

private fun id(value: String): String = value.also { require(idPattern.matches(it)) }
private fun date(value: String): LocalDate = value.also { require(Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(it)) }.let(LocalDate::parse)
private fun time(value: String): LocalTime = value.also { require(clockPattern.matches(it)) }.let(LocalTime::parse)
private fun revision(value: String, year: Int, term: String): String = value.also {
    val match = requireNotNull(revisionPattern.matchEntire(it)); require(match.groupValues[1] == year.toString() && match.groupValues[2] == term)
}
private fun httpsUrl(value: String) { val uri = URI(value); require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null) }
private fun trustedAssetUrl(value: String, repository: String, revision: String, name: String) {
    require(Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(repository))
    val uri = URI(value)
    require(uri.scheme == "https" && uri.host.equals("github.com", ignoreCase = true) && uri.port == -1 && uri.userInfo == null && uri.query == null && uri.fragment == null)
    require(uri.rawPath.equals("/$repository/releases/download/$revision/$name", ignoreCase = false))
}
