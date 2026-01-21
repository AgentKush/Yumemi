package org.koitharu.kotatsu.core.exceptions

/**
 * Exception thrown when the device's WebView implementation does not support proxy configuration.
 * This typically occurs on older Android versions or devices with outdated WebView providers.
 */
class ProxyWebViewUnsupportedException : IllegalStateException("Proxy for WebView is not supported on this device")
