package com.qierong.dlnascreencastdemo.dlna.discovery

import com.qierong.dlnascreencastdemo.dlna.DiscoveryLogger
import com.qierong.dlnascreencastdemo.dlna.DlnaDevice
import com.qierong.dlnascreencastdemo.dlna.NoOpDiscoveryLogger
import com.qierong.dlnascreencastdemo.dlna.discoveryLogReason
import com.qierong.dlnascreencastdemo.dlna.discoveryLogType
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.StringReader
import java.net.URI
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import org.xml.sax.SAXException

class DeviceDescriptionParser(
    private val logger: DiscoveryLogger = NoOpDiscoveryLogger,
) {
    fun parse(descriptionUrl: URI, xml: String): DlnaDevice? {
        require(!DOCTYPE_PATTERN.containsMatchIn(xml)) {
            "XML 安全限制：禁止 DOCTYPE 声明"
        }

        val document = try {
            newSecureFactory(descriptionUrl)
                .newDocumentBuilder()
                .apply {
                    setEntityResolver { _, _ ->
                        throw SAXException("XML 安全限制：禁止外部实体解析")
                    }
                }
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

    private fun newSecureFactory(descriptionUrl: URI): DocumentBuilderFactory {
        return DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            enableOptionalSecuritySetting(descriptionUrl, "XInclude") {
                isXIncludeAware = false
            }
            enableOptionalSecuritySetting(descriptionUrl, "expandEntityReferences") {
                isExpandEntityReferences = false
            }
            enableOptionalSecuritySetting(descriptionUrl, XMLConstants.FEATURE_SECURE_PROCESSING) {
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            }
            OPTIONAL_FEATURES.forEach { (feature, enabled) ->
                enableOptionalSecuritySetting(descriptionUrl, feature) {
                    setFeature(feature, enabled)
                }
            }
            OPTIONAL_ATTRIBUTES.forEach { attribute ->
                enableOptionalSecuritySetting(descriptionUrl, attribute) {
                    setAttribute(attribute, "")
                }
            }
        }
    }

    private inline fun enableOptionalSecuritySetting(
        descriptionUrl: URI,
        setting: String,
        configure: () -> Unit,
    ) {
        try {
            configure()
        } catch (exception: Exception) {
            logger.debug(
                "XML 附加防护不可用：" +
                    "url=$descriptionUrl " +
                    "config=$setting " +
                    "type=${exception.discoveryLogType()} " +
                    "reason=${exception.discoveryLogReason()}",
            )
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
        val DOCTYPE_PATTERN = Regex("(?is).*<!DOCTYPE\\b.*")
        val OPTIONAL_FEATURES = listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
            "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
        )
        val OPTIONAL_ATTRIBUTES = listOf(
            ACCESS_EXTERNAL_DTD,
            ACCESS_EXTERNAL_SCHEMA,
        )
    }
}
