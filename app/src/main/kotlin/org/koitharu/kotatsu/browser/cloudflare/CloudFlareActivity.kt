package org.koitharu.kotatsu.browser.cloudflare

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.view.isInvisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.yield
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.browser.BaseBrowserActivity
import org.koitharu.kotatsu.core.exceptions.CloudFlareProtectedException
import org.koitharu.kotatsu.core.exceptions.resolve.CaptchaHandler
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.network.cookies.MutableCookieJar
import org.koitharu.kotatsu.core.parser.ParserMangaRepository
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.network.CloudFlareHelper
import org.koitharu.kotatsu.parsers.util.ifNullOrEmpty
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import javax.inject.Inject

@AndroidEntryPoint
class CloudFlareActivity : BaseBrowserActivity(), CloudFlareCallback {

	private var pendingResult = RESULT_CANCELED

	@Inject
	lateinit var cookieJar: MutableCookieJar

	@Inject
	lateinit var captchaHandler: CaptchaHandler

	private lateinit var cfClient: CloudFlareClient

	override fun onCreate2(savedInstanceState: Bundle?, source: MangaSource, repository: ParserMangaRepository?) {
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = true)
		val url = intent?.dataString
		Log.d(TAG, "onCreate2: url=$url, source=$source")
		if (url.isNullOrEmpty()) {
			Log.w(TAG, "URL is empty, finishing")
			finishAfterTransition()
			return
		}
		cfClient = CloudFlareClient(cookieJar, this, adBlock, url)
		viewBinding.webView.webViewClient = cfClient
		lifecycleScope.launch {
			try {
				proxyProvider.applyWebViewConfig()
			} catch (e: Exception) {
				Log.e(TAG, "Failed to apply WebView config", e)
				Snackbar.make(viewBinding.webView, e.getDisplayMessage(resources), Snackbar.LENGTH_LONG).show()
			}
			if (savedInstanceState == null) {
				onTitleChanged(getString(R.string.loading_), url)
				Log.d(TAG, "Loading URL: $url")
				viewBinding.webView.loadUrl(url)
			}
		}
	}

	override fun onCreateOptionsMenu(menu: Menu?): Boolean {
		menuInflater.inflate(R.menu.opt_captcha, menu)
		return super.onCreateOptionsMenu(menu)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
		android.R.id.home -> {
			viewBinding.webView.stopLoading()
			finishAfterTransition()
			true
		}

		R.id.action_done -> {
			Log.d(TAG, "User pressed Done button")
			if (cfClient.manualCheck()) {
				Log.i(TAG, "Manual check passed!")
			} else {
				Log.w(TAG, "Manual check failed - no clearance cookie found")
				Snackbar.make(
					viewBinding.webView,
					R.string.captcha_not_solved,
					Snackbar.LENGTH_SHORT
				).show()
			}
			true
		}

		R.id.action_retry -> {
			restartCheck()
			true
		}

		else -> super.onOptionsItemSelected(item)
	}

	override fun finish() {
		Log.d(TAG, "finish() called with result: $pendingResult")
		setResult(pendingResult)
		super.finish()
	}

	override fun onLoadingStateChanged(isLoading: Boolean) = Unit

	override fun onPageLoaded() {
		viewBinding.progressBar.isInvisible = true
	}

	override fun onLoopDetected() {
		Log.w(TAG, "Loop detected, restarting check")
		Snackbar.make(
			viewBinding.webView,
			R.string.network_error,
			Snackbar.LENGTH_SHORT
		).show()
		restartCheck()
	}

	override fun onCheckPassed() {
		Log.i(TAG, "Check passed! Setting result to OK")
		pendingResult = RESULT_OK
		lifecycleScope.launch {
			val source = intent?.getStringExtra(AppRouter.KEY_SOURCE)
			Log.d(TAG, "Discarding captcha for source: $source")
			if (source != null) {
				runCatchingCancellable {
					captchaHandler.discard(MangaSource(source))
				}.onFailure {
					it.printStackTraceDebug()
				}
			}
			finishAfterTransition()
		}
	}

	override fun onTitleChanged(title: CharSequence, subtitle: CharSequence?) {
		setTitle(title)
		supportActionBar?.subtitle = subtitle?.toString()?.toHttpUrlOrNull()?.host.ifNullOrEmpty { subtitle }
	}

	private fun restartCheck() {
		lifecycleScope.launch {
			viewBinding.webView.stopLoading()
			yield()
			cfClient.reset()
			val targetUrl = intent?.dataString?.toHttpUrlOrNull()
			if (targetUrl != null) {
				Log.d(TAG, "Restarting check, clearing CF cookies for: $targetUrl")
				clearCfCookies(targetUrl)
				viewBinding.webView.loadUrl(targetUrl.toString())
			}
		}
	}

	private suspend fun clearCfCookies(url: HttpUrl) = runInterruptible(Dispatchers.Default) {
		cookieJar.removeCookies(url) { cookie ->
			CloudFlareHelper.isCloudFlareCookie(cookie.name)
		}
	}

	class Contract : ActivityResultContract<CloudFlareProtectedException, Boolean>() {
		override fun createIntent(context: Context, input: CloudFlareProtectedException): Intent {
			return AppRouter.cloudFlareResolveIntent(context, input)
		}

		override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
			return resultCode == RESULT_OK
		}
	}

	companion object {
		const val TAG = "CloudFlareActivity"
	}
}
