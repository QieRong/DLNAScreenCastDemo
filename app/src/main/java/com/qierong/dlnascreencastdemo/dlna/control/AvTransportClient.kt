package com.qierong.dlnascreencastdemo.dlna.control

import android.util.Log
import kotlin.coroutines.cancellation.CancellationException

class AvTransportClient(
    private val transport: SoapTransport = UrlConnectionSoapTransport(),
    private val requestBuilder: SoapRequestBuilder = SoapRequestBuilder(),
    private val responseParser: SoapResponseParser = SoapResponseParser(),
) : AvTransportController {
    override suspend fun setAvTransportUri(
        controlUrl: String,
        streamUrl: String,
    ): AvTransportResult = send(
        controlUrl = controlUrl,
        request = requestBuilder.setAvTransportUri(
            streamUrl = streamUrl,
            metadata = requestBuilder.buildDidlLiteMetadata(streamUrl)
        ),
        streamUrlForLog = streamUrl,
    )

    override suspend fun play(controlUrl: String): AvTransportResult =
        send(controlUrl, requestBuilder.play())

    override suspend fun pause(controlUrl: String): AvTransportResult =
        send(controlUrl, requestBuilder.pause())

    override suspend fun stop(controlUrl: String): AvTransportResult =
        send(controlUrl, requestBuilder.stop())

    private suspend fun send(
        controlUrl: String,
        request: SoapRequest,
        streamUrlForLog: String? = null,
    ): AvTransportResult {
        logInfo(
            "阶段=${request.stage.label} controlURL=$controlUrl " +
                "streamUrl=${streamUrlForLog ?: "未变更"}",
        )
        return try {
            val response = transport.post(
                url = controlUrl,
                soapAction = request.soapActionHeader,
                body = request.body,
                connectTimeoutMs = TIMEOUT_MS,
                readTimeoutMs = TIMEOUT_MS,
            )
            mapResponse(request.stage, response).also { result ->
                logResult(request.stage, response.statusCode, result)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            val summary = exception.message ?: exception::class.java.simpleName
            logWarn("阶段=${request.stage.label} 网络失败：$summary")
            AvTransportResult.Failure(
                stage = AvTransportStage.Network,
                summary = "网络失败：$summary",
            )
        }
    }

    private fun mapResponse(
        stage: AvTransportStage,
        response: SoapHttpResponse,
    ): AvTransportResult {
        if (response.statusCode !in 200..299) {
            val fault = responseParser.parseFault(response.body)
            return AvTransportResult.Failure(
                stage = stage,
                summary = listOfNotNull(
                    "HTTP ${response.statusCode}",
                    fault?.toSummary(),
                ).joinToString("；"),
                httpStatusCode = response.statusCode,
                soapFault = fault,
            )
        }
        val fault = responseParser.parseFault(response.body)
        return if (fault == null) {
            AvTransportResult.Success(stage, response.statusCode)
        } else {
            AvTransportResult.Failure(
                stage = stage,
                summary = fault.toSummary(),
                httpStatusCode = response.statusCode,
                soapFault = fault,
            )
        }
    }

    private fun logResult(
        stage: AvTransportStage,
        httpStatusCode: Int,
        result: AvTransportResult,
    ) {
        when (result) {
            is AvTransportResult.Success -> {
                logInfo("阶段=${stage.label} HTTP=$httpStatusCode 结果=成功")
            }
            is AvTransportResult.Failure -> {
                logWarn(
                    "阶段=${stage.label} HTTP=$httpStatusCode 结果=失败 摘要=${result.summary}",
                )
            }
        }
    }

    private fun logInfo(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    private fun logWarn(message: String) {
        runCatching { Log.w(TAG, message) }
    }

    private companion object {
        const val TAG = "DlnaControl"
        const val TIMEOUT_MS = 5_000
    }
}
