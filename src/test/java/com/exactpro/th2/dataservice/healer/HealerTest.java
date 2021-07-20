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
import com.exactpro.th2.crawler.dataservice.grpc.DataServiceInfo;
import com.exactpro.th2.crawler.dataservice.grpc.EventDataRequest;
import com.exactpro.th2.crawler.dataservice.grpc.EventResponse;
import com.exactpro.th2.crawler.dataservice.grpc.Status;
import com.exactpro.th2.dataprovider.grpc.EventData;
import com.exactpro.th2.dataservice.healer.cfg.HealerConfiguration;
import com.exactpro.th2.dataservice.healer.grpc.HealerServiceImpl;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import io.grpc.util.MutableHandlerRegistry;
import org.junit.Before;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class HealerTest {
    /**
     * This rule manages automatic graceful shutdown for the registered server at the end of test.
     */
    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    private final MutableHandlerRegistry serviceRegistry = new MutableHandlerRegistry();
    private final HealerConfiguration configuration = new HealerConfiguration("test", "1", 100);
    private final CrawlerInfo crawlerInfo = CrawlerInfo.newBuilder().setId(CrawlerId.newBuilder().setName("testCrawler")).build();
    private final CradleStorage storageMock = mock(CradleStorage.class);
    private final List<StoredTestEventWrapper> events = new ArrayList<>();


    @SuppressWarnings("unchecked")
    private final StreamObserver<DataServiceInfo> dataServiceResponseObserver
            = (StreamObserver<DataServiceInfo>) mock(StreamObserver.class);

    @SuppressWarnings("unchecked")
    private final StreamObserver<EventResponse> eventResponseObserver
            = (StreamObserver<EventResponse>) mock(StreamObserver.class);

    private HealerServiceImpl healer;

    @Before
    public void setUp() throws Exception {
        grpcCleanup.register(InProcessServerBuilder.forName("server")
                .fallbackHandlerRegistry(serviceRegistry).directExecutor().build().start());
    }

    @BeforeEach
    public void prepare() throws Exception {
        healer = new HealerServiceImpl(configuration, storageMock);

        when(storageMock.getTestEvent(any(StoredTestEventId.class))).then(invocation -> {
            StoredTestEventId id = invocation.getArgument(0);

            for (StoredTestEventWrapper storedEvent : events) {
                if (storedEvent.getId().toString().equals(id.toString()))
                    return storedEvent;
            }

            return null;
        });

        createEvents();
    }

    @Test
    public void handshakeHandling() {
        healer.crawlerConnect(crawlerInfo, dataServiceResponseObserver);

        verify(dataServiceResponseObserver).onNext(eq(DataServiceInfo.newBuilder().setName("test").setVersion("1").build()));
        verify(dataServiceResponseObserver).onCompleted();
        verifyNoMoreInteractions(dataServiceResponseObserver);
    }

    @Test
    public void correctEventIdInResponse() {
        EventDataRequest request = EventDataRequest.newBuilder()
                .setId(crawlerInfo.getId())
                .addEventData(EventData.newBuilder().setEventId(EventID.newBuilder().setId("event_id1")).build())
                .addEventData(EventData.newBuilder().setEventId(EventID.newBuilder().setId("event_id2")).build())
                .build();

        healer.crawlerConnect(crawlerInfo, dataServiceResponseObserver);

        healer.sendEvent(request, eventResponseObserver);

        verify(eventResponseObserver).onNext(eq(EventResponse.newBuilder().setId(EventID.newBuilder().setId("event_id2").build()).build()));
        verify(eventResponseObserver).onCompleted();
    }

    @Test
    public void healedCorrectly() throws IOException {
        EventID parentId = EventID.newBuilder().setId("parent_event_id").build();
        EventID childId = EventID.newBuilder().setId("child_event_id").build();
        EventID grandchildId = EventID.newBuilder().setId("grandchild_event_id").build();

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
                .setId(crawlerInfo.getId())
                .addEventData(parentEvent)
                .addEventData(childEvent)
                .addEventData(grandchildEvent)
                .build();

        healer.crawlerConnect(crawlerInfo, dataServiceResponseObserver);

        healer.sendEvent(request, eventResponseObserver);

        verify(storageMock).updateEventStatus(events.get(0), false);
        verify(storageMock).updateEventStatus(events.get(1), false);
        verify(eventResponseObserver).onCompleted();
    }

    @Test
    public void crawlerUnknown() {
        healer.sendEvent(
                EventDataRequest.newBuilder()
                        .setId(crawlerInfo.getId())
                        .addEventData(EventData.getDefaultInstance())
                        .build(), eventResponseObserver
        );

        EventResponse response = EventResponse.newBuilder()
                .setStatus(Status.newBuilder().setHandshakeRequired(true))
                .build();

        verify(eventResponseObserver).onNext(eq(response));
        verify(eventResponseObserver).onCompleted();
        verifyNoMoreInteractions(eventResponseObserver);
    }

    private void createEvents() throws CradleStorageException {
        Instant instant = Instant.now();

        TestEventToStore parentEventToStore = TestEventToStore.builder()
                .startTimestamp(instant)
                .endTimestamp(instant.plus(1, ChronoUnit.MINUTES))
                .name("parent_event_name")
                .content(new byte[]{1, 2, 3})
                .id(new StoredTestEventId("parent_event_id"))
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
                .parentId(new StoredTestEventId("parent_event_id"))
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
                .id(new StoredTestEventId("grandchild_event_id"))
                .parentId(new StoredTestEventId("child_event_id"))
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
