package com.qierong.dlnascreencastdemo.dlna

interface DeviceRepository {
    suspend fun discoverRenderers(): List<DlnaDevice>
}

class WifiNotConnectedException : IllegalStateException("请先连接 Wi-Fi，再搜索设备")
