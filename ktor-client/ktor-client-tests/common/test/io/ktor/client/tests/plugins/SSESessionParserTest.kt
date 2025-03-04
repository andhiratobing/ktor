/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.plugins

import io.ktor.client.plugins.sse.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.sse.*
import io.ktor.test.dispatcher.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.*
import kotlin.test.*
import kotlin.time.*

@OptIn(InternalAPI::class)
class SSESessionParserTest {

    @Test
    fun testOnlyData() {
        val expectedEvents = listOf(
            ServerSentEvent(data = "This is the first message."),
            ServerSentEvent(data = "This is the second message, it${END_OF_LINE}has two lines."),
            ServerSentEvent(data = "This is the third message.")
        )

        val input = """
            data: This is the first message.

            data: This is the second message, it
            data: has two lines.

            data: This is the third message.
        """.trimIndent() + NEWLINE
        testParsingEvents(expectedEvents, input)
    }

    @Test
    fun testEventsWithDifferentTypes() {
        val expectedEvents = listOf(
            ServerSentEvent(event = "add", data = "73857293"),
            ServerSentEvent(event = "remove", data = "2153"),
            ServerSentEvent(event = "add", data = "113411")
        )

        val input = """
            event: add
            data: 73857293

            event: remove
            data: 2153

            event: add
            data: 113411
        """.trimIndent() + NEWLINE
        testParsingEvents(expectedEvents, input)
    }

    @Test
    fun testMultilineEvent() {
        val expectedEvents = listOf(
            ServerSentEvent(data = "HI$END_OF_LINE+7${END_OF_LINE}10")
        )

        val input = """
            data: HI
            data: +7
            data: 10
        """.trimIndent() + NEWLINE
        testParsingEvents(expectedEvents, input)
    }

    @Test
    fun testComments() {
        val expectedEvents = listOf(
            ServerSentEvent(comments = "it is comment"),
            ServerSentEvent(
                data = "Hello${END_OF_LINE}World",
                comments = "second comment${END_OF_LINE}one more comment"
            ),
            ServerSentEvent(comments = "just comment")
        )

        val input = """
            : it is comment


            : second comment
            data: Hello
            : one more comment
            data: World

            :just comment
            
            :not a comment
        """.trimIndent()
        testParsingEvents(expectedEvents, input)
    }

    @Test
    fun testEmptyFields() {
        val expectedEvents = listOf(
            ServerSentEvent(id = "1", data = "first event"),
            ServerSentEvent(id = "", data = "empty id"),
            ServerSentEvent(id = "", event = "", data = ""),
            ServerSentEvent(id = "", data = ""),
            ServerSentEvent(id = "", data = END_OF_LINE)
        )

        val input = """
            data: first event
            id: 1

            data:empty id
            id

            id
            data
            event

            data

            data
            data
        """.trimIndent() + NEWLINE
        testParsingEvents(expectedEvents, input)
    }

    @Test
    fun testEventEndsWithEmptyLine() {
        val expectedEvents = listOf(
            ServerSentEvent(data = "first event"),
            ServerSentEvent(data = "second event"),
        )

        val input = """
            data: first event

            data: second event

            data: no event
            data: because no empty line after it
        """.trimIndent()
        testParsingEvents(expectedEvents, input)
    }

    @Test
    fun testIdenticalEvents() {
        val expectedEvents = listOf(
            ServerSentEvent(data = "event"),
            ServerSentEvent(data = "event")
        )

        val input = """
            data: event

            data:event
        """.trimIndent() + NEWLINE
        testParsingEvents(expectedEvents, input)
    }

    @Test
    fun testIdAndEventField() {
        val expectedEvents = listOf(
            ServerSentEvent(id = "3", data = "id is${END_OF_LINE}3"),
            ServerSentEvent(id = "3", event = "remove")
        )

        val input = """
            id: 1
            id: 2
            id: 3
            data: id is
            data: 3

            event: add
            event: remove
        """.trimIndent() + NEWLINE
        testParsingEvents(expectedEvents, input)
    }

    @Test
    fun testFullEvents() {
        val expectedEvents = listOf(
            ServerSentEvent(data = "12", id = "1", event = "add"),
            ServerSentEvent(data = "-3", id = "2", event = "remove")
        )

        val input = """
            id: 1
            data: 12
            event: add

            event: remove
            id: 2
            data: -3
        """.trimIndent() + NEWLINE
        testParsingEvents(expectedEvents, input)
    }

    @Test
    fun testSkipBlankLines() {
        val expectedEvents = listOf(
            ServerSentEvent(data = "first"),
            ServerSentEvent(data = "second")
        )

        val input = """
            data: first



            data: second
        """.trimIndent() + NEWLINE
        testParsingEvents(expectedEvents, input)
    }

    @Test
    fun testNoEvents() {
        testParsingEvents(emptyList(), "")
    }

    @Test
    fun testNullCharacterInIdIgnored() {
        val expectedEvents = listOf(
            ServerSentEvent(data = "data\u0000")
        )

        val input = """
            data: data 
            id: 123 456
        """.trimIndent() + NEWLINE
        testParsingEvents(expectedEvents, input)
    }

    @Test
    fun testRetryField() {
        val expectedEvents = listOf(
            ServerSentEvent(data = "first"),
            ServerSentEvent(data = "second", retry = 100),
            ServerSentEvent(retry = 200),
        )

        val input = """
            data: first
            
            data: second
            retry: 100
            
            retry: 200
            
            retry: no12
        """.trimIndent() + NEWLINE
        testParsingEvents(expectedEvents, input)
    }

    @OptIn(InternalAPI::class)
    private fun testParsingEvents(expectedEvents: List<ServerSentEvent>, input: String) = testSuspend {
        val session = DefaultClientSSESession(
            SSEClientContent(Duration.ZERO, showCommentEvents = true, showRetryEvents = true),
            ByteReadChannel(input),
            this.coroutineContext,
            HttpStatusCode.OK,
            buildHeaders { append(HttpHeaders.ContentType, ContentType.Text.EventStream) }
        )
        val actualEvents = session.incoming.toList()
        assertContentEquals(expectedEvents.map { it.toString() }, actualEvents.map { it.toString() })
    }

    companion object {
        private const val NEWLINE = END_OF_LINE + END_OF_LINE
    }
}
