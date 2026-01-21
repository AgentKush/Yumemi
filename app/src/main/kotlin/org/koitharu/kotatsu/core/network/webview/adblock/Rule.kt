package org.koitharu.kotatsu.core.network.webview.adblock

import okhttp3.HttpUrl

sealed interface Rule {

	operator fun invoke(url: HttpUrl, baseUrl: HttpUrl?): Boolean

	data class Domain(private val domain: String) : Rule {

		override fun invoke(url: HttpUrl, baseUrl: HttpUrl?): Boolean = (url.topPrivateDomain() ?: url.host) == domain
	}

	data class ExactUrl(private val url: HttpUrl) : Rule {

		override operator fun invoke(url: HttpUrl, baseUrl: HttpUrl?): Boolean = url == this.url
	}

	data class Path(private val path: String, private val contains: Boolean) : Rule {

		override fun invoke(url: HttpUrl, baseUrl: HttpUrl?): Boolean {
			val fullPath = url.host + "/" + url.encodedPath
			return if (contains) {
				fullPath.contains(path)
			} else {
				fullPath.endsWith(path)
			}
		}
	}

	data class WithModifiers(
		private val baseRule: Rule,
		private val script: Boolean?,
		private val thirdParty: Boolean?,
		private val domains: Set<String>?,
		private val domainsNot: Set<String>?,
	) : Rule {

		override fun invoke(url: HttpUrl, baseUrl: HttpUrl?): Boolean {
			if (!baseRule.invoke(url, baseUrl)) {
				return false
			}
			if (baseUrl == null) {
				return true
			}

			val baseDomain = baseUrl.topPrivateDomain() ?: baseUrl.host

			// Check domain restrictions
			if (domains != null && baseDomain !in domains) {
				return false
			}
			if (domainsNot != null && baseDomain in domainsNot) {
				return false
			}

			// Check third-party modifier
			thirdParty?.let {
				val isThirdPartyRequest =
					(url.topPrivateDomain() ?: url.host) != baseDomain
				if (isThirdPartyRequest != it) {
					return false
				}
			}

			// Note: script modifier is not checked here as we don't have resource type info
			return true
		}
	}
}
