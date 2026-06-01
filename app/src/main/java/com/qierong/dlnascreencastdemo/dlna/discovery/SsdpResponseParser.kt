package com.qierong.dlnascreencastdemo.dlna.discovery

import java.net.URI

object SsdpResponseParser {
    fun parseDescriptionUrl(response: String): URI? {
        val location = response
            .lineSequence()
            .map(String::trim)
            .firstOrNull { it.substringBefore(':').equals("location", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return null

        return runCatching {
            URI(location).normalize().takeIf(::isSupportedHttpUri)
        }.getOrNull()
    }

    private fun isSupportedHttpUri(uri: URI): Boolean {
        return !uri.host.isNullOrBlank() &&
            (
                uri.scheme.equals("http", ignoreCase = true) ||
                    uri.scheme.equals("https", ignoreCase = true)
                )
    }
}
