package com.qierong.dlnascreencastdemo.dlna.discovery

import java.net.URI

fun interface DeviceDescriptionFetcher {
    fun fetch(descriptionUrl: URI): ByteArray
}
