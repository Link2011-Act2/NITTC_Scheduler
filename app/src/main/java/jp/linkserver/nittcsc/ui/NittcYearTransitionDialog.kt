package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import jp.linkserver.nittcsc.R
import jp.linkserver.nittcsc.data.NittcProfileEntity
import jp.linkserver.nittcsc.data.NittcProgram
import jp.linkserver.nittcsc.data.NittcYearOutcome
import jp.linkserver.nittcsc.data.NittcYearTransitionEntity
import jp.linkserver.nittcsc.data.SchedulerUsageMode
import jp.linkserver.nittcsc.data.UsageModeEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun NittcYearTransitionDialog(
    year: Int, mode: UsageModeEntity?, profiles: List<NittcProfileEntity>, transitions: List<NittcYearTransitionEntity>,
    record: suspend (Int, UsageModeEntity?, NittcProfileEntity?, NittcYearTransitionEntity?, NittcYearOutcome) -> Boolean,
    complete: suspend (Int, UsageModeEntity?, NittcProfileEntity?, NittcYearTransitionEntity?, NittcYearOutcome, NittcProfileEntity) -> Boolean,
    onDismiss: () -> Unit,
    announced: Boolean = false
) {
    val expectedMode = remember(year) { mode }
    val expectedProfile = remember(year) { profiles.singleOrNull { it.academicYear == year } }
    val expectedTransition = remember(year) { transitions.singleOrNull { it.academicYear == year } }
    var choice by remember(year) { mutableStateOf<NittcYearOutcome?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val selected = choice
    if (selected != null) {
        NittcConfigurationDialog(mode, profiles, year, onSave = { _, _, newMode, profile ->
            if (newMode != SchedulerUsageMode.NITTC || profile == null || profile.academicYear != year) false
            else complete(year, expectedMode, expectedProfile, expectedTransition, selected, profile)
        }, onDismiss = { choice = null }, lockYear = true, lockMode = true,
            initialProgram = if (selected == NittcYearOutcome.ADVANCED) NittcProgram.ADVANCED else null,
            lockProgram = selected == NittcYearOutcome.ADVANCED)
        return
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.nittc_year_transition_title, year)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (announced) Text(stringResource(R.string.nittc_year_release_available, year))
                Text(stringResource(R.string.nittc_year_transition_question))
                listOf(
                    NittcYearOutcome.PROMOTED to R.string.nittc_year_promoted,
                    NittcYearOutcome.REPEATED to R.string.nittc_year_repeated,
                    NittcYearOutcome.TRANSFERRED to R.string.nittc_year_transferred,
                    NittcYearOutcome.ADVANCED to R.string.nittc_year_advanced
                ).forEach { (outcome, label) ->
                    TextButton(enabled = !saving, onClick = { choice = outcome }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(label))
                    }
                }
                TextButton(enabled = !saving, onClick = {
                    saving = true; error = false
                    scope.launch {
                        try {
                            if (record(year, expectedMode, expectedProfile, expectedTransition, NittcYearOutcome.GRADUATED)) onDismiss()
                            else error = true
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { error = true }
                        finally { saving = false }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.nittc_year_graduated)) }
                if (error) Text(stringResource(R.string.nittc_save_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(enabled = !saving, onClick = {
                saving = true; error = false
                scope.launch {
                    try {
                        if (record(year, expectedMode, expectedProfile, expectedTransition, NittcYearOutcome.DEFERRED)) onDismiss()
                        else error = true
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = true }
                    finally { saving = false }
                }
            }) { Text(stringResource(R.string.nittc_year_deferred)) }
        }
    )
}
