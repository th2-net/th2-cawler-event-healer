/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.crawler.processor.healer;

import com.exactpro.cradle.CradleStorage;
import com.exactpro.cradle.testevents.StoredTestEvent;
import com.exactpro.cradle.testevents.StoredTestEventId;
import com.exactpro.cradle.testevents.StoredTestEventWrapper;
import com.exactpro.cradle.testevents.TestEventToStore;
import com.exactpro.cradle.utils.CradleStorageException;
import com.exactpro.th2.common.grpc.EventID;
import com.exactpro.th2.common.grpc.EventStatus;
import com.exactpro.th2.crawler.dataprocessor.grpc.CrawlerId;
import com.exactpro.th2.crawler.dataprocessor.grpc.CrawlerInfo;
import com.exactpro.th2.crawler.dataprocessor.grpc.DataProcessorGrpc;
import com.exactpro.th2.crawler.dataprocessor.grpc.DataProcessorInfo;
import com.exactpro.th2.crawler.dataprocessor.grpc.EventDataRequest;
import com.exactpro.th2.crawler.dataprocessor.grpc.EventResponse;
import com.exactpro.th2.crawler.processor.healer.cfg.HealerConfiguration;
import com.exactpro.th2.crawler.processor.healer.grpc.HealerImpl;
import com.exactpro.th2.dataprovider.grpc.EventData;

