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

package com.exactpro.th2.dataservice.healer;

import com.exactpro.cradle.CradleStorage;
import com.exactpro.cradle.testevents.StoredTestEvent;
import com.exactpro.cradle.testevents.StoredTestEventId;
import com.exactpro.cradle.testevents.StoredTestEventWrapper;
import com.exactpro.cradle.testevents.TestEventToStore;
import com.exactpro.cradle.utils.CradleStorageException;
import com.exactpro.th2.common.grpc.EventID;
import com.exactpro.th2.common.grpc.EventStatus;
import com.exactpro.th2.crawler.dataservice.grpc.CrawlerId;
import com.exactpro.th2.crawler.dataservice.grpc.CrawlerInfo;
import com.exactpro.th2.crawler.dataservice.grpc.DataServiceGrpc;
import com.exactpro.th2.crawler.dataservice.grpc.DataServiceInfo;
import com.exactpro.th2.crawler.dataservice.grpc.EventDataRequest;
import com.exactpro.th2.crawler.dataservice.grpc.EventResponse;
import com.exactpro.th2.dataprovider.grpc.EventData;
import com.exactpro.th2.dataservice.healer.cfg.HealerConfiguration;
import com.exactpro.th2.dataservice.healer.grpc.HealerServiceImpl;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class HealerTest {
    private static final String HEALER_NAME = "healer";
    private static final String HEALER_VERSION = "1";
    private static final String CRAWLER_NAME = "crawler";
    private static final String PARENT_EVENT_ID = "parent_event_id";
    private static final String CHILD_EVENT_ID = "child_event_id";
    private static final String GRANDCHILD_EVENT_ID = "grandchild_event_id";
    private static final HealerConfiguration CONFIGURATION = new HealerConfiguration(HEALER_NAME, HEALER_VERSION, 100);
    private static final CrawlerId CRAWLER_ID = CrawlerId.newBuilder().setName(CRAWLER_NAME).build();
    private static final CrawlerInfo CRAWLER_INFO = CrawlerInfo.newBuilder().setId(CRAWLER_ID).build();
    private static final CradleStorage STORAGE_MOCK = mock(CradleStorage.class);
    private static final List<StoredTestEventWrapper> events = new ArrayList<>();

    private static Server server;
    private static ManagedChannel channel;
    private static DataServiceGrpc.DataServiceBlockingStub blockingStub;

    @BeforeEach
    public void prepare() throws IOException, CradleStorageException {
        int port = 8081;

        server = ServerBuilder.forPort(port)
                .addService(new HealerServiceImpl(CONFIGURATION, STORAGE_MOCK))
                .build()
                .start();
        channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .directExecutor()
                .build();

        blockingStub = DataServiceGrpc.newBlockingStub(channel);

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
        DataServiceInfo dataServiceInfo = blockingStub.crawlerConnect(CRAWLER_INFO);
        assertEquals(HEALER_NAME, dataServiceInfo.getName());
        assertEquals(HEALER_VERSION, dataServiceInfo.getVersion());
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
    public void healedCorrectly() throws IOException {
        EventID parentId = EventID.newBuilder().setId(PARENT_EVENT_ID).build();
        EventID childId = EventID.newBuilder().setId(CHILD_EVENT_ID).build();
        EventID grandchildId = EventID.newBuilder().setId(GRANDCHILD_EVENT_ID).build();

        EventData parentEvent = EventData.newBuilder()
                .setEventId(parentId)
                .setSuccessful(EventStatus.SUCCESS)
                .build();

        EventData childEvent = EventData.newBuilder()
                .setEventId(childId)
                .setParentEventId(parentId)
                .setSuccessful(EventStatus.SUCCESS)
                .build();

        EventData grandchildEvent = EventData.newBuilder()
                .setEventId(grandchildId)
                .setParentEventId(childId)
                .setSuccessful(EventStatus.FAILED)
                .build();

        EventDataRequest request = EventDataRequest.newBuilder()
                .setId(CRAWLER_INFO.getId())
                .addEventData(parentEvent)
                .addEventData(childEvent)
                .addEventData(grandchildEvent)
                .build();

        blockingStub.crawlerConnect(CRAWLER_INFO);
        blockingStub.sendEvent(request);

        verify(STORAGE_MOCK).updateEventStatus(events.get(0), false);
        verify(STORAGE_MOCK).updateEventStatus(events.get(1), false);
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
