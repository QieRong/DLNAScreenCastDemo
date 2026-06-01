package com.qierong.dlnascreencastdemo.dlna

data class DlnaDevice(
    val id: String,
    val udn: String?,
    val friendlyName: String,
    val manufacturer: String?,
    val modelName: String?,
    val ipAddress: String,
    val descriptionUrl: String,
    val avTransportControlUrl: String?,
)
