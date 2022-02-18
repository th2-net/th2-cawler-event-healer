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

package com.exactpro.th2.crawler.processor.healer.grpc;

import static com.exactpro.th2.common.message.MessageUtils.toJson;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.cradle.CradleStorage;
import com.exactpro.cradle.testevents.StoredTestEventId;
import com.exactpro.cradle.testevents.StoredTestEventWrapper;
import com.exactpro.th2.common.grpc.EventID;
import com.exactpro.th2.common.grpc.EventStatus;
import com.exactpro.th2.crawler.dataprocessor.grpc.CrawlerId;
import com.exactpro.th2.crawler.dataprocessor.grpc.CrawlerInfo;
import com.exactpro.th2.crawler.dataprocessor.grpc.DataProcessorGrpc;
import com.exactpro.th2.crawler.dataprocessor.grpc.DataProcessorInfo;
import com.exactpro.th2.crawler.dataprocessor.grpc.EventDataRequest;
import com.exactpro.th2.crawler.dataprocessor.grpc.EventResponse;
import com.exactpro.th2.crawler.dataprocessor.grpc.IntervalInfo;
import com.exactpro.th2.crawler.dataprocessor.grpc.Status;
import com.exactpro.th2.crawler.processor.healer.cfg.HealerConfiguration;
import com.exactpro.th2.dataprovider.grpc.EventData;
import com.exactpro.th2.crawler.processor.healer.cache.EventsCache;
import com.google.protobuf.Empty;

import io.grpc.stub.StreamObserver;

public class HealerImpl extends DataProcessorGrpc.DataProcessorImplBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealerImpl.class);

    private final HealerConfiguration configuration;
    private final CradleStorage storage;
    private final Map<String, InnerEvent> cache;
    private final Set<CrawlerId> knownCrawlers = ConcurrentHashMap.newKeySet();

    public HealerImpl(HealerConfiguration configuration, CradleStorage storage) {
        this.configuration = requireNonNull(configuration, "Configuration cannot be null");
        this.storage = requireNonNull(storage, "Cradle storage cannot be null");
        this.cache = new EventsCache<>(configuration.getMaxCacheCapacity());
    }

    @Override
    public void crawlerConnect(CrawlerInfo request, StreamObserver<DataProcessorInfo> responseObserver) {
        try {

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("crawlerConnect request: {}", toJson(request, true));
            }

            knownCrawlers.add(request.getId());

            DataProcessorInfo response = DataProcessorInfo.newBuilder()
                    .setName(configuration.getName())
                    .setVersion(configuration.getVersion())
                    .build();

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("crawlerConnect response: {}", toJson(response, true));
            }

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
            LOGGER.error("crawlerConnect error: " + e.getMessage(), e);
        }
    }

    @Override
    public void intervalStart(IntervalInfo request, StreamObserver<Empty> responseObserver) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("intervalStart request: {}", toJson(request));
        }
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void sendEvent(EventDataRequest request, StreamObserver<EventResponse> responseObserver) {
        try {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("sendEvent request: {}", toJson(request, true));
            }

            if (!knownCrawlers.contains(request.getId())) {

                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn("Received request from unknown crawler with id {}. Sending response with HandshakeRequired = true", toJson(request.getId(), true));
                }

                responseObserver.onNext(EventResponse.newBuilder()
                        .setStatus(Status.newBuilder().setHandshakeRequired(true))
                        .build());
                responseObserver.onCompleted();
                return;
            }

            int eventsCount = request.getEventDataCount();

            Set<String> notFoundParent = new HashSet<>();


            heal(request.getEventDataList(), notFoundParent);

            EventID lastEventId = null;

            if (eventsCount > 0) {
                lastEventId = request.getEventDataList().get(eventsCount - 1).getEventId();
            }

            EventResponse.Builder builder = EventResponse.newBuilder();

            if (lastEventId != null) {
                builder.setId(lastEventId);
            }

            EventResponse response = builder.build();

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("sendEvent response: {}", toJson(response, true));
            }

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
            LOGGER.error("sendEvent error: " + e, e);
        }
    }

    private void heal(Collection<EventData> events, Set<String> notFoundParent) throws IOException {
        List<InnerEvent> eventAncestors;
        for (EventData event : events) {
            if (event.getSuccessful() == EventStatus.FAILED && event.hasParentEventId()) {
                eventAncestors = getAncestors(event, notFoundParent);
                if (eventAncestors.isEmpty()){
                    notFoundParent.add(event.getParentEventId().getId());
                }
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

    private List<InnerEvent> getAncestors(EventData event, Set<String> notFoundParent) throws IOException {
        List<InnerEvent> eventAncestors = new ArrayList<>();
        String parentId = event.getParentEventId().getId();

        while (parentId != null) {
            InnerEvent innerEvent;

            if (cache.containsKey(parentId)) {
                innerEvent = cache.get(parentId);
            } else {
                if (notFoundParent.contains(parentId)) {
                    LOGGER.info("Parent element {} none", parentId);
                    return eventAncestors;
                }

                StoredTestEventWrapper parent = storage.getTestEvent(new StoredTestEventId(parentId));

                if (parent == null) {
                    Instant from = Instant.now();
                    Instant to = from.plus(configuration.getWaitParent(), configuration.getWaitParentOffsetUnit());
                    long sleepTime = Duration.between(from, to).abs().toMillis();
                    LOGGER.info("Waiting for parentEventId in the interval from {} to {}. Wait for {}", from, to, Duration.ofMillis(sleepTime));

                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        LOGGER.error("getAncestors error: " + e.getMessage());
                        e.printStackTrace();
                    }

                    parent = storage.getTestEvent(new StoredTestEventId(parentId));
                    if (parent == null) {
                        LOGGER.info("Failed to extract test event data {}", parentId);
                        return eventAncestors;
                    }
                }

                innerEvent = new InnerEvent(parent, parent.isSuccess());
                cache.put(parentId, innerEvent);
            }

            eventAncestors.add(innerEvent);

            if (!innerEvent.success) break;

            StoredTestEventId eventId = innerEvent.event.getParentId();

            if (eventId == null)
                break;

            parentId = eventId.toString();
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
