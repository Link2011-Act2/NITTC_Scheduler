package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.NittcProfileEntity
import jp.linkserver.nittcsc.data.NittcProgram
import jp.linkserver.nittcsc.data.SchedulerUsageMode
import jp.linkserver.nittcsc.data.UsageModeEntity
import jp.linkserver.nittcsc.logic.academicYearForDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
internal fun NittcConfigurationDialog(
    mode: UsageModeEntity?, profiles: List<NittcProfileEntity>, defaultYear: Int,
    onSave: suspend (UsageModeEntity?, NittcProfileEntity?, SchedulerUsageMode, NittcProfileEntity?) -> Boolean,
    onDismiss: () -> Unit,
    required: Boolean = false,
    lockYear: Boolean = false,
    lockMode: Boolean = false,
    initialProgram: NittcProgram? = null,
    lockProgram: Boolean = false
) {
    val expectedMode = remember { mode }
    var selection by remember { mutableStateOf(mode?.mode?.let { name -> SchedulerUsageMode.entries.firstOrNull { it.name == name } }) }
    var yearText by remember { mutableStateOf((defaultYear.takeIf { it in 1900..9998 } ?: academicYearForDate(LocalDate.now())).toString()) }
    val year = yearText.toIntOrNull()?.takeIf { it in 1900..9998 }
    val expectedProfile = remember(year) { profiles.singleOrNull { it.academicYear == year } }
    var program by remember(year) { mutableStateOf(expectedProfile?.program ?: initialProgram?.name ?: "UNDERGRADUATE") }
    var grade by remember(year) { mutableIntStateOf(expectedProfile?.grade ?: 1) }
    var classId by remember(year) { mutableStateOf(expectedProfile?.classId ?: if (initialProgram == NittcProgram.ADVANCED) "AC-" else "") }
    var dd by remember(year) { mutableStateOf(expectedProfile?.isDd ?: false) }
    var international by remember(year) { mutableStateOf(expectedProfile?.isInternational ?: false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val undergraduate = program == "UNDERGRADUATE"
    val grades = if (undergraduate) (1..5).toList() else (1..2).toList()
    val classes = if (grade == 1) (1..4).map { "1-$it" } else listOf("M", "E", "I", "B").map { "$grade$it" }
    val validClass = if (undergraduate) classId in classes else Regex("^AC-[A-Z0-9]+$").matches(classId)
    val canSave = !saving && selection != null && (selection == SchedulerUsageMode.GENERIC || (year != null && grade in grades && validClass))
    AlertDialog(
        onDismissRequest = { if (!saving && !required) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !required, dismissOnClickOutside = !required),
        title = { Text(stringResource(R.string.nittc_settings_title)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!lockMode) OptionMenu(stringResource(R.string.nittc_mode_label), selection?.name.orEmpty(),
                    listOf(SchedulerUsageMode.GENERIC.name to stringResource(R.string.nittc_mode_generic),
                        SchedulerUsageMode.NITTC.name to stringResource(R.string.nittc_mode_school)),
                    enabled = !saving) { selection = SchedulerUsageMode.valueOf(it) }
                if (selection == SchedulerUsageMode.NITTC) {
                    OutlinedTextField(yearText, { yearText = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.nittc_academic_year)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !saving && !lockYear,
                        modifier = Modifier.fillMaxWidth())
                    OptionMenu(stringResource(R.string.nittc_program), program,
                        listOf("UNDERGRADUATE" to stringResource(R.string.nittc_undergraduate), "ADVANCED" to stringResource(R.string.nittc_advanced)), !saving && !lockProgram) {
                        if (program != it) { program = it; grade = 1; classId = if (it == "UNDERGRADUATE") "" else "AC-" }
                    }
                    OptionMenu(stringResource(R.string.nittc_grade), grade.toString(),
                        grades.map { it.toString() to stringResource(R.string.nittc_grade_value, it) }, !saving) {
                        val nextGrade = it.toInt()
                        if (grade != nextGrade) { grade = nextGrade; if (undergraduate) classId = "" }
                    }
                    if (undergraduate) OptionMenu(stringResource(R.string.nittc_class), classId, classes.map { it to it }, !saving) { classId = it }
                    else OutlinedTextField(classId, { classId = it.uppercase().take(32) },
                        label = { Text(stringResource(R.string.nittc_class)) }, singleLine = true,
                        enabled = !saving, modifier = Modifier.fillMaxWidth())
                    Row { Checkbox(dd, { dd = it }, enabled = !saving); Text(stringResource(R.string.nittc_dd), modifier = Modifier.padding(top = 12.dp)) }
                    Row { Checkbox(international, { international = it }, enabled = !saving); Text(stringResource(R.string.nittc_international), modifier = Modifier.padding(top = 12.dp)) }
                }
                if (error) Text(stringResource(R.string.nittc_save_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = {
                val selectedMode = selection ?: return@TextButton
                val profile = if (selectedMode == SchedulerUsageMode.NITTC) NittcProfileEntity(year!!, program, grade, classId, dd,
                    international, expectedProfile?.additionalAttributesJson ?: "[]") else null
                saving = true; error = false
                scope.launch {
                    try { if (onSave(expectedMode, expectedProfile, selectedMode, profile)) onDismiss() else error = true }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = true }
                    finally { saving = false }
                }
            }) { Text(stringResource(R.string.nittc_save)) }
        },
        dismissButton = { if (!required) TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.nittc_cancel)) } }
    )
}

@Composable
private fun OptionMenu(label: String, value: String, choices: List<Pair<String, String>>, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val placeholder = stringResource(R.string.nittc_select)
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
                Text(choices.firstOrNull { it.first == value }?.second ?: value.ifEmpty { placeholder })
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { (key, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { expanded = false; onSelect(key) }) }
            }
        }
    }
}
