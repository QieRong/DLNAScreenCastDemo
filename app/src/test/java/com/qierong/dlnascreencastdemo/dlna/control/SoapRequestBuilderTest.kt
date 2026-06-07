package com.qierong.dlnascreencastdemo.dlna.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SoapRequestBuilder 单元测试。
 *
 * 覆盖：
 * 1. SetAVTransportURI SOAP 请求构建与 URL 转义
 * 2. Play / Pause / Stop SOAP action 格式
 * 3. DIDL-Lite metadata 构建（PR15：Renderer 兼容性）
 * 4. XML 特殊字符转义（& < > " '）
 */
class SoapRequestBuilderTest {
    private val builder = SoapRequestBuilder()

    // ─── SetAVTransportURI ────────────────────────────────────────────────────

    @Test
    fun setAvTransportUri_escapesStreamUrlAndUsesEmptyMetadata() {
        val request = builder.setAvTransportUri(
            streamUrl = "http://192.168.1.8:8080/live.ts?token=a&b=<c>",
        )

        assertEquals(
            "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"",
            request.soapActionHeader,
        )
        assertTrue(request.body.contains("<InstanceID>0</InstanceID>"))
        assertTrue(
            request.body.contains(
                "<CurrentURI>http://192.168.1.8:8080/live.ts?token=a&amp;b=&lt;c&gt;</CurrentURI>",
            ),
        )
        assertTrue(request.body.contains("<CurrentURIMetaData></CurrentURIMetaData>"))
        // 确认未转义的原始字符不存在（防止 XML 注入）
        assertFalse(request.body.contains("token=a&b=<c>"))
    }

    // ─── Play ─────────────────────────────────────────────────────────────────

    @Test
    fun play_usesSpeedOne() {
        val request = builder.play()

        assertEquals(
            "\"urn:schemas-upnp-org:service:AVTransport:1#Play\"",
            request.soapActionHeader,
        )
        assertTrue(request.body.contains("<InstanceID>0</InstanceID>"))
        assertTrue(request.body.contains("<Speed>1</Speed>"))
    }

    // ─── Pause / Stop ─────────────────────────────────────────────────────────

    @Test
    fun pauseAndStop_useExpectedSoapActions() {
        assertEquals(
            "\"urn:schemas-upnp-org:service:AVTransport:1#Pause\"",
            builder.pause().soapActionHeader,
        )
        assertEquals(
            "\"urn:schemas-upnp-org:service:AVTransport:1#Stop\"",
            builder.stop().soapActionHeader,
        )
    }

    // ─── DIDL-Lite metadata（PR15：Renderer 兼容性）────────────────────────────

    /**
     * 验证 buildDidlLiteMetadata 输出的 DIDL-Lite 包含 object.item.videoItem 类型声明。
     * UPnP ContentDirectory 规范要求 Renderer 通过 upnp:class 识别内容类型。
     */
    @Test
    fun buildDidlLiteMetadata_containsVideoItemClass() {
        val metadata = builder.buildDidlLiteMetadata("http://192.168.1.1:8080/live.ts")
        assertTrue(
            "DIDL-Lite 必须声明 object.item.videoItem 类型",
            metadata.contains("object.item.videoItem"),
        )
    }

    /**
     * 验证 protocolInfo 使用 http-get:*:video/mp2t:* 声明 MPEG-TS over HTTP 流。
     * Kodi 等 DLNA Renderer 依赖 protocolInfo 判断是否可播放该 URI。
     */
    @Test
    fun buildDidlLiteMetadata_containsCorrectProtocolInfo() {
        val metadata = builder.buildDidlLiteMetadata("http://192.168.1.1:8080/live.ts")
        assertTrue(
            "protocolInfo 必须声明 http-get:*:video/mp2t:*",
            metadata.contains("protocolInfo=\"http-get:*:video/mp2t:*\""),
        )
    }

