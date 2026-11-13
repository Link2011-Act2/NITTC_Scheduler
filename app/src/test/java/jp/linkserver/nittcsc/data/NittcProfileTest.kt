package jp.linkserver.nittcsc.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NittcProfileTest {
    private fun profile(year: Int = 2026, grade: Int = 1, id: String = "1-1") = NittcProfileEntity(
        academicYear = year, program = NittcProgram.UNDERGRADUATE.name, grade = grade, classId = id,
        isDd = true, isInternational = false, additionalAttributesJson = "[\"FUTURE\"]"
    )
    private inline fun rejects(action: () -> Unit) { assertTrue(runCatching(action).isFailure) }

    @Test fun validatesClassAndSeparateStudentAttributes() {
        assertEquals(setOf("DD", "FUTURE"), profile().attributes())
        profile(grade = 2, id = "2M").validateProfile()
        profile().copy(program = "ADVANCED", grade = 1, classId = "AC-ABC").validateProfile()
        rejects { profile(grade = 2, id = "2-M").validateProfile() }
        rejects { profile(grade = 3, id = "2M").validateProfile() }
        rejects { profile().copy(additionalAttributesJson = "[\"DD\"]").validateProfile() }
        rejects { profile().copy(additionalAttributesJson = "[\"X\",\"X\"]").validateProfile() }
    }

    @Test fun backupRetainsHistoryAndUnchosenOrChosenModes() {
        val first = profile(); val second = profile(2027, 2, "2M").copy(isDd = false, isInternational = true)
        for (mode in listOf(null, UsageModeEntity(mode = null, migratedFromLegacy = true), UsageModeEntity(mode = "NITTC"))) {
            val original = ModeProfileBackup(mode, listOf(first, second))
            assertEquals(original, ModeProfileBackup.decode(JSONObject(original.encode().toString())))
        }
    }

    @Test fun backupRejectsDuplicateYearsInvalidTypesAndUnknownMode() {
        val encoded = ModeProfileBackup(UsageModeEntity(mode = "GENERIC"), listOf(profile())).encode()
        val duplicate = JSONObject(encoded.toString()); duplicate.getJSONArray("profiles").put(duplicate.getJSONArray("profiles").getJSONObject(0))
        rejects { ModeProfileBackup.decode(duplicate) }
        val invalidMode = JSONObject(encoded.toString()); invalidMode.getJSONObject("mode").put("value", "OTHER")
        rejects { ModeProfileBackup.decode(invalidMode) }
        val invalidFlag = JSONObject(encoded.toString()); invalidFlag.getJSONArray("profiles").getJSONObject(0).put("isDd", "true")
        rejects { ModeProfileBackup.decode(invalidFlag) }
    }
}
