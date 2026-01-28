package org.koitharu.kotatsu.reader.ui.pager.standard

import android.view.View
import androidx.viewpager2.widget.ViewPager2

/**
 * A PageTransformer that removes the gap between pages during horizontal scrolling.
 * Pages are positioned flush against each other for a seamless reading experience.
 */
class GaplessPageTransformer : ViewPager2.PageTransformer {

	override fun transformPage(page: View, position: Float) {
		// Remove any elevation that might cause visual separation
		page.elevation = 0f
		
		// Ensure pages are flush against each other
		// position: -1 = fully off-screen left, 0 = fully visible, 1 = fully off-screen right
		when {
			position < -1f -> {
				// Page is way off-screen to the left
				page.translationX = 0f
			}
			position <= 1f -> {
				// Page is visible or partially visible
				// No additional translation needed - pages naturally sit flush
				page.translationX = 0f
				page.alpha = 1f
			}
			else -> {
				// Page is way off-screen to the right
				page.translationX = 0f
			}
		}
	}
}
