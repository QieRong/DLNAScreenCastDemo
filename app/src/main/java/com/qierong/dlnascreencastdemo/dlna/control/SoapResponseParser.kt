package com.qierong.dlnascreencastdemo.dlna.control

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

class SoapResponseParser {
    fun parseFault(body: String): SoapFault? {
        if (!body.contains("Fault", ignoreCase = true)) return null
        val document = runCatching { body.toDocument() }.getOrNull() ?: return null
        val fault = document.firstElementByLocalName("Fault") ?: return null
        return SoapFault(
            faultCode = fault.firstTextByLocalName("faultcode"),
            faultString = fault.firstTextByLocalName("faultstring"),
            upnpErrorCode = fault.firstTextByLocalName("errorCode"),
            upnpErrorDescription = fault.firstTextByLocalName("errorDescription"),
        )
    }

    private fun String.toDocument(): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isExpandEntityReferences = false
        }
        return factory
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(toByteArray(Charsets.UTF_8)))
    }

    private fun Document.firstElementByLocalName(localName: String): Element? =
        getElementsByTagNameNS("*", localName).item(0) as? Element

    private fun Element.firstTextByLocalName(localName: String): String? =
        getElementsByTagNameNS("*", localName)
            .item(0)
            ?.textContent
            ?.trim()
            ?.takeIf(String::isNotEmpty)
}
