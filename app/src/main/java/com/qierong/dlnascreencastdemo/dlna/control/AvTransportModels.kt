package com.qierong.dlnascreencastdemo.dlna.control

enum class AvTransportStage(
    val label: String,
    val actionName: String,
) {
    SetUri("SetURI", "SetAVTransportURI"),
    Play("Play", "Play"),
    Pause("Pause", "Pause"),
    Stop("Stop", "Stop"),
    Network("网络", "Network"),
}

data class SoapRequest(
    val stage: AvTransportStage,
    val soapActionHeader: String,
    val body: String,
)

data class SoapHttpResponse(
    val statusCode: Int,
    val body: String,
)

data class SoapFault(
    val faultCode: String?,
    val faultString: String?,
    val upnpErrorCode: String?,
    val upnpErrorDescription: String?,
) {
    fun toSummary(): String {
        val faultPart = listOfNotNull(faultCode, faultString)
            .joinToString("：")
            .ifBlank { "SOAP Fault" }
        val upnpPart = when {
            upnpErrorCode != null && upnpErrorDescription != null ->
                "；UPnP $upnpErrorCode：$upnpErrorDescription"
            upnpErrorCode != null -> "；UPnP $upnpErrorCode"
            upnpErrorDescription != null -> "；UPnP：$upnpErrorDescription"
            else -> ""
        }
        return "SOAP Fault $faultPart$upnpPart"
    }
}

sealed interface AvTransportResult {
    data class Success(
        val stage: AvTransportStage,
        val httpStatusCode: Int,
    ) : AvTransportResult

    data class Failure(
        val stage: AvTransportStage,
        val summary: String,
        val httpStatusCode: Int? = null,
        val soapFault: SoapFault? = null,
    ) : AvTransportResult
}

interface AvTransportController {
    suspend fun setAvTransportUri(controlUrl: String, streamUrl: String): AvTransportResult
    suspend fun play(controlUrl: String): AvTransportResult
    suspend fun pause(controlUrl: String): AvTransportResult
    suspend fun stop(controlUrl: String): AvTransportResult
}
