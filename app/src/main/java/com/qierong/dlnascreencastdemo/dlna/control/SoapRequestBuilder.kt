package com.qierong.dlnascreencastdemo.dlna.control

/**
 * 构建 DLNA AVTransport SOAP 请求体。
 *
 * 所有 URL 和 metadata 内容均经 XML 转义，防止特殊字符破坏 XML 结构。
 * buildDidlLiteMetadata() 生成符合 UPnP ContentDirectory / DLNA 规范的最小 videoItem，
 * 用于 SetAVTransportURI 的 CurrentURIMetaData 字段，帮助 Renderer 识别流类型。
 */
class SoapRequestBuilder {

    /**
     * 构建 SetAVTransportURI SOAP 请求。
     *
     * @param streamUrl 流媒体 URL，例如 http://192.168.1.1:8080/live.ts
     * @param metadata  CurrentURIMetaData 字段，默认为空字符串；
     *                  如需 DIDL-Lite 可通过 buildDidlLiteMetadata() 生成后传入
     */
    fun setAvTransportUri(
        streamUrl: String,
        metadata: String = "",
    ): SoapRequest = actionRequest(
        stage = AvTransportStage.SetUri,
        innerXml = """
            <InstanceID>0</InstanceID>
            <CurrentURI>${streamUrl.xmlEscaped()}</CurrentURI>
            <CurrentURIMetaData>${metadata.xmlEscaped()}</CurrentURIMetaData>
        """.trimIndent(),
    )

    fun play(): SoapRequest = actionRequest(
        stage = AvTransportStage.Play,
        innerXml = """
            <InstanceID>0</InstanceID>
            <Speed>1</Speed>
        """.trimIndent(),
    )

    fun pause(): SoapRequest = actionRequest(
        stage = AvTransportStage.Pause,
        innerXml = "<InstanceID>0</InstanceID>",
    )

    fun stop(): SoapRequest = actionRequest(
        stage = AvTransportStage.Stop,
        innerXml = "<InstanceID>0</InstanceID>",
    )

    /**
     * 构建最小 DIDL-Lite metadata 字符串，符合 DLNA / UPnP ContentDirectory 规范。
     *
     * 结构：DIDL-Lite 根元素 → object.item.videoItem → res 元素（含 protocolInfo 属性）。
     * protocolInfo 使用 "http-get:*:video/mp2t:*" 声明 MPEG-TS over HTTP 流。
     * URL 内的特殊字符（& < > " '）已 XML 转义，可安全嵌入 XML 属性和元素值中。
     *
     * 该方法仅在 SetURI / Play 成功且 Kodi 不发 GET /live.ts 时启用（Renderer 兼容性修复）。
     *
     * @param streamUrl 流媒体 URL，例如 http://192.168.1.1:8080/live.ts
     * @param title     可选标题，默认 "Live Screen Cast"
     * @return DIDL-Lite XML 字符串，需再经 xmlEscaped() 才能嵌入 SOAP 元素值
     */
    fun buildDidlLiteMetadata(
        streamUrl: String,
        title: String = "Live Screen Cast",
    ): String =
        // 注意：返回值是原始 XML 字符串，调用 setAvTransportUri() 时会自动通过 metadata.xmlEscaped() 二次转义
        """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><item id="1" parentID="0" restricted="1"><dc:title>${title.xmlEscaped()}</dc:title><upnp:class>object.item.videoItem</upnp:class><res protocolInfo="http-get:*:video/mp2t:DLNA.ORG_OP=00;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01500000000000000000000000000000">${streamUrl.xmlEscaped()}</res></item></DIDL-Lite>"""

    private fun actionRequest(
        stage: AvTransportStage,
        innerXml: String,
    ): SoapRequest {
        val action = stage.actionName
        return SoapRequest(
            stage = stage,
            soapActionHeader = "\"$SERVICE_TYPE#$action\"",
            body = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope
                    xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                    s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                  <s:Body>
                    <u:$action xmlns:u="$SERVICE_TYPE">
                      $innerXml
                    </u:$action>
                  </s:Body>
                </s:Envelope>
            """.trimIndent(),
        )
    }

    /**
     * 对字符串中的 XML 特殊字符进行转义，防止 XML 注入。
     * 转义规则：& → &amp;  < → &lt;  > → &gt;  " → &quot;  ' → &apos;
     */
    internal fun String.xmlEscaped(): String = buildString(length) {
        this@xmlEscaped.forEach { char ->
            append(
                when (char) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&apos;"
                    else -> char
                },
            )
        }
    }

    private companion object {
        const val SERVICE_TYPE = "urn:schemas-upnp-org:service:AVTransport:1"
    }
}