import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static com.exactpro.th2.common.message.MessageUtils.toTimestamp;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HealerTest {

    private static final String HEALER_NAME = "healer";
    private static final String HEALER_VERSION = "1";
    private static final String CRAWLER_NAME = "crawler";
    private static final String PARENT_EVENT_ID = "parent_event_id";
    private static final String CHILD_EVENT_ID = "child_event_id";
    private static final String GRANDCHILD_EVENT_ID = "grandchild_event_id";
    private static final HealerConfiguration CONFIGURATION = new HealerConfiguration(HEALER_NAME, HEALER_VERSION, 100, 1, ChronoUnit.SECONDS);
    private static final CrawlerId CRAWLER_ID = CrawlerId.newBuilder().setName(CRAWLER_NAME).build();
    private static final CrawlerInfo CRAWLER_INFO = CrawlerInfo.newBuilder().setId(CRAWLER_ID).build();
    private static final CradleStorage STORAGE_MOCK = mock(CradleStorage.class);
    private static final List<StoredTestEventWrapper> events = new ArrayList<>();

    private static Server server;
    private static ManagedChannel channel;
    private static DataProcessorGrpc.DataProcessorBlockingStub blockingStub;

    @BeforeEach
    public void prepare() throws IOException, CradleStorageException {
        String serverName = InProcessServerBuilder.generateName();

        server = InProcessServerBuilder.forName(serverName)
                .addService(new HealerImpl(CONFIGURATION, STORAGE_MOCK))
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName)
                .usePlaintext()
                .directExecutor()
                .build();

        blockingStub = DataProcessorGrpc.newBlockingStub(channel);

        when(STORAGE_MOCK.getTestEvent(any(StoredTestEventId.class))).then(invocation -> {
            StoredTestEventId id = invocation.getArgument(0);

            for (StoredTestEventWrapper storedEvent : events) {
                if (storedEvent.getId().toString().equals(id.toString()))
                    return storedEvent;
            }

            return null;
        });

        createEvents();
    }

    @AfterEach
    public void shutdown() {
        server.shutdown();
        channel.shutdown();
        events.clear();
    }

    @Test
    public void handshakeHandling() {
        DataProcessorInfo dataProcessorInfo = blockingStub.crawlerConnect(CRAWLER_INFO);
        assertEquals(HEALER_NAME, dataProcessorInfo.getName());
        assertEquals(HEALER_VERSION, dataProcessorInfo.getVersion());
    }

    @Test
    public void correctEventIdInResponse() {
        EventID eventId1 = EventID.newBuilder().setId("event_id1").build();
        EventID eventId2 = EventID.newBuilder().setId("event_id2").build();

        EventDataRequest request = EventDataRequest.newBuilder()
                .setId(CRAWLER_INFO.getId())
                .addEventData(EventData.newBuilder().setEventId(eventId1).build())
                .addEventData(EventData.newBuilder().setEventId(eventId2).build())
                .build();

        blockingStub.crawlerConnect(CRAWLER_INFO);
        EventResponse response = blockingStub.sendEvent(request);

        assertEquals(eventId2.getId(), response.getId().getId());
    }

    @Test
    public void healedCorrectly() {
        EventID parentId = EventID.newBuilder().setId(PARENT_EVENT_ID).build();
        EventID childId = EventID.newBuilder().setId(CHILD_EVENT_ID).build();
        EventID grandchildId = EventID.newBuilder().setId(GRANDCHILD_EVENT_ID).build();

        Instant instant = Instant.now();

        Timestamp timestamp = toTimestamp(instant.plus(3, ChronoUnit.SECONDS));
        EventData childEvent = EventData.newBuilder()
                .setStartTimestamp(timestamp)
                .setEndTimestamp(timestamp)
                .setEventId(childId)
                .setSuccessful(EventStatus.SUCCESS)
                .build();

        EventDataRequest request = EventDataRequest.newBuilder()
                .setId(CRAWLER_INFO.getId())
                .addEventData(childEvent)
                .addEventData(childEvent)
                .addEventData(childEvent)
                .build();

        blockingStub.crawlerConnect(CRAWLER_INFO);
        blockingStub.sendEvent(request);

        instant = Instant.now();
        timestamp = toTimestamp(instant);

        EventData parentEvent = EventData.newBuilder()
                .setStartTimestamp(timestamp)
                .setEndTimestamp(timestamp)
                .setEventId(parentId)
                .setSuccessful(EventStatus.SUCCESS)
                .build();

        timestamp = toTimestamp(instant.plus(1, ChronoUnit.SECONDS));
        childEvent = EventData.newBuilder()
                .setStartTimestamp(timestamp)
                .setEndTimestamp(timestamp)
                .setEventId(childId)
                .setParentEventId(parentId)
                .setSuccessful(EventStatus.SUCCESS)
                .build();

        timestamp = toTimestamp(instant.plus(2, ChronoUnit.SECONDS));
        EventData grandchildEvent = EventData.newBuilder()
                .setStartTimestamp(timestamp)
                .setEndTimestamp(timestamp)
                .setEventId(grandchildId)
                .setParentEventId(childId)
                .setSuccessful(EventStatus.FAILED)
                .build();

        request = EventDataRequest.newBuilder()
                .setId(CRAWLER_INFO.getId())
                .addEventData(parentEvent)
                .addEventData(childEvent)
                .addEventData(grandchildEvent)
                .build();

        blockingStub.sendEvent(request);

    }

    @Test
    public void crawlerUnknown() {
        EventResponse response = blockingStub.sendEvent(EventDataRequest.newBuilder()
                .setId(CRAWLER_INFO.getId())
                .addEventData(EventData.getDefaultInstance())
                .build());

        assertTrue(response.getStatus().getHandshakeRequired());
    }

    private void createEvents() throws CradleStorageException {
        Instant instant = Instant.now();

        TestEventToStore parentEventToStore = TestEventToStore.builder()
                .startTimestamp(instant)
                .endTimestamp(instant.plus(1, ChronoUnit.MINUTES))
                .name("parent_event_name")
                .content(new byte[]{1, 2, 3})
                .id(new StoredTestEventId(PARENT_EVENT_ID))
                .success(true)
                .type("event_type")
                .success(true)
                .build();

        StoredTestEvent parentEventData = StoredTestEvent.newStoredTestEventSingle(parentEventToStore);
        StoredTestEventWrapper parentEvent = new StoredTestEventWrapper(parentEventData);

        TestEventToStore childEventToStore = TestEventToStore.builder()
                .startTimestamp(instant.plus(2, ChronoUnit.MINUTES))
                .endTimestamp(instant.plus(3, ChronoUnit.MINUTES))
                .name("child_event_name")
                .content(new byte[]{1, 2, 3})
                .id(new StoredTestEventId("child_event_id"))
                .parentId(new StoredTestEventId(PARENT_EVENT_ID))
                .success(true)
                .type("event_type")
                .build();

        StoredTestEvent childEventData = StoredTestEvent.newStoredTestEventSingle(childEventToStore);
        StoredTestEventWrapper childEvent = new StoredTestEventWrapper(childEventData);

        TestEventToStore grandchildEventToStore = TestEventToStore.builder()
                .startTimestamp(instant.plus(4, ChronoUnit.MINUTES))
                .endTimestamp(instant.plus(5, ChronoUnit.MINUTES))
                .name("grandchild_event_name")
                .content(new byte[]{1, 2, 3})
                .id(new StoredTestEventId(GRANDCHILD_EVENT_ID))
                .parentId(new StoredTestEventId(CHILD_EVENT_ID))
                .type("event_type")
                .success(false)
                .build();

        StoredTestEvent grandchildEventData = StoredTestEvent.newStoredTestEventSingle(grandchildEventToStore);
        StoredTestEventWrapper grandchildEvent = new StoredTestEventWrapper(grandchildEventData);

        events.add(parentEvent);
        events.add(childEvent);
        events.add(grandchildEvent);
    }
}
