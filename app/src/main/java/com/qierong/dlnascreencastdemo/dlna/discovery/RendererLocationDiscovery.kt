package com.qierong.dlnascreencastdemo.dlna.discovery

import java.net.URI

fun interface RendererLocationDiscovery {
    suspend fun discoverDescriptionUrls(): List<URI>
}
