package org.koitharu.kotatsu.browser.cloudflare

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koitharu.kotatsu.browser.BrowserClient
import org.koitharu.kotatsu.core.network.cookies.MutableCookieJar
import org.koitharu.kotatsu.core.network.webview.adblock.AdBlock

private const val TAG = "CloudFlareClient"
private const val LOOP_COUNTER = 10
private const val CHECK_DELAY_MS = 1000L
private const val CF_CLEARANCE = "cf_clearance"

class CloudFlareClient(
	private val cookieJar: MutableCookieJar,
	private val callback: CloudFlareCallback,
	adBlock: AdBlock,
	private val targetUrl: String,
) : BrowserClient(callback, adBlock) {

	private val oldClearance = getClearanceFromCookieManager()
	private var counter = 0
	private var lastUrl: String? = null
	private val handler = Handler(Looper.getMainLooper())
	private val checkRunnable = Runnable { checkClearanceDelayed() }
	private var checkPassed = false

	init {
		Log.d(TAG, "Init with targetUrl=$targetUrl, oldClearance=$oldClearance")
	}

	override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
		super.onPageStarted(view, url, favicon)
		Log.d(TAG, "onPageStarted: $url")
		// Cancel any pending check when a new page starts loading
		handler.removeCallbacks(checkRunnable)
	}

	override fun onPageCommitVisible(view: WebView, url: String) {
		super.onPageCommitVisible(view, url)
		Log.d(TAG, "onPageCommitVisible: $url")
		callback.onPageLoaded()
	}

	override fun onPageFinished(webView: WebView, url: String) {
		super.onPageFinished(webView, url)
		Log.d(TAG, "onPageFinished: $url")
		callback.onPageLoaded()
		
		if (checkPassed) {
			Log.d(TAG, "Check already passed, skipping")
			return
		}
		
		// Flush WebView cookies to CookieManager before checking
		CookieManager.getInstance().flush()
		
		// Schedule a delayed check to allow cookies to propagate
		handler.removeCallbacks(checkRunnable)
		handler.postDelayed(checkRunnable, CHECK_DELAY_MS)
		
		// Track URL to detect loops (same URL loading multiple times without progress)
		if (url == lastUrl) {
			counter++
			Log.d(TAG, "Same URL detected, counter=$counter")
			if (counter >= LOOP_COUNTER) {
				Log.w(TAG, "Loop detected after $LOOP_COUNTER attempts")
				handler.removeCallbacks(checkRunnable)
				reset()
				callback.onLoopDetected()
			}
		} else {
			// Different URL - this is a redirect, not a loop
			lastUrl = url
			counter = 0
		}
	}

	fun reset() {
		Log.d(TAG, "reset()")
		counter = 0
		lastUrl = null
		checkPassed = false
		handler.removeCallbacks(checkRunnable)
	}

	/**
	 * Manually check if CloudFlare clearance was obtained.
	 * Called when user presses the "Done" button.
	 * @return true if clearance was obtained, false otherwise
	 */
	fun manualCheck(): Boolean {
		Log.d(TAG, "manualCheck()")
		CookieManager.getInstance().flush()
		val clearance = getClearanceFromCookieManager()
		Log.d(TAG, "Manual check: clearance=$clearance, oldClearance=$oldClearance")
		if (clearance != null && clearance != oldClearance) {
			checkPassed = true
			syncCookiesToOkHttp()
			callback.onCheckPassed()
			return true
		}
		return false
	}

	private fun checkClearanceDelayed() {
		if (checkPassed) return
		
		val clearance = getClearanceFromCookieManager()
		Log.d(TAG, "Delayed check: clearance=$clearance, oldClearance=$oldClearance")
		if (clearance != null && clearance != oldClearance) {
			checkPassed = true
			syncCookiesToOkHttp()
			callback.onCheckPassed()
		}
	}

	/**
	 * Get cf_clearance cookie directly from Android's CookieManager (WebView cookies)
	 */
	private fun getClearanceFromCookieManager(): String? {
		val cookieManager = CookieManager.getInstance()
		val cookies = cookieManager.getCookie(targetUrl) ?: return null
		return cookies.split(";")
			.map { it.trim() }
			.find { it.startsWith("$CF_CLEARANCE=") }
			?.substringAfter("=")
			?.also { Log.d(TAG, "Found cf_clearance cookie: ${it.take(20)}...") }
	}

	/**
	 * Sync cookies from WebView's CookieManager to OkHttp's cookie jar
	 * This ensures subsequent OkHttp requests have the clearance cookie
	 */
	private fun syncCookiesToOkHttp() {
		try {
			val httpUrl = targetUrl.toHttpUrlOrNull() ?: return
			val cookieManager = CookieManager.getInstance()
			val rawCookies = cookieManager.getCookie(targetUrl) ?: return
			
			val cookies = rawCookies.split(";").mapNotNull { raw ->
				Cookie.parse(httpUrl, raw.trim())
			}
			
			if (cookies.isNotEmpty()) {
				cookieJar.saveFromResponse(httpUrl, cookies)
				Log.d(TAG, "Synced ${cookies.size} cookies to OkHttp")
			}
		} catch (e: Exception) {
			Log.e(TAG, "Failed to sync cookies", e)
		}
	}
}
