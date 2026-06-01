package com.qierong.dlnascreencastdemo.dlna.discovery

import com.qierong.dlnascreencastdemo.dlna.DlnaDevice
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.StringReader
import java.net.URI
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

class DeviceDescriptionParser {
    fun parse(descriptionUrl: URI, xml: String): DlnaDevice? {
        val document = try {
            newSecureFactory()
                .newDocumentBuilder()
                .parse(InputSource(StringReader(xml)))
        } catch (exception: Exception) {
            throw IllegalArgumentException("设备描述 XML 无法安全解析", exception)
        }

        val renderer = document
            .getElementsByTagNameNS("*", "device")
            .asSequence()
            .mapNotNull { it as? Element }
            .firstOrNull { element ->
                element.directChildText("deviceType")
                    ?.startsWith(MEDIA_RENDERER_PREFIX) == true
            }
            ?: return null

        val udn = renderer.directChildText("UDN").orNullIfBlank()
        val friendlyName = renderer.directChildText("friendlyName").orNullIfBlank()
            ?: UNNAMED_DEVICE
        val controlUrl = renderer
            .descendants("service")
            .firstOrNull { service ->
                service.directChildText("serviceType")
                    ?.startsWith(AV_TRANSPORT_PREFIX) == true
            }
            ?.directChildText("controlURL")
            .orNullIfBlank()
            ?.let(descriptionUrl::resolve)
            ?.toString()

        return DlnaDevice(
            id = udn ?: descriptionUrl.toString(),
            udn = udn,
            friendlyName = friendlyName,
            manufacturer = renderer.directChildText("manufacturer").orNullIfBlank(),
            modelName = renderer.directChildText("modelName").orNullIfBlank(),
            ipAddress = descriptionUrl.host.orEmpty(),
            descriptionUrl = descriptionUrl.toString(),
            avTransportControlUrl = controlUrl,
        )
    }

    private fun newSecureFactory(): DocumentBuilderFactory {
        return DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            isExpandEntityReferences = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            runCatching { setAttribute(ACCESS_EXTERNAL_DTD, "") }
            runCatching { setAttribute(ACCESS_EXTERNAL_SCHEMA, "") }
        }
    }

    private fun Element.directChildText(name: String): String? {
        return childNodes
            .asSequence()
            .filterIsInstance<Element>()
            .firstOrNull { it.localName == name || it.nodeName == name }
            ?.textContent
            ?.trim()
    }

    private fun Element.descendants(name: String): Sequence<Element> {
        return getElementsByTagNameNS("*", name)
            .asSequence()
            .mapNotNull { it as? Element }
    }

    private fun String?.orNullIfBlank(): String? = this?.takeIf(String::isNotBlank)

    private fun org.w3c.dom.NodeList.asSequence(): Sequence<Node> {
        return (0 until length).asSequence().map(::item)
    }

    private companion object {
        const val MEDIA_RENDERER_PREFIX = "urn:schemas-upnp-org:device:MediaRenderer:"
        const val AV_TRANSPORT_PREFIX = "urn:schemas-upnp-org:service:AVTransport:"
        const val UNNAMED_DEVICE = "未命名设备"
        const val ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD"
        const val ACCESS_EXTERNAL_SCHEMA =
            "http://javax.xml.XMLConstants/property/accessExternalSchema"
    }
}
