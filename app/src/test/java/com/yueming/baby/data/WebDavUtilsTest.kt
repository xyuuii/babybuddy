package com.yueming.baby.data

import com.yueming.baby.data.cloud.toWebDavConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("WebDAV 工具 & Cloud 配置单元测试")
class WebDavUtilsTest {

    // Use reflection to access private methods for testing
    // These test internal parsing/normalization logic that doesn't require network

    @Nested
    @DisplayName("URL 规范化 (normalizeUrl)")
    inner class UrlNormalizationTests {
        @Test
        fun `无协议时应默认添加 http`() {
            val raw = "192.168.0.28:5005"
            val result = invokePrivateNormalize(raw)
            assertEquals("http://192.168.0.28:5005", result)
        }

        @Test
        fun `已有 http 时应保持不变`() {
            val raw = "http://192.168.0.28:5005"
            val result = invokePrivateNormalize(raw)
            assertEquals("http://192.168.0.28:5005", result)
        }

        @Test
        fun `已有 https 时应保持不变`() {
            val raw = "https://nas.xyuuii.com:5005"
            val result = invokePrivateNormalize(raw)
            assertEquals("https://nas.xyuuii.com:5005", result)
        }

        @Test
        fun `尾部斜杠应被移除`() {
            val raw = "http://192.168.0.28:5005/"
            val result = invokePrivateNormalize(raw)
            assertEquals("http://192.168.0.28:5005", result)
        }

        @Test
        fun `纯 IP 格式`() {
            val raw = "10.0.0.1"
            val result = invokePrivateNormalize(raw)
            assertEquals("http://10.0.0.1", result)
        }
    }

