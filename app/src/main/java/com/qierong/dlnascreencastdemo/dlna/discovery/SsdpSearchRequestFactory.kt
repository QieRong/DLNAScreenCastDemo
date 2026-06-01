package com.qierong.dlnascreencastdemo.dlna.discovery

object SsdpSearchRequestFactory {
    val SEARCH_TARGETS = listOf(
        "urn:schemas-upnp-org:device:MediaRenderer:1",
        "ssdp:all",
    )

    fun create(searchTarget: String): String {
        require(searchTarget in SEARCH_TARGETS) { "不支持的 SSDP 搜索目标" }
        return buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: 239.255.255.250:1900\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 2\r\n")
            append("ST: $searchTarget\r\n")
            append("\r\n")
        }
    }
}
