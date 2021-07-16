package com.exactpro.th2.dataservice.healer.util;

import com.exactpro.cradle.CradleObjectsFactory;
import com.exactpro.cradle.CradleStorage;
import com.exactpro.cradle.Direction;
import com.exactpro.cradle.TimeRelation;
import com.exactpro.cradle.intervals.IntervalsWorker;
import com.exactpro.cradle.messages.StoredMessage;
import com.exactpro.cradle.messages.StoredMessageBatch;
import com.exactpro.cradle.messages.StoredMessageFilter;
import com.exactpro.cradle.messages.StoredMessageId;
import com.exactpro.cradle.testevents.StoredTestEvent;
import com.exactpro.cradle.testevents.StoredTestEventId;
import com.exactpro.cradle.testevents.StoredTestEventMetadata;
import com.exactpro.cradle.testevents.StoredTestEventWrapper;
import com.exactpro.cradle.testevents.TestEventsMessagesLinker;
import com.exactpro.cradle.utils.CradleStorageException;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class CradleStorageTest extends CradleStorage {
    @Override
    protected String doInit(String instanceName, boolean prepareStorage) throws CradleStorageException {
        return null;
    }

    @Override
    protected void doDispose() throws CradleStorageException {

    }

    @Override
    protected void doStoreMessageBatch(StoredMessageBatch batch) throws IOException {

    }

    @Override
    protected CompletableFuture<Void> doStoreMessageBatchAsync(StoredMessageBatch batch) {
        return null;
    }

    @Override
    protected void doStoreTimeMessage(StoredMessage message) throws IOException {

    }

    @Override
    protected CompletableFuture<Void> doStoreTimeMessageAsync(StoredMessage message) {
        return null;
    }

    @Override
    protected void doStoreProcessedMessageBatch(StoredMessageBatch batch) throws IOException {

    }

    @Override
    protected CompletableFuture<Void> doStoreProcessedMessageBatchAsync(StoredMessageBatch batch) {
        return null;
    }

    @Override
    protected void doStoreTestEvent(StoredTestEvent event) throws IOException {

    }

    @Override
    protected CompletableFuture<Void> doStoreTestEventAsync(StoredTestEvent event) {
        return null;
    }

    @Override
    protected void doUpdateParentTestEvents(StoredTestEvent event) throws IOException {

    }

    @Override
    protected CompletableFuture<Void> doUpdateParentTestEventsAsync(StoredTestEvent event) {
        return null;
    }

    @Override
    protected void doStoreTestEventMessagesLink(StoredTestEventId eventId, StoredTestEventId batchId, Collection<StoredMessageId> messageIds) throws IOException {

    }

    @Override
    protected CompletableFuture<Void> doStoreTestEventMessagesLinkAsync(StoredTestEventId eventId, StoredTestEventId batchId, Collection<StoredMessageId> messageIds) {
        return null;
    }

    @Override
    protected StoredMessage doGetMessage(StoredMessageId id) throws IOException {
        return null;
    }

    @Override
    protected CompletableFuture<StoredMessage> doGetMessageAsync(StoredMessageId id) {
        return null;
    }

    @Override
    protected Collection<StoredMessage> doGetMessageBatch(StoredMessageId id) throws IOException {
        return null;
    }

    @Override
    protected CompletableFuture<Collection<StoredMessage>> doGetMessageBatchAsync(StoredMessageId id) {
        return null;
    }

    @Override
    protected StoredMessage doGetProcessedMessage(StoredMessageId id) throws IOException {
        return null;
    }

    @Override
    protected CompletableFuture<StoredMessage> doGetProcessedMessageAsync(StoredMessageId id) {
        return null;
    }

    @Override
    protected long doGetLastMessageIndex(String streamName, Direction direction) throws IOException {
        return 0;
    }

    @Override
    protected long doGetLastProcessedMessageIndex(String streamName, Direction direction) throws IOException {
        return 0;
    }

    @Override
    protected StoredMessageId doGetNearestMessageId(String streamName, Direction direction, Instant timestamp, TimeRelation timeRelation) throws IOException {
        return null;
    }

    @Override
    protected CompletableFuture<StoredMessageId> doGetNearestMessageIdAsync(String streamName, Direction direction, Instant timestamp, TimeRelation timeRelation) {
        return null;
    }

    @Override
    protected StoredTestEventWrapper doGetTestEvent(StoredTestEventId id) throws IOException {
        return null;
    }

    @Override
    protected CompletableFuture<StoredTestEventWrapper> doGetTestEventAsync(StoredTestEventId ids) {
        return null;
    }

    @Override
    protected Iterable<StoredTestEventWrapper> doGetCompleteTestEvents(Set<StoredTestEventId> ids) throws IOException {
        return null;
    }

    @Override
    protected CompletableFuture<Iterable<StoredTestEventWrapper>> doGetCompleteTestEventsAsync(Set<StoredTestEventId> id) {
        return null;
    }

    @Override
    protected Collection<String> doGetStreams() throws IOException {
        return null;
    }

    @Override
    protected Collection<Instant> doGetRootTestEventsDates() throws IOException {
        return null;
    }

    @Override
    protected Collection<Instant> doGetTestEventsDates(StoredTestEventId parentId) throws IOException {
        return null;
    }

    @Override
    protected void doUpdateEventStatus(StoredTestEventWrapper event, boolean success) throws IOException {

    }

    @Override
    protected CompletableFuture<Void> doUpdateEventStatusAsync(StoredTestEventWrapper event, boolean success) {
        return null;
    }

    @Override
    public CradleObjectsFactory getObjectsFactory() {
        return null;
    }

    @Override
    public TestEventsMessagesLinker getTestEventsMessagesLinker() {
        return null;
    }

    @Override
    public IntervalsWorker getIntervalsWorker() {
        return null;
    }

    @Override
    protected Iterable<StoredMessage> doGetMessages(StoredMessageFilter filter) throws IOException {
        return null;
    }

    @Override
    protected CompletableFuture<Iterable<StoredMessage>> doGetMessagesAsync(StoredMessageFilter filter) {
        return null;
    }

    @Override
    protected Iterable<StoredMessageBatch> doGetMessagesBatches(StoredMessageFilter filter) throws IOException {
        return null;
    }

    @Override
    protected CompletableFuture<Iterable<StoredMessageBatch>> doGetMessagesBatchesAsync(StoredMessageFilter filter) {
        return null;
    }

    @Override
    protected Iterable<StoredTestEventMetadata> doGetRootTestEvents(Instant from, Instant to) throws CradleStorageException, IOException {
        return null;
    }

    @Override
    protected CompletableFuture<Iterable<StoredTestEventMetadata>> doGetRootTestEventsAsync(Instant from, Instant to) throws CradleStorageException {
        return null;
    }

    @Override
    protected Iterable<StoredTestEventMetadata> doGetTestEvents(StoredTestEventId parentId, Instant from, Instant to) throws CradleStorageException, IOException {
        return null;
    }

    @Override
    protected CompletableFuture<Iterable<StoredTestEventMetadata>> doGetTestEventsAsync(StoredTestEventId parentId, Instant from, Instant to) throws CradleStorageException {
        return null;
    }

    @Override
    protected Iterable<StoredTestEventMetadata> doGetTestEvents(Instant from, Instant to) throws CradleStorageException, IOException {
        return null;
    }

    @Override
    protected CompletableFuture<Iterable<StoredTestEventMetadata>> doGetTestEventsAsync(Instant from, Instant to) throws CradleStorageException {
        return null;
    }
}
