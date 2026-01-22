package org.koitharu.kotatsu.backups.ui.backup

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.backups.domain.BackupSection
import org.koitharu.kotatsu.backups.domain.BackupUtils
import org.koitharu.kotatsu.backups.ui.restore.BackupSectionModel
import org.koitharu.kotatsu.backups.ui.restore.BackupSectionsAdapter
import org.koitharu.kotatsu.core.ui.AlertDialogFragment
import org.koitharu.kotatsu.core.ui.list.OnListItemClickListener
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.tryLaunch
import org.koitharu.kotatsu.databinding.DialogSelectiveBackupBinding

/**
 * Dialog for creating selective backups.
 * Allows users to choose which sections to include in the backup.
 */
@AndroidEntryPoint
class SelectiveBackupDialogFragment : AlertDialogFragment<DialogSelectiveBackupBinding>(),
	OnListItemClickListener<BackupSectionModel>, View.OnClickListener {

	private val viewModel: SelectiveBackupViewModel by viewModels()

	private val backupCreateCall = registerForActivityResult(
		ActivityResultContracts.CreateDocument("application/zip"),
	) { uri: Uri? ->
		if (uri != null) {
			startBackupService(uri)
		}
	}

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = DialogSelectiveBackupBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(binding: DialogSelectiveBackupBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		val adapter = BackupSectionsAdapter(this)
		binding.recyclerView.adapter = adapter
		binding.buttonCancel.setOnClickListener(this)
		binding.buttonExport.setOnClickListener(this)
		binding.buttonSelectAll.setOnClickListener(this)
		binding.buttonSelectNone.setOnClickListener(this)
		
		viewModel.sections.observe(viewLifecycleOwner, adapter)
		viewModel.isLoading.observe(viewLifecycleOwner) { isLoading: Boolean ->
			binding.progressBar.isVisible = isLoading
			binding.recyclerView.isGone = isLoading
			binding.buttonExport.isEnabled = !isLoading
		}
		viewModel.hasSelection.observe(viewLifecycleOwner) { hasSelection: Boolean ->
			binding.buttonExport.isEnabled = hasSelection
		}
	}

	override fun onBuildDialog(builder: MaterialAlertDialogBuilder): MaterialAlertDialogBuilder {
		return super.onBuildDialog(builder)
			.setTitle(R.string.export_data)
			.setCancelable(true)
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_cancel -> dismiss()
			R.id.button_export -> {
				val filename = BackupUtils.generateFileName(v.context)
				if (!backupCreateCall.tryLaunch(filename)) {
					Toast.makeText(v.context, R.string.operation_not_supported, Toast.LENGTH_SHORT).show()
				}
			}
			R.id.button_select_all -> viewModel.selectAll()
			R.id.button_select_none -> viewModel.selectNone()
		}
	}

	override fun onItemClick(item: BackupSectionModel, view: View) {
		viewModel.onItemClick(item)
	}

	private fun startBackupService(uri: Uri) {
		val sections = viewModel.getCheckedSections()
		if (sections.isEmpty()) {
			Toast.makeText(context, R.string.nothing_selected, Toast.LENGTH_SHORT).show()
			return
		}
		
		val started = if (sections.size == BackupSection.entries.size - 1) { // -1 for INDEX
			// Full backup, use regular service
			BackupService.start(requireContext(), uri)
		} else {
			// Selective backup
			SelectiveBackupService.start(requireContext(), uri, sections)
		}
		
		if (started) {
			Toast.makeText(context, R.string.backup_creating_background, Toast.LENGTH_SHORT).show()
			dismiss()
		} else {
			Toast.makeText(context, R.string.operation_not_supported, Toast.LENGTH_SHORT).show()
		}
	}

	companion object {
		const val TAG = "SelectiveBackupDialog"
	}
}
