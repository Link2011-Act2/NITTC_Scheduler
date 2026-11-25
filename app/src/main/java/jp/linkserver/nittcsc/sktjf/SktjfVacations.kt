package jp.linkserver.nittcsc.sktjf

import java.net.URI
import java.security.MessageDigest
import java.time.LocalDate

internal const val VACATION_MANIFEST_NAME = "vacations.manifest.json"
internal const val VACATION_ASSET_NAME = "vacations.json"
internal const val VACATION_MANIFEST_LIMIT = 16 * 1024
internal const val VACATION_ASSET_LIMIT = 512 * 1024
private val vacationTypes = setOf("summer", "winter", "year_end", "spring")
private val vacationHash = Regex("[a-f0-9]{64}")

internal data class SktjfVacationSource(val name: String, val url: String?, val sha256: String, val page: Int)
internal data class SktjfVacation(
    val id: String,
    val type: String,
    val name: String,
    val start: LocalDate,
    val end: LocalDate?,
    val confirmedThrough: LocalDate,
    val estimatedEndMonth: Int?,
    val sources: List<SktjfVacationSource>
) {
    fun covers(date: LocalDate): Boolean = date in start..confirmedThrough
}

internal data class SktjfVacationAsset(val revision: String, val url: String, val sha256: String)

internal object SktjfVacationJson {
    fun manifest(bytes: ByteArray, repository: String, revision: String): SktjfVacationAsset {
        require(parseSktjfRevision(revision) != null)
        val root = strictSktjfObject(bytes, VACATION_MANIFEST_LIMIT)
        root.fields(setOf("format", "manifestVersion", "schoolId", "revision", "asset"))
        require(root.text("format") == "SKTJF-VACATIONS-MANIFEST" && root.text("schoolId") == "nittc")
        require(root.integer("manifestVersion", 1..1) == 1 && root.text("revision") == revision)
        val asset = root.getJSONObject("asset")
        asset.fields(setOf("name", "url", "sha256", "dataFormatVersion"))
        require(asset.text("name") == VACATION_ASSET_NAME && asset.integer("dataFormatVersion", 1..1) == 1)
        val url = asset.text("url", 2048)
        require(url == "https://github.com/$repository/releases/download/$revision/$VACATION_ASSET_NAME")
        return SktjfVacationAsset(revision, url, asset.text("sha256").also { require(vacationHash.matches(it)) })
    }

    fun periods(bytes: ByteArray, asset: SktjfVacationAsset): List<SktjfVacation> {
        require(bytes.size in 1..VACATION_ASSET_LIMIT)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(MessageDigest.isEqual(digest.toByteArray(Charsets.US_ASCII), asset.sha256.toByteArray(Charsets.US_ASCII)))
        val root = strictSktjfObject(bytes, VACATION_ASSET_LIMIT)
        root.fields(setOf("format", "schemaVersion", "schoolId", "revision", "periods"))
        require(root.text("format") == "SKTJF-VACATIONS" && root.text("schoolId") == "nittc")
        require(root.integer("schemaVersion", 1..1) == 1 && root.text("revision") == asset.revision)
        val periods = root.getJSONArray("periods").objects(128).map { item ->
            item.fields(setOf("id", "type", "name", "start", "end", "confirmedThrough", "estimatedEndMonth", "sources"))
            val id = item.text("id")
            val type = item.text("type").also { require(it in vacationTypes) }
            val year = id.take(4).toInt()
            require(year in 1900..9998 && id == "$year-$type")
            val start = date(item.text("start"))
            val end = item.optionalText("end")?.let(::date)
            val through = date(item.text("confirmedThrough"))
            require(start in LocalDate.of(year, 4, 1)..LocalDate.of(year + 1, 3, 31))
            require(through in start..LocalDate.of(year + 1, 12, 31) && (end == null || end == through))
            val estimate = if (item.isNull("estimatedEndMonth")) null else item.integer("estimatedEndMonth", 1..12)
            require(end == null || estimate == null)
            val sources = item.getJSONArray("sources").objects(16).map { source ->
                source.fields(setOf("name", "url", "sha256", "page"))
                val url = source.optionalText("url", 2048)
                if (url != null) require(URI(url).let { it.scheme == "https" && it.host != null && it.userInfo == null })
                val hash = source.text("sha256").also { require(vacationHash.matches(it)) }
                SktjfVacationSource(source.text("name", 512), url, hash, source.integer("page", 1..Int.MAX_VALUE))
            }
            require(sources.isNotEmpty() && sources.distinct().size == sources.size)
            SktjfVacation(id, type, item.text("name", 128), start, end, through, estimate, sources)
        }.sortedBy { it.start }
        require(periods.map { it.id }.distinct().size == periods.size)
        for ((index, period) in periods.withIndex()) {
            for (next in periods.drop(index + 1)) {
                if (period.confirmedThrough < next.start) break
                require(period.type == "year_end" && next.type == "spring" &&
                    period.id.take(4).toInt() + 1 == next.id.take(4).toInt() &&
                    period.end != null && period.end <= next.confirmedThrough)
            }
        }
        return periods
    }

    private fun date(value: String): LocalDate {
        require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(value))
        return LocalDate.parse(value)
    }
}
