package org.koitharu.kotatsu.reader.ui

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import org.koitharu.kotatsu.R

class ReaderMenuProvider(
	private val viewModel: ReaderViewModel,
	private val callback: Callback? = null,
) : MenuProvider {

	interface Callback {
		fun onOpenMangaInfo()
	}

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_reader, menu)
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		return when (menuItem.itemId) {
			R.id.action_info -> {
				callback?.onOpenMangaInfo()
				true
			}

			else -> false
		}
	}
}
