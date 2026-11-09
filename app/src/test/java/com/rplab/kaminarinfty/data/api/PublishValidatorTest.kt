package com.rplab.kaminarinfty.data.api

import com.rplab.kaminarinfty.model.AuthMethod
import com.rplab.kaminarinfty.model.ServerConfig
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PublishValidatorTest {

    private fun config(
        method: AuthMethod = AuthMethod.GUEST,
        username: String = "",
        secret: String = "",
        serverUrl: String = "https://ntfy.example.com",
    ) = ServerConfig(
        serverUrl = serverUrl,
        authMethod = method,
        username = username,
        tokenOrPassword = secret,
        useHttpsOnly = true,
    )

    @Test
    fun `valid request passes`() {
        assertNull(
            PublishValidator.validate(
                config(),
                PublishRequest(
                    topic = "alerts",
                    message = "hello",
                    title = "Title",
                    priority = 5,
                    tags = listOf("warning", "disk"),
                    clickUrl = "https://example.com/panel",
                    attachUrl = "https://example.com/chart.png",
                    delay = "30m",
                ),
            ),
        )
    }

    @Test
    fun `invalid topic is rejected`() {
        assertNotNull(PublishValidator.validate(config(), PublishRequest("bad topic!", "hello")))
    }

    @Test
    fun `blank message is rejected`() {
        assertNotNull(PublishValidator.validate(config(), PublishRequest("alerts", "  ")))
    }

    @Test
    fun `priority outside range is rejected`() {
        assertNotNull(PublishValidator.validate(config(), PublishRequest("alerts", "hi", priority = 0)))
        assertNotNull(PublishValidator.validate(config(), PublishRequest("alerts", "hi", priority = 6)))
    }

    @Test
    fun `tag with comma is rejected`() {
        assertNotNull(
            PublishValidator.validate(
                config(),
                PublishRequest("alerts", "hi", tags = listOf("a,b")),
            ),
        )
    }

    @Test
    fun `non http click and attach urls are rejected`() {
        assertNotNull(
            PublishValidator.validate(config(), PublishRequest("alerts", "hi", clickUrl = "ftp://x")),
        )
        assertNotNull(
            PublishValidator.validate(config(), PublishRequest("alerts", "hi", attachUrl = "/relative.png")),
        )
    }

    @Test
    fun `invalid delay is rejected`() {
        assertNotNull(PublishValidator.validate(config(), PublishRequest("alerts", "hi", delay = "tomorrow")))
    }

    @Test
    fun `unix timestamp delay is accepted`() {
        assertNull(PublishValidator.validate(config(), PublishRequest("alerts", "hi", delay = "1700000000")))
    }

    @Test
    fun `basic auth without credentials is rejected`() {
        assertNotNull(
            PublishValidator.validate(
                config(AuthMethod.USERNAME_PASSWORD, username = "alice"),
                PublishRequest("alerts", "hi"),
            ),
        )
    }

    @Test
    fun `bearer auth without token is rejected`() {
        assertNotNull(
            PublishValidator.validate(
                config(AuthMethod.ACCESS_TOKEN),
                PublishRequest("alerts", "hi"),
            ),
        )
    }

    @Test
    fun `blank server url is rejected`() {
        assertNotNull(
            PublishValidator.validate(
                config(serverUrl = ""),
                PublishRequest("alerts", "hi"),
            ),
        )
    }
}
