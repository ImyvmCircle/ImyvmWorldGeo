package com.imyvm.iwg.entrypoint.register.command.helper

import com.imyvm.iwg.infra.RegionNotFoundException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CommandRegionIdentifierHandlerTest {
    @Test
    fun `out of range numeric identifier is a handled lookup miss`() {
        assertFailsWith<RegionNotFoundException> {
            resolveRegionIdentifier("999999999999999999999")
        }
    }
}
