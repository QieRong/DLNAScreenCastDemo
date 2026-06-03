package com.qierong.dlnascreencastdemo.dlna.control

class SoapRequestBuilder {
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

    private fun String.xmlEscaped(): String = buildString(length) {
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