    @Nested
    @DisplayName("PROPFIND XML 解析 (parsePropfindHrefs)")
    inner class PropfindParsingTests {
        @Test
        fun `标准 PROPFIND 响应应正确解析文件名`() {
            val xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <D:multistatus xmlns:D="DAV:">
                  <D:response>
                    <D:href>/yueming/data/</D:href>
                    <D:propstat>
                      <D:prop>
                        <D:displayname>data</D:displayname>
                      </D:prop>
                      <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                  </D:response>
                  <D:response>
                    <D:href>/yueming/data/babies.json</D:href>
                    <D:propstat>
                      <D:prop>
                        <D:getcontentlength>1234</D:getcontentlength>
                      </D:prop>
                      <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                  </D:response>
                  <D:response>
                    <D:href>/yueming/data/timeline.json</D:href>
                    <D:propstat>
                      <D:prop>
                        <D:getcontentlength>5678</D:getcontentlength>
                      </D:prop>
                      <D:status>HTTP/1.1 200 OK</D:status>
                    </D:propstat>
                  </D:response>
                </D:multistatus>
            """.trimIndent()

            val files = invokePrivateParseHrefs(xml, "/yueming/data")
            assertEquals(2, files.size, "应解析出2个文件（不含目录自身）")
            assertTrue(files.contains("babies.json"))
            assertTrue(files.contains("timeline.json"))
        }

        @Test
        fun `空目录应返回空集合`() {
            val xml = """
                <?xml version="1.0"?>
                <D:multistatus xmlns:D="DAV:">
                  <D:response>
                    <D:href>/yueming/data/</D:href>
                  </D:response>
                </D:multistatus>
            """.trimIndent()

            val files = invokePrivateParseHrefs(xml, "/yueming/data")
            assertTrue(files.isEmpty(), "空目录应返回空集合")
        }

        @Test
        fun `HTML 实体应正确解码`() {
            val xml = """
                <D:multistatus xmlns:D="DAV:">
                  <D:response>
                    <D:href>/yueming/data/</D:href>
                  </D:response>
                  <D:response>
                    <D:href>/yueming/data/test&amp;file.json</D:href>
                  </D:response>
                </D:multistatus>
            """.trimIndent()

            val files = invokePrivateParseHrefs(xml, "/yueming/data")
            assertTrue(files.contains("test&file.json"), "&amp; 应被解码为 &")
        }

        @Test
        fun `URL 编码的文件名应正确解码`() {
            val xml = """
                <D:multistatus xmlns:D="DAV:">
                  <D:response>
                    <D:href>/yueming/data/</D:href>
                  </D:response>
                  <D:response>
                    <D:href>/yueming/data/%E6%9C%88%E6%9C%88.json</D:href>
                  </D:response>
                </D:multistatus>
            """.trimIndent()

            val files = invokePrivateParseHrefs(xml, "/yueming/data")
            assertTrue(files.contains("月月.json"), "URL 编码的中文应被正确解码")
        }

        @Test
        fun `空 XML 应返回空集合`() {
            val files = invokePrivateParseHrefs("", "/yueming/data")
            assertTrue(files.isEmpty())
        }
    }

    @Nested
    @DisplayName("buildAbsoluteUrl 路径拼接")
    inner class AbsoluteUrlTests {
        @Test
        fun `远程路径带斜杠前缀`() {
            val config = WebDavManager.WebDavConfig(
                url = "http://192.168.0.28:5005",
                username = "test",
                password = "test"
            )
            val result = invokeBuildAbsoluteUrl(config, "/yueming/data/babies.json")
            assertEquals("http://192.168.0.28:5005/yueming/data/babies.json", result)
        }

        @Test
        fun `远程路径不带斜杠前缀`() {
            val config = WebDavManager.WebDavConfig(
                url = "http://192.168.0.28:5005",
                username = "test",
                password = "test"
            )
            val result = invokeBuildAbsoluteUrl(config, "yueming/data/babies.json")
            assertEquals("http://192.168.0.28:5005/yueming/data/babies.json", result)
        }

        @Test
        fun `base URL 尾部有斜杠`() {
            val config = WebDavManager.WebDavConfig(
                url = "http://192.168.0.28:5005/",
                username = "test",
                password = "test"
            )
            val result = invokeBuildAbsoluteUrl(config, "/yueming/data/babies.json")
            assertEquals("http://192.168.0.28:5005/yueming/data/babies.json", result)
        }
    }

    @Nested
    @DisplayName("CloudStorageConfig → WebDavConfig 转换")
    inner class CloudConfigConversionTests {
        @Test
        fun `基本转换`() {
            val cloud = com.yueming.baby.data.cloud.CloudStorageConfig(
                protocol = com.yueming.baby.data.cloud.StorageProtocol.WEBDAV,
                host = "192.168.0.28",
                port = 5005,
                username = "admin",
                password = "secret",
                webdavPath = "/yueming/"
            )
            val wd = cloud.toWebDavConfig()
            assertEquals("http://192.168.0.28:5005", wd.url)
            assertEquals("admin", wd.username)
            assertEquals("secret", wd.password)
            assertEquals("/yueming/", wd.dataPath)
        }

        @Test
        fun `https host 应保留协议`() {
            val cloud = com.yueming.baby.data.cloud.CloudStorageConfig(
                host = "https://nas.xyuuii.com",
                port = 5005,
                username = "admin",
                password = "secret"
            )
            val wd = cloud.toWebDavConfig()
            assertEquals("https://nas.xyuuii.com", wd.url)
        }
    }

    // --- Private method access helpers ---

    private fun invokePrivateNormalize(rawUrl: String): String {
        val method = WebDavManager::class.java.declaredMethods
            .first { it.name == "normalizeUrl" }
        method.isAccessible = true
        return method.invoke(WebDavManager, rawUrl) as String
    }

    private fun invokePrivateParseHrefs(xml: String, dirPath: String): Set<String> {
        val method = WebDavManager::class.java.declaredMethods
            .first { it.name == "parsePropfindHrefs" }
        method.isAccessible = true
        return method.invoke(WebDavManager, xml, dirPath) as Set<String>
    }

    private fun invokeBuildAbsoluteUrl(
        config: WebDavManager.WebDavConfig,
        remotePath: String
    ): String {
        val method = WebDavManager::class.java.declaredMethods
            .first { it.name == "buildAbsoluteUrl" }
        method.isAccessible = true
        return method.invoke(WebDavManager, config, remotePath) as String
    }
}
