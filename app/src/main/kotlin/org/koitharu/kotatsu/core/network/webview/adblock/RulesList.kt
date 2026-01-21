package org.koitharu.kotatsu.core.network.webview.adblock

import androidx.annotation.CheckResult
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Very simple implementation of adblock list parser
 * Not all features are supported
 */
class RulesList {

	private val blockRules = ArrayList<Rule>()
	private val allowRules = ArrayList<Rule>()
	private val cssSelectors = ArrayList<String>()

	/**
	 * CSS selectors to inject for element hiding.
	 * These should be applied via WebView's evaluateJavascript after page load.
	 */
	val elementHidingSelectors: List<String>
		get() = cssSelectors

	operator fun get(url: HttpUrl, baseUrl: HttpUrl?): Rule? {
		val rule = blockRules.find { x -> x(url, baseUrl) }
		return rule?.takeIf { allowRules.none { x -> x(url, baseUrl) } }
	}

	fun add(line: String) {
		val parts = line.lowercase().trim().split('$')
		parts.first().addImpl(isWhitelist = false, modifiers = parts.getOrNull(1))
	}

	fun trimToSize() {
		blockRules.trimToSize()
		allowRules.trimToSize()
		cssSelectors.trimToSize()
	}

	private fun String.addImpl(isWhitelist: Boolean, modifiers: String?) {
		val list = if (isWhitelist) allowRules else blockRules

		when {
			startsWith('!') || startsWith('[') -> {
				// Comment, do nothing
			}

			startsWith("||") -> {
				// domain
				list += Rule.Domain(substring(2).substringBefore('^').trim()).withModifiers(modifiers)
			}

			startsWith('|') -> {
				val url = substring(1).substringBefore('^').trim().toHttpUrlOrNull()
				if (url != null) {
					list += Rule.ExactUrl(url).withModifiers(modifiers)
				}
			}

			startsWith("@@") -> {
				substring(2).substringBefore('^').trim().addImpl(!isWhitelist, modifiers)
			}

			startsWith("##") -> {
				// CSS element hiding selector (generic, applies to all domains)
				val selector = substring(2).trim()
				if (selector.isNotEmpty()) {
					cssSelectors += selector
				}
			}

			else -> {
				if (endsWith('*')) {
					list += Rule.Path(this.dropLast(1), contains = true).withModifiers(modifiers)
				} else if (!contains('*')) { // wildcards is not supported yet
					list += Rule.Path(this, contains = false).withModifiers(modifiers)
				}
			}
		}
	}

	@CheckResult
	private fun Rule.withModifiers(options: String?): Rule {
		if (options.isNullOrEmpty()) {
			return this
		}
		var script: Boolean? = null
		var thirdParty: Boolean? = null
		var domains: MutableSet<String>? = null
		var domainsNot: MutableSet<String>? = null

		options.split(',').forEach { option ->
			val isNot = option.startsWith('~')
			val optionName = option.removePrefix("~")

			when {
				optionName == "script" -> script = !isNot
				optionName == "third-party" -> thirdParty = !isNot
				optionName.startsWith("domain=") -> {
					// Parse domain restriction: domain=example.com|~exclude.com
					val domainList = optionName.removePrefix("domain=").split('|')
					domainList.forEach { domain ->
						val isDomainNot = domain.startsWith('~')
						val domainName = domain.removePrefix("~").lowercase()
						if (domainName.isNotEmpty()) {
							if (isDomainNot) {
								if (domainsNot == null) domainsNot = mutableSetOf()
								domainsNot?.add(domainName)
							} else {
								if (domains == null) domains = mutableSetOf()
								domains?.add(domainName)
							}
						}
					}
				}
			}
		}
		return Rule.WithModifiers(
			baseRule = this,
			script = script,
			thirdParty = thirdParty,
			domains = domains,
			domainsNot = domainsNot,
		)
	}
}
