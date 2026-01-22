package org.koitharu.kotatsu.backups.ui.backup

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.backups.domain.BackupSection
import org.koitharu.kotatsu.backups.ui.restore.BackupSectionModel
import org.koitharu.kotatsu.core.ui.BaseViewModel
import javax.inject.Inject

/**
 * ViewModel for selective backup dialog.
 * Manages section selection for creating backups with specific data.
 */
@HiltViewModel
class SelectiveBackupViewModel @Inject constructor() : BaseViewModel() {

	private val _sections = MutableStateFlow<List<BackupSectionModel>>(emptyList())
	val sections: StateFlow<List<BackupSectionModel>> = _sections

	val hasSelection: StateFlow<Boolean> = _sections.map { list ->
		list.any { it.isChecked }
	}.stateIn(viewModelScope, SharingStarted.Lazily, false)

	init {
		loadSections()
	}

	private fun loadSections() {
		launchLoadingJob(Dispatchers.Default) {
			val sectionModels = BackupSection.entries
				.filter { it != BackupSection.INDEX } // INDEX is internal
				.map { section ->
					BackupSectionModel(
						section = section,
						isChecked = true, // All selected by default
						isEnabled = true, // All enabled for export
					)
				}
			_sections.value = sectionModels
		}
	}

	fun onItemClick(item: BackupSectionModel) {
		val currentList = _sections.value.toMutableList()
		val index = currentList.indexOfFirst { it.section == item.section }
		if (index >= 0) {
			currentList[index] = item.copy(isChecked = !item.isChecked)
			_sections.value = currentList
		}
	}

	fun selectAll() {
		_sections.value = _sections.value.map { it.copy(isChecked = true) }
	}

	fun selectNone() {
		_sections.value = _sections.value.map { it.copy(isChecked = false) }
	}

	fun getCheckedSections(): Set<BackupSection> {
		return _sections.value
			.filter { it.isChecked }
			.map { it.section }
			.toSet()
	}
}
