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

package com.exactpro.th2.check2.grpc;

import com.exactpro.cradle.CradleStorage;
import com.exactpro.cradle.testevents.StoredTestEventId;
import com.exactpro.cradle.testevents.StoredTestEventWrapper;
import com.exactpro.th2.check2.cache.EventsCache;
import com.exactpro.th2.check2.cfg.Check2Configuration;
import com.exactpro.th2.common.grpc.EventID;
import com.exactpro.th2.common.grpc.EventStatus;
import com.exactpro.th2.crawler.dataservice.grpc.CrawlerId;
import com.exactpro.th2.crawler.dataservice.grpc.CrawlerInfo;
import com.exactpro.th2.crawler.dataservice.grpc.DataServiceGrpc;
import com.exactpro.th2.crawler.dataservice.grpc.DataServiceInfo;
import com.exactpro.th2.crawler.dataservice.grpc.EventDataRequest;
import com.exactpro.th2.crawler.dataservice.grpc.EventResponse;
import com.exactpro.th2.crawler.dataservice.grpc.Status;
import com.exactpro.th2.dataprovider.grpc.EventData;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.exactpro.th2.common.message.MessageUtils.toJson;


public class Check2Handler extends DataServiceGrpc.DataServiceImplBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(Check2Handler.class);

    private final Check2Configuration configuration;
    private final CradleStorage storage;
    private final Map<String, InnerEvent> cache;
    private final ConcurrentHashMap.KeySetView<CrawlerId, Boolean> knownCrawlers;

    public Check2Handler(Check2Configuration configuration, CradleStorage storage) {
        this.configuration = configuration;
        this.storage = Objects.requireNonNull(storage, "Cradle storage cannot be null");
        this.cache = new EventsCache<>(configuration.getCacheSize());
        this.knownCrawlers = ConcurrentHashMap.newKeySet();
    }

    @Override
    public void crawlerConnect(CrawlerInfo request, StreamObserver<DataServiceInfo> responseObserver) {
        try {
            LOGGER.info("crawlerConnect request: {}", request);
            knownCrawlers.add(request.getId());

            DataServiceInfo response = DataServiceInfo.newBuilder()
                    .setName(configuration.getName())
                    .setVersion(configuration.getVersion())
                    .build();

            LOGGER.info("crawlerConnect response: {}", response);

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
            LOGGER.error("crawlerConnect error: " + e.getMessage(), e);
        }
    }

    @Override
    public void sendEvent(EventDataRequest request, StreamObserver<EventResponse> responseObserver) {
        try {
            LOGGER.info("sendEvent request: {}", request);

            if (!knownCrawlers.contains(request.getId())) {
                LOGGER.warn("Received request from unknown crawler with id {}. Sending response with HandshakeRequired = true", toJson(request.getId()));
                responseObserver.onNext(EventResponse.newBuilder()
                        .setStatus(Status.newBuilder().setHandshakeRequired(true))
                        .build());
                responseObserver.onCompleted();
                return;
            }

            int eventsCount = request.getEventDataCount();

            heal(request.getEventDataList());

            EventID lastEventId = null;

            if (eventsCount > 0) {
                lastEventId = request.getEventDataList().get(eventsCount - 1).getEventId();
            }

            EventResponse.Builder builder = EventResponse.newBuilder();

            if (lastEventId != null) {
                builder.setId(lastEventId);
            }

            EventResponse response = builder.build();

            LOGGER.info("sendEvent response: {}", response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
            LOGGER.error("sendEvent error: " + e, e);
        }
    }

    private void heal(Collection<EventData> events) throws IOException {
        List<InnerEvent> eventAncestors;

        for (EventData event: events) {
            if (event.getSuccessful() == EventStatus.FAILED && event.hasParentEventId()) {

                eventAncestors = getAncestors(event);

                for (InnerEvent ancestor : eventAncestors) {
                    StoredTestEventWrapper ancestorEvent = ancestor.event;

                    if (ancestor.success) {
                        storage.updateEventStatus(ancestorEvent, false);
                        ancestor.markFailed();
                        LOGGER.info("Event {} healed", ancestorEvent.getId());
                    }
                }

            }
        }
    }

    private List<InnerEvent> getAncestors(EventData event) throws IOException {
        List<InnerEvent> eventAncestors = new ArrayList<>();
        String parentId = event.getParentEventId().getId();

        while (parentId != null) {
            InnerEvent innerEvent;

            if (cache.containsKey(parentId)) {
                innerEvent = cache.get(parentId);
            } else {
                StoredTestEventWrapper parent = storage.getTestEvent(new StoredTestEventId(parentId));

                innerEvent = new InnerEvent(parent, parent.isSuccess());
                cache.put(parentId, innerEvent);
            }

            eventAncestors.add(innerEvent);

            parentId = innerEvent.event.getParentId().toString();
        }

        return eventAncestors;
    }

    private static class InnerEvent {
        private final StoredTestEventWrapper event;
        private volatile boolean success;

        private InnerEvent(StoredTestEventWrapper event, boolean success) {
            this.event = event;
            this.success = success;
        }

        private void markFailed() { this.success = false; }
    }

}