    /**
     * 验证流 URL 中的特殊字符在 res 元素值中被正确转义。
     * URL 可能包含 & 分隔的 query 参数，必须转义为 &amp; 才能安全嵌入 XML。
     */
    @Test
    fun buildDidlLiteMetadata_escapesUrlSpecialCharsInResElement() {
        val url = "http://192.168.1.1:8080/live.ts?nonce=abc&ver=1"
        val metadata = builder.buildDidlLiteMetadata(url)
        // & 必须被转义为 &amp;
        assertTrue(
            "URL 中的 & 必须在 DIDL-Lite res 内转义为 &amp;",
            metadata.contains("nonce=abc&amp;ver=1"),
        )
        // 原始 & 不能出现在 XML 中
        assertFalse(
            "原始 & 不能出现在 DIDL-Lite XML 中",
            metadata.contains("nonce=abc&ver=1"),
        )
    }

    /**
     * 验证 URL 中的 < > 特殊字符被转义。
     */
    @Test
    fun buildDidlLiteMetadata_escapesLtGtInUrl() {
        val url = "http://192.168.1.1:8080/live.ts?a=<b>"
        val metadata = builder.buildDidlLiteMetadata(url)
        assertTrue(metadata.contains("&lt;b&gt;"))
        assertFalse(metadata.contains("<b>"))
    }

    /**
     * 验证 URL 中的双引号被转义，防止破坏 protocolInfo 属性值边界。
     */
    @Test
    fun buildDidlLiteMetadata_escapesDoubleQuoteInUrl() {
        val url = "http://192.168.1.1:8080/live.ts?q=\"test\""
        val metadata = builder.buildDidlLiteMetadata(url)
        assertTrue(metadata.contains("&quot;test&quot;"))
    }

    /**
     * 验证 URL 中的单引号被转义。
     */
    @Test
    fun buildDidlLiteMetadata_escapesSingleQuoteInUrl() {
        val url = "http://192.168.1.1:8080/live.ts?q=it's"
        val metadata = builder.buildDidlLiteMetadata(url)
        assertTrue(metadata.contains("it&apos;s"))
    }

    /**
     * 验证 buildDidlLiteMetadata 生成的 DIDL-Lite 包含 DIDL-Lite 命名空间声明。
     */
    @Test
    fun buildDidlLiteMetadata_containsDidlLiteRootElement() {
        val metadata = builder.buildDidlLiteMetadata("http://192.168.1.1:8080/live.ts")
        assertTrue(
            "DIDL-Lite 根元素必须存在",
            metadata.contains("DIDL-Lite"),
        )
        assertTrue(
            "必须包含 UPnP metadata 命名空间",
            metadata.contains("urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"),
        )
    }

    /**
     * 验证通过 setAvTransportUri 传入 DIDL-Lite 时，metadata 会被二次 XML 转义，
     * 保证 SOAP XML 结构不被破坏（DIDL-Lite 中的 < > 等特殊字符在 SOAP 中以 &lt; &gt; 出现）。
     */
    @Test
    fun setAvTransportUri_withDidlLiteMetadata_escapesMetadataForSoapBody() {
        val streamUrl = "http://192.168.1.1:8080/live.ts"
        val didlLite = builder.buildDidlLiteMetadata(streamUrl)
        val request = builder.setAvTransportUri(streamUrl = streamUrl, metadata = didlLite)

        // metadata 经过二次转义后嵌入 SOAP，DIDL-Lite 根标签 < 应变为 &lt;
        assertTrue(
            "SOAP 中的 DIDL-Lite 必须对 < 进行 XML 转义",
            request.body.contains("&lt;DIDL-Lite"),
        )
        // 原始 <DIDL-Lite 不能直接出现在 SOAP body 中（否则破坏 XML 结构）
        assertFalse(
            "SOAP body 中不允许出现未转义的 <DIDL-Lite",
            request.body.contains("<DIDL-Lite"),
        )
    }
}
