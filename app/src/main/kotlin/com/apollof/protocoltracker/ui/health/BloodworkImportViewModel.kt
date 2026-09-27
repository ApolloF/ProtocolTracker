package com.apollof.protocoltracker.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollof.protocoltracker.AppContainer
import com.apollof.protocoltracker.data.TrackerRepository
import com.apollof.protocoltracker.domain.io.labimport.BloodworkImport
import com.apollof.protocoltracker.domain.io.labimport.Choice
import com.apollof.protocoltracker.domain.io.labimport.ImportDraft
import com.apollof.protocoltracker.domain.io.labimport.ImportMessages
import com.apollof.protocoltracker.domain.io.labimport.ImportRead
import com.apollof.protocoltracker.domain.io.labimport.Review
import com.apollof.protocoltracker.domain.io.labimport.ReviewRow
import com.apollof.protocoltracker.domain.io.labimport.RowState
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.ui.UiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The two steps of the bloodwork import (import doc §10). */
sealed interface ImportStep {
    /** Help, Copy AI prompt and Paste answer; [message] says why the last pasted text was not read. */
    data class Start(val message: String? = null) : ImportStep

    /** The pasted answer as it will be saved; [changed] once a row was left out or kept. */
    data class Check(val review: Review, val changed: Boolean) : ImportStep
}

/**
 * Bloodwork import (dev): reads a pasted chatbot answer, applies the owner's taps and saves one entry per draw in one
 * write. The domain decides everything ([BloodworkImport], [Review]); this only holds the state. Nothing is kept across
 * process death: the import starts again at Start.
 */
class BloodworkImportViewModel(private val c: AppContainer) : ViewModel() {
    private val _step = MutableStateFlow<ImportStep>(ImportStep.Start())
    val step: StateFlow<ImportStep> = _step

    /** True once saved; the screen then opens Journal, where [AppContainer.journalFocus] shows the snackbar. */
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved

    private var draft: ImportDraft? = null
    private var existing: List<JournalEntry.Bloodwork> = emptyList()
    private val choices = mutableMapOf<Int, Choice>()
    private var saving = false

    /** Reads [text] (null: the clipboard holds no text). An answer opens Check; anything else stays on Start with its message. */
    fun paste(text: String?) {
        viewModelScope.launch {
            val today = c.clock().atZone(c.zone()).toLocalDate()
            when (val read = withContext(Dispatchers.Default) { BloodworkImport.read(text.orEmpty(), today) }) {
                is ImportRead.Refused -> _step.value = ImportStep.Start(read.problem.message)
                is ImportRead.Found -> {
                    existing = c.repository.journal.first().filterIsInstance<JournalEntry.Bloodwork>()
                    draft = read.draft
                    choices.clear()
                    _step.value = ImportStep.Check(review(), changed = false)
                }
            }
        }
    }

    /** A tap on a row: leaves a saving row out, keeps a left-out one. */
    fun toggle(row: ReviewRow) {
        if (!row.toggles || _step.value !is ImportStep.Check) return
        choices[row.row.id] = if (row.state == RowState.READY) Choice.LEAVE_OUT else Choice.KEEP
        _step.value = ImportStep.Check(review(), changed = true)
    }

    /** Back from Check: the answer and every choice are dropped. */
    fun discard() {
        draft = null
        choices.clear()
        _step.value = ImportStep.Start()
    }

    fun save() {
        val review = (_step.value as? ImportStep.Check)?.review ?: return
        if (!review.canSave || saving) return
        saving = true
        viewModelScope.launch {
            val entries = review.entries(c.zone(), c.clock()) { TrackerRepository.newId() }
            c.repository.saveJournal(entries)
            val ids = entries.map { it.id }
            c.journalFocus.request(UiMessage(ImportMessages.saved(entries.size)) { c.repository.deleteJournal(ids) })
            _saved.value = true
        }
    }

    private fun review() = Review.of(checkNotNull(draft), choices, existing, c.zone())
}
