/*
 * Copyright 2022 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.processor.healer

import com.exactpro.cradle.BookId
import com.exactpro.cradle.PageId
import com.exactpro.cradle.cassandra.CassandraCradleStorage
import com.exactpro.cradle.testevents.StoredTestEventId
import com.exactpro.cradle.testevents.StoredTestEventSingle
import com.exactpro.th2.common.grpc.Event
import com.exactpro.th2.common.grpc.EventID
import com.exactpro.th2.common.grpc.EventStatus
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.RawMessage
import com.exactpro.th2.common.utils.event.EventBatcher
import com.exactpro.th2.common.utils.message.toTimestamp
import com.exactpro.th2.processor.api.IProcessor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import kotlin.test.assertFailsWith

class ProcessorTest {
    private lateinit var cradleStorage: CassandraCradleStorage
    private lateinit var eventBatcher: EventBatcher
    private lateinit var processor: IProcessor

    @BeforeEach
    fun beforeEach() {
        cradleStorage = mock {  }
        eventBatcher = mock {  }

        processor = Processor(
            cradleStorage,
            eventBatcher,
            Settings()
        )
    }

    @Test
    fun `handle message`() {
        assertFailsWith<UnsupportedOperationException>("Call unsupported expected overload") {
            processor.handle(INTERVAL_EVENT_ID, Message.getDefaultInstance())
        }
    }

    @Test
    fun `handle raw message`() {
        assertFailsWith<UnsupportedOperationException>("Call unsupported expected overload") {
            processor.handle(INTERVAL_EVENT_ID, RawMessage.getDefaultInstance())
        }
    }

    @Test
    fun `doesn't heal success event`() {
        val eventA = A_EVENT_ID.createSingleEvent(null, "A", true).apply {
            whenever(cradleStorage.getTestEvent(eq(this.id))).thenReturn(this)
        }
        val eventB = B_EVENT_ID.createSingleEvent(eventA.id, "B", true).apply {
            whenever(cradleStorage.getTestEvent(eq(this.id))).thenReturn(this)
        }

        processor.handle(INTERVAL_EVENT_ID, eventB.toGrpcEvent())
        verify(cradleStorage, never().description("Load event")).getTestEvent(any())
        verify(cradleStorage, never().description("Update event")).updateEventStatus(any(), any())

        verify(eventBatcher, never().description("Publish event")).onEvent(any())
    }

    @Test
    fun `heal parent events until failed event`() {
        val eventA = A_EVENT_ID.createSingleEvent(null, "A", false).apply {
            whenever(cradleStorage.getTestEvent(eq(this.id))).thenReturn(this)
        }
        val eventB = B_EVENT_ID.createSingleEvent(eventA.id, "B", true).apply {
            whenever(cradleStorage.getTestEvent(eq(this.id))).thenReturn(this)
        }
        val eventC = C_EVENT_ID.createSingleEvent(eventB.id, "C", true).apply {
            whenever(cradleStorage.getTestEvent(eq(this.id))).thenReturn(this)
        }
        val eventD = D_EVENT_ID.createSingleEvent(eventC.id, "D", false).apply {
            whenever(cradleStorage.getTestEvent(eq(this.id))).thenReturn(this)
        }

        processor.handle(INTERVAL_EVENT_ID, eventD.toGrpcEvent())
        verify(cradleStorage, times(1).description("Load event A")).getTestEvent(eq(eventA.id))
        verify(cradleStorage, times(1).description("Load event B")).getTestEvent(eq(eventB.id))
        verify(cradleStorage, times(1).description("Load event C")).getTestEvent(eq(eventC.id))
        verify(cradleStorage, never().description("Load event D")).getTestEvent(eq(eventD.id))
        verify(cradleStorage, times(3).description("Load events")).getTestEvent(any())

        verify(cradleStorage, never().description("Update event A")).updateEventStatus(eq(eventA), eq(false))
        verify(cradleStorage, times(1).description("Update event B")).updateEventStatus(eq(eventB), eq(false))
        verify(cradleStorage, times(1).description("Update event C")).updateEventStatus(eq(eventC), eq(false))
        verify(cradleStorage, never().description("Update event D")).updateEventStatus(eq(eventD), eq(false))
        verify(cradleStorage, times(2).description("Update events")).updateEventStatus(any(), any())

        verify(eventBatcher, times(2).description("Publish event")).onEvent(any())
    }

    private fun StoredTestEventSingle.toGrpcEvent() = Event.newBuilder().apply {
        id = this@toGrpcEvent.id.toGrpcEventId()
        this@toGrpcEvent.parentId?.let {
            parentId = it.toGrpcEventId()
        }
        name = this@toGrpcEvent.name
        type = this@toGrpcEvent.type
        status = if (this@toGrpcEvent.isSuccess) EventStatus.SUCCESS else EventStatus.FAILED
    }.build()

    private fun StoredTestEventId.toGrpcEventId() = EventID.newBuilder().apply {
        bookName = this@toGrpcEventId.bookId.name
        scope = this@toGrpcEventId.scope
        id = this@toGrpcEventId.id
        startTimestamp = this@toGrpcEventId.startTimestamp.toTimestamp()
    }.build()

    private fun String.createEventId() = StoredTestEventId(
        BookId(BOOK_NAME),
        SCOPE_NAME,
        Instant.now(),
        this
    )

    private fun String.createSingleEvent(
        parentId: StoredTestEventId?,
        description: String,
        success: Boolean
    ): StoredTestEventSingle {
        val id = this.createEventId()
        return StoredTestEventSingle(
            id,
            "$description name",
            "$description type",
            parentId,
            null,
            success,
            ByteArray(10),
            emptySet(),
            PageId(id.bookId, PAGE_NAME),
            null,
            Instant.now()
        )
    }

//    @Throws(CradleStorageException::class)
//    private fun createEvents() {
//        val instant = Instant.now()
//        val parentEventToStore: TestEventToStore = TestEventToStore.builder()
//            .startTimestamp(instant)
//            .endTimestamp(instant.plus(1, ChronoUnit.MINUTES))
//            .name("parent_event_name")
//            .content(byteArrayOf(1, 2, 3))
//            .id(StoredTestEventId(PARENT_EVENT_ID))
//            .success(true)
//            .type("event_type")
//            .success(true)
//            .build()
//        val parentEventData: StoredTestEvent = StoredTestEvent.newStoredTestEventSingle(parentEventToStore)
//        val parentEvent = StoredTestEventWrapper(parentEventData)
//        val childEventToStore: TestEventToStore = TestEventToStore.builder()
//            .startTimestamp(instant.plus(2, ChronoUnit.MINUTES))
//            .endTimestamp(instant.plus(3, ChronoUnit.MINUTES))
//            .name("child_event_name")
//            .content(byteArrayOf(1, 2, 3))
//            .id(StoredTestEventId("child_event_id"))
//            .parentId(StoredTestEventId(PARENT_EVENT_ID))
//            .success(true)
//            .type("event_type")
//            .build()
//        val childEventData: StoredTestEvent = StoredTestEvent.newStoredTestEventSingle(childEventToStore)
//        val childEvent = StoredTestEventWrapper(childEventData)
//        val grandchildEventToStore: TestEventToStore = TestEventToStore.builder()
//            .startTimestamp(instant.plus(4, ChronoUnit.MINUTES))
//            .endTimestamp(instant.plus(5, ChronoUnit.MINUTES))
//            .name("grandchild_event_name")
//            .content(byteArrayOf(1, 2, 3))
//            .id(StoredTestEventId(GRANDCHILD_EVENT_ID))
//            .parentId(StoredTestEventId(CHILD_EVENT_ID))
//            .type("event_type")
//            .success(false)
//            .build()
//        val grandchildEventData: StoredTestEvent = StoredTestEvent.newStoredTestEventSingle(grandchildEventToStore)
//        val grandchildEvent = StoredTestEventWrapper(grandchildEventData)
//        events.add(parentEvent)
//        events.add(childEvent)
//        events.add(grandchildEvent)
//    }

    companion object {
        private const val BOOK_NAME = "book"
        private const val PAGE_NAME = "page"
        private const val SCOPE_NAME = "scope"
        private const val A_EVENT_ID = "a_event_id"
        private const val B_EVENT_ID = "b_event_id"
        private const val C_EVENT_ID = "c_event_id"
        private const val D_EVENT_ID = "d_event_id"

        private val INTERVAL_EVENT_ID = EventID.newBuilder().apply {
            bookName = BOOK_NAME
            scope = SCOPE_NAME
            id = "interval event id"
            startTimestamp = Instant.now().toTimestamp()
        }.build()
    }
}