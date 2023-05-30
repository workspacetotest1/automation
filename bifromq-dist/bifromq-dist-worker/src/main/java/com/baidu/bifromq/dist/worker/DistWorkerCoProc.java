/*
 * Copyright (c) 2023. Baidu, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.baidu.bifromq.dist.worker;

import static com.baidu.bifromq.basekv.utils.KeyRangeUtil.intersect;
import static com.baidu.bifromq.basekv.utils.KeyRangeUtil.isEmptyRange;
import static com.baidu.bifromq.baseutils.ThreadUtil.threadFactory;
import static com.baidu.bifromq.dist.entity.EntityUtil.isSubInfoKey;
import static com.baidu.bifromq.dist.entity.EntityUtil.parseInbox;
import static com.baidu.bifromq.dist.entity.EntityUtil.parseMatchRecord;
import static com.baidu.bifromq.dist.entity.EntityUtil.parseTopicFilter;
import static com.baidu.bifromq.dist.entity.EntityUtil.parseTrafficId;
import static com.baidu.bifromq.dist.entity.EntityUtil.trafficPrefix;
import static com.baidu.bifromq.dist.entity.EntityUtil.trafficUpperBound;
import static com.baidu.bifromq.dist.util.TopicUtil.isWildcardTopicFilter;
import static com.baidu.bifromq.sysprops.BifroMQSysProp.DIST_FAN_OUT_PARALLELISM;
import static com.baidu.bifromq.sysprops.BifroMQSysProp.DIST_LOAD_TRACKING_SECONDS;
import static com.baidu.bifromq.sysprops.BifroMQSysProp.DIST_MAX_RANGE_LOAD;
import static com.baidu.bifromq.sysprops.BifroMQSysProp.DIST_PARALLEL_FAN_OUT_SIZE;
import static com.baidu.bifromq.sysprops.BifroMQSysProp.DIST_SPLIT_KEY_EST_THRESHOLD;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.CompletableFuture.allOf;

import com.baidu.bifromq.basekv.proto.KVRangeId;
import com.baidu.bifromq.basekv.proto.LoadHint;
import com.baidu.bifromq.basekv.proto.Range;
import com.baidu.bifromq.basekv.store.api.IKVIterator;
import com.baidu.bifromq.basekv.store.api.IKVRangeCoProc;
import com.baidu.bifromq.basekv.store.api.IKVRangeReader;
import com.baidu.bifromq.basekv.store.api.IKVReader;
import com.baidu.bifromq.basekv.store.api.IKVWriter;
import com.baidu.bifromq.basekv.utils.KVRangeIdUtil;
import com.baidu.bifromq.basescheduler.IBatchCallScheduler;
import com.baidu.bifromq.dist.client.ClearResult;
import com.baidu.bifromq.dist.client.IDistClient;
import com.baidu.bifromq.dist.entity.EntityUtil;
import com.baidu.bifromq.dist.entity.GroupMatching;
import com.baidu.bifromq.dist.entity.Inbox;
import com.baidu.bifromq.dist.entity.Matching;
import com.baidu.bifromq.dist.entity.NormalMatching;
import com.baidu.bifromq.dist.rpc.proto.AddTopicFilter;
import com.baidu.bifromq.dist.rpc.proto.AddTopicFilterReply;
import com.baidu.bifromq.dist.rpc.proto.BatchDist;
import com.baidu.bifromq.dist.rpc.proto.BatchDistReply;
import com.baidu.bifromq.dist.rpc.proto.ClearSubInfo;
import com.baidu.bifromq.dist.rpc.proto.ClearSubInfoReply;
import com.baidu.bifromq.dist.rpc.proto.CollectMetricsReply;
import com.baidu.bifromq.dist.rpc.proto.DeleteMatchRecord;
import com.baidu.bifromq.dist.rpc.proto.DeleteMatchRecordReply;
import com.baidu.bifromq.dist.rpc.proto.DistPack;
import com.baidu.bifromq.dist.rpc.proto.DistServiceROCoProcInput;
import com.baidu.bifromq.dist.rpc.proto.DistServiceROCoProcOutput;
import com.baidu.bifromq.dist.rpc.proto.DistServiceRWCoProcInput;
import com.baidu.bifromq.dist.rpc.proto.DistServiceRWCoProcOutput;
import com.baidu.bifromq.dist.rpc.proto.GCReply;
import com.baidu.bifromq.dist.rpc.proto.GCRequest;
import com.baidu.bifromq.dist.rpc.proto.GroupMatchRecord;
import com.baidu.bifromq.dist.rpc.proto.InboxSubInfo;
import com.baidu.bifromq.dist.rpc.proto.InsertMatchRecord;
import com.baidu.bifromq.dist.rpc.proto.InsertMatchRecordReply;
import com.baidu.bifromq.dist.rpc.proto.JoinMatchGroup;
import com.baidu.bifromq.dist.rpc.proto.JoinMatchGroupReply;
import com.baidu.bifromq.dist.rpc.proto.LeaveMatchGroup;
import com.baidu.bifromq.dist.rpc.proto.LeaveMatchGroupReply;
import com.baidu.bifromq.dist.rpc.proto.MatchRecord;
import com.baidu.bifromq.dist.rpc.proto.RemoveTopicFilter;
import com.baidu.bifromq.dist.rpc.proto.RemoveTopicFilterReply;
import com.baidu.bifromq.dist.rpc.proto.TopicFanout;
import com.baidu.bifromq.dist.rpc.proto.UpdateReply;
import com.baidu.bifromq.dist.rpc.proto.UpdateRequest;
import com.baidu.bifromq.dist.worker.scheduler.InboxWriteRequest;
import com.baidu.bifromq.plugin.inboxbroker.HasResult;
import com.baidu.bifromq.plugin.inboxbroker.IInboxBrokerManager;
import com.baidu.bifromq.type.ClientInfo;
import com.baidu.bifromq.type.QoS;
import com.baidu.bifromq.type.SubInfo;
import com.baidu.bifromq.type.SysClientInfo;
import com.baidu.bifromq.type.TopicMessagePack;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class DistWorkerCoProc implements IKVRangeCoProc {
    private static final int SEND_BATCH_SIZE = Math.max(1, DIST_PARALLEL_FAN_OUT_SIZE.get());
    private final KVRangeId id;
    private final IDistClient distClient;
    private final IInboxBrokerManager inboxBrokerManager;
    private final IBatchCallScheduler<InboxWriteRequest, Void> scheduler;
    private final SubscriptionCache routeCache;
    private final LoadEstimator loadEstimator;
    private final Gauge loadGauge;
    private final ExecutorService fanoutExecutor;

    public DistWorkerCoProc(KVRangeId id,
                            Supplier<IKVRangeReader> readClientProvider,
                            IDistClient distClient,
                            IInboxBrokerManager messageReceiverManager,
                            IBatchCallScheduler<InboxWriteRequest, Void> scheduler,
                            Executor matchExecutor) {
        this.id = id;
        this.distClient = distClient;
        this.inboxBrokerManager = messageReceiverManager;
        this.scheduler = scheduler;
        this.loadEstimator = new LoadEstimator(DIST_MAX_RANGE_LOAD.get(), DIST_SPLIT_KEY_EST_THRESHOLD.get(),
            DIST_LOAD_TRACKING_SECONDS.get());
        this.routeCache = new SubscriptionCache(id, readClientProvider, matchExecutor, loadEstimator);
        loadGauge = Gauge.builder("dist.worker.load", () -> loadEstimator.estimate().getLoad())
            .tags("id", KVRangeIdUtil.toString(id))
            .register(Metrics.globalRegistry);
        fanoutExecutor = ExecutorServiceMetrics.monitor(Metrics.globalRegistry,
            new ThreadPoolExecutor(DIST_FAN_OUT_PARALLELISM.get(),
                DIST_FAN_OUT_PARALLELISM.get(), 0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1000),
                threadFactory("fanout-executor-%d")),
            "fanout-executor");
    }

    @Override
    public LoadHint get() {
        return loadEstimator.estimate();
    }

    @Override
    public CompletableFuture<ByteString> query(ByteString input, IKVReader reader) {
        try {
            CodedInputStream cis = input.newCodedInput();
            cis.enableAliasing(true);
            DistServiceROCoProcInput coProcInput = DistServiceROCoProcInput.parseFrom(cis);
            switch (coProcInput.getInputCase()) {
                case DIST: {
                    return dist(coProcInput.getDist(), reader)
                        .thenApply(v -> DistServiceROCoProcOutput.newBuilder()
                            .setDistReply(v).build().toByteString());
                }
                case GCREQUEST: {
                    return gc(coProcInput.getGcRequest(), reader)
                        .thenApply(v -> DistServiceROCoProcOutput.newBuilder()
                            .setGcReply(v).build().toByteString());
                }
                case COLLECTMETRICSREQUEST: {
                    return collect(coProcInput.getCollectMetricsRequest().getReqId(), reader)
                        .thenApply(v -> DistServiceROCoProcOutput.newBuilder()
                            .setCollectMetricsReply(v).build().toByteString());
                }
                default:
                    log.error("Unknown co proc type {}", coProcInput.getInputCase());
                    CompletableFuture<ByteString> f = new CompletableFuture();
                    f.completeExceptionally(
                        new IllegalStateException("Unknown co proc type " + coProcInput.getInputCase()));
                    return f;
            }
        } catch (Throwable e) {
            log.error("Unable to parse ro co-proc", e);
            CompletableFuture<ByteString> f = new CompletableFuture();
            f.completeExceptionally(new IllegalStateException("Unable to parse ro co-proc", e));
            return f;
        }
    }

    @SneakyThrows
    @Override
    public Supplier<ByteString> mutate(ByteString input, IKVReader reader, IKVWriter writer) {
        CodedInputStream cis = input.newCodedInput();
        cis.enableAliasing(true);
        DistServiceRWCoProcInput coProcInput = DistServiceRWCoProcInput.parseFrom(cis);
        log.trace("Receive rw co-proc request\n{}", coProcInput);
        UpdateRequest request = coProcInput.getUpdateRequest();
        Set<String> touchedTraffics = Sets.newHashSet();
        Set<ScopedTopic> touchedTopics = Sets.newHashSet();
        ByteString output = DistServiceRWCoProcOutput.newBuilder()
            .setUpdateReply(batchUpdate(request, reader, writer, touchedTraffics, touchedTopics))
            .build().toByteString();
        return () -> {
            touchedTopics.forEach(routeCache::invalidate);
            touchedTraffics.forEach(routeCache::touch);
            return output;
        };
    }

    public void close() {
        routeCache.close();
        fanoutExecutor.shutdown();
        Metrics.globalRegistry.remove(loadGauge);
    }

    private UpdateReply batchUpdate(UpdateRequest request, IKVReader reader, IKVWriter writer,
                                    Set<String> touchedTraffics, Set<ScopedTopic> touchedTopics) {
        UpdateReply.Builder replyBuilder = UpdateReply.newBuilder().setReqId(request.getReqId());
        if (request.hasAddTopicFilter()) {
            replyBuilder.setAddTopicFilter(
                addTopicFilter(request.getAddTopicFilter(), reader, writer));
        }
        if (request.hasRemoveTopicFilter()) {
            replyBuilder.setRemoveTopicFilter(
                removeTopicFilter(request.getRemoveTopicFilter(), reader, writer));
        }
        if (request.hasInsertMatchRecord()) {
            replyBuilder.setInsertMatchRecord(
                insertMatchRecord(request.getInsertMatchRecord(), reader, writer, touchedTraffics, touchedTopics));
        }
        if (request.hasDeleteMatchRecord()) {
            replyBuilder.setDeleteMatchRecord(
                deleteMatchRecord(request.getDeleteMatchRecord(), reader, writer, touchedTraffics, touchedTopics));
        }
        if (request.hasJoinMatchGroup()) {
            replyBuilder.setJoinMatchGroup(
                joinMatchGroup(request.getJoinMatchGroup(), reader, writer, touchedTraffics, touchedTopics));
        }
        if (request.hasLeaveMatchGroup()) {
            replyBuilder.setLeaveMatchGroup(
                leaveMatchGroup(request.getLeaveMatchGroup(), reader, writer, touchedTraffics, touchedTopics));
        }
        if (request.hasClearSubInfo()) {
            replyBuilder.setClearSubInfo(clearSubInfo(request.getClearSubInfo(), reader, writer));
        }
        return replyBuilder.build();
    }

    private AddTopicFilterReply addTopicFilter(AddTopicFilter request, IKVReader reader, IKVWriter writer) {
        AddTopicFilterReply.Builder replyBuilder = AddTopicFilterReply.newBuilder();
        for (String subInfoKeyUtf8 : request.getTopicFilterMap().keySet()) {
            ByteString key = ByteString.copyFromUtf8(subInfoKeyUtf8);
            InboxSubInfo subInfo = reader.get(key).map(b -> {
                try {
                    return InboxSubInfo.parseFrom(b);
                } catch (InvalidProtocolBufferException e) {
                    log.error("Unable to parse SubInfo", e);
                    return InboxSubInfo.getDefaultInstance();
                }
            }).orElse(InboxSubInfo.getDefaultInstance());
            loadEstimator.track(key, LoadUnits.KEY_GET);

            AddTopicFilterReply.Results.Builder subResultBuilder = AddTopicFilterReply.Results.newBuilder();
            InboxSubInfo newTopicFilters = request.getTopicFilterMap().get(subInfoKeyUtf8);
            boolean update = false;
            for (String topicFilter : newTopicFilters.getTopicFiltersMap().keySet()) {
                QoS subQoS = newTopicFilters.getTopicFiltersMap().get(topicFilter);
                if (subInfo.getTopicFiltersMap().get(topicFilter) != subQoS) {
                    // TODO: make it configurable?
                    if (subInfo.getTopicFiltersCount() < 100) {
                        subInfo = subInfo.toBuilder().putTopicFilters(topicFilter, subQoS).build();
                        subResultBuilder.putResults(topicFilter, AddTopicFilterReply.Result.OK);
                        update = true;
                    } else {
                        subResultBuilder.putResults(topicFilter, AddTopicFilterReply.Result.ExceedQuota);
                    }
                } else {
                    subResultBuilder.putResults(topicFilter, AddTopicFilterReply.Result.OK);
                }
            }
            if (update) {
                loadEstimator.track(key, LoadUnits.KEY_PUT);
                writer.put(key, subInfo.toByteString());
            }
            replyBuilder.putResult(subInfoKeyUtf8, subResultBuilder.build());
        }
        return replyBuilder.build();
    }

    private RemoveTopicFilterReply removeTopicFilter(RemoveTopicFilter request, IKVReader reader, IKVWriter writer) {
        RemoveTopicFilterReply.Builder replyBuilder = RemoveTopicFilterReply.newBuilder();
        for (String subInfoKeyUtf8 : request.getTopicFilterMap().keySet()) {
            ByteString key = ByteString.copyFromUtf8(subInfoKeyUtf8);
            boolean ok = false;
            Optional<InboxSubInfo> subInfo = reader.get(key).map(b -> {
                try {
                    return InboxSubInfo.parseFrom(b);
                } catch (InvalidProtocolBufferException e) {
                    log.error("Unable to parse SubInfo", e);
                    return null;
                }
            });
            loadEstimator.track(key, LoadUnits.KEY_GET);

            InboxSubInfo.Builder subInfoBuilder = subInfo.orElse(InboxSubInfo.getDefaultInstance()).toBuilder();
            RemoveTopicFilterReply.Results.Builder resultBuilder = RemoveTopicFilterReply.Results.newBuilder();
            for (String topicFilter : request.getTopicFilterMap().get(subInfoKeyUtf8).getTopicFilterList()) {
                if (subInfoBuilder.getTopicFiltersMap().containsKey(topicFilter)) {
                    subInfoBuilder.removeTopicFilters(topicFilter);
                    ok = true;
                    resultBuilder.putResult(topicFilter, RemoveTopicFilterReply.Result.Exist);
                } else {
                    resultBuilder.putResult(topicFilter, RemoveTopicFilterReply.Result.NonExist);
                }
            }

            if (ok) {
                if (subInfoBuilder.getTopicFiltersCount() == 0) {
                    loadEstimator.track(key, LoadUnits.KEY_DEL);
                    writer.delete(key);
                } else {
                    loadEstimator.track(key, LoadUnits.KEY_PUT);
                    writer.put(key, subInfoBuilder.build().toByteString());
                }
            }
            replyBuilder.putResult(subInfoKeyUtf8, resultBuilder.build());
        }
        return replyBuilder.build();
    }

    private InsertMatchRecordReply insertMatchRecord(InsertMatchRecord request, IKVReader reader, IKVWriter writer,
                                                     Set<String> touchedTraffics, Set<ScopedTopic> touchedTopics) {
        InsertMatchRecordReply.Builder replyBuilder = InsertMatchRecordReply.newBuilder();
        for (String matchRecordKey : request.getRecordMap().keySet()) {
            ByteString key = ByteString.copyFromUtf8(matchRecordKey);
            QoS subQoS = request.getRecordMap().get(matchRecordKey);
            switch (subQoS) {
                case AT_MOST_ONCE:
                case AT_LEAST_ONCE:
                case EXACTLY_ONCE:
                    if (!reader.exist(key)) {
                        writer.put(key, MatchRecord.newBuilder().setNormal(subQoS).build().toByteString());
                        loadEstimator.track(key, LoadUnits.KEY_PUT);

                        String trafficId = parseTrafficId(matchRecordKey);
                        String topicFilter = parseTopicFilter(matchRecordKey);
                        if (isWildcardTopicFilter(topicFilter)) {
                            touchedTraffics.add(parseTrafficId(matchRecordKey));
                        } else {
                            touchedTopics.add(ScopedTopic.builder()
                                .trafficId(trafficId)
                                .topic(topicFilter)
                                .range(reader.range())
                                .build());
                        }
                    }
                    loadEstimator.track(key, LoadUnits.KEY_EXIST);
                    break;
            }
        }
        return replyBuilder.build();
    }

    private JoinMatchGroupReply joinMatchGroup(JoinMatchGroup request, IKVReader reader, IKVWriter writer,
                                               Set<String> touchedTraffics, Set<ScopedTopic> touchedTopics) {
        JoinMatchGroupReply.Builder replyBuilder = JoinMatchGroupReply.newBuilder();
        for (String matchRecordKeyUtf8 : request.getRecordMap().keySet()) {
            ByteString matchRecordKey = ByteString.copyFromUtf8(matchRecordKeyUtf8);
            GroupMatchRecord newMembers = request.getRecordMap().get(matchRecordKeyUtf8);
            GroupMatchRecord.Builder matchGroup = reader.get(matchRecordKey)
                .map(b -> {
                    try {
                        return MatchRecord.parseFrom(b).getGroup();
                    } catch (InvalidProtocolBufferException e) {
                        log.error("Unable to parse GroupMatchRecord", e);
                        return GroupMatchRecord.getDefaultInstance();
                    }
                })
                .orElse(GroupMatchRecord.getDefaultInstance()).toBuilder();
            loadEstimator.track(matchRecordKey, LoadUnits.KEY_GET);

            JoinMatchGroupReply.Results.Builder resultBuilder = JoinMatchGroupReply.Results.newBuilder();
            boolean updated = false;
            for (String qInboxId : newMembers.getEntryMap().keySet()) {
                QoS subQoS = newMembers.getEntryMap().get(qInboxId);
                // TODO: max members in shared group
                if (!matchGroup.containsEntry(qInboxId) && matchGroup.getEntryCount() < 200) {
                    matchGroup.putEntry(qInboxId, subQoS);
                    resultBuilder.putResult(qInboxId, JoinMatchGroupReply.Result.OK);
                    updated = true;
                } else {
                    resultBuilder.putResult(qInboxId, JoinMatchGroupReply.Result.ExceedLimit);
                }
            }
            if (updated) {
                writer.put(matchRecordKey, MatchRecord.newBuilder().setGroup(matchGroup).build().toByteString());
                loadEstimator.track(matchRecordKey, LoadUnits.KEY_PUT);

                String trafficId = parseTrafficId(matchRecordKey);
                String topicFilter = parseTopicFilter(matchRecordKeyUtf8);
                if (isWildcardTopicFilter(topicFilter)) {
                    touchedTraffics.add(parseTrafficId(matchRecordKey));
                } else {
                    touchedTopics.add(ScopedTopic.builder()
                        .trafficId(trafficId)
                        .topic(topicFilter)
                        .range(reader.range())
                        .build());
                }
            }
            replyBuilder.putResult(matchRecordKeyUtf8, resultBuilder.build());
        }
        return replyBuilder.build();
    }

    private DeleteMatchRecordReply deleteMatchRecord(DeleteMatchRecord request, IKVReader reader, IKVWriter writer,
                                                     Set<String> touchedTraffics, Set<ScopedTopic> touchedTopics) {
        for (String matchRecordKeyUtf8 : request.getMatchRecordKeyList()) {
            ByteString matchRecordKey = ByteString.copyFromUtf8(matchRecordKeyUtf8);
            Optional<ByteString> value = reader.get(matchRecordKey);
            loadEstimator.track(matchRecordKey, LoadUnits.KEY_GET);

            if (value.isPresent()) {
                writer.delete(matchRecordKey);
                loadEstimator.track(matchRecordKey, LoadUnits.KEY_DEL);

                String trafficId = parseTrafficId(matchRecordKey);
                String topicFilter = parseTopicFilter(matchRecordKeyUtf8);
                if (isWildcardTopicFilter(topicFilter)) {
                    touchedTraffics.add(parseTrafficId(matchRecordKey));
                } else {
                    touchedTopics.add(ScopedTopic.builder()
                        .trafficId(trafficId)
                        .topic(topicFilter)
                        .range(reader.range())
                        .build());
                }
            }
        }
        return DeleteMatchRecordReply.getDefaultInstance();
    }

    private LeaveMatchGroupReply leaveMatchGroup(LeaveMatchGroup request, IKVReader reader, IKVWriter writer,
                                                 Set<String> touchedTraffics, Set<ScopedTopic> touchedTopics) {
        for (String matchRecordKeyUtf8 : request.getRecordMap().keySet()) {
            ByteString matchRecordKey = ByteString.copyFromUtf8(matchRecordKeyUtf8);
            Optional<ByteString> value = reader.get(matchRecordKey);
            loadEstimator.track(matchRecordKey, LoadUnits.KEY_GET);

            if (value.isPresent()) {
                Matching matching = parseMatchRecord(matchRecordKey, value.get());
                assert matching instanceof GroupMatching;
                GroupMatching groupMatching = (GroupMatching) matching;
                Map<String, QoS> existing = Maps.newHashMap(groupMatching.inboxMap);
                for (String qualifiedInboxId : request.getRecordMap()
                    .get(matchRecordKeyUtf8)
                    .getQInboxIdList()) {
                    existing.remove(qualifiedInboxId);
                }
                if (existing.size() != groupMatching.inboxMap.size()) {
                    if (existing.isEmpty()) {
                        writer.delete(matchRecordKey);
                        loadEstimator.track(matchRecordKey, LoadUnits.KEY_DEL);
                    } else {
                        writer.put(matchRecordKey, MatchRecord.newBuilder()
                            .setGroup(GroupMatchRecord.newBuilder()
                                .putAllEntry(existing)
                                .build()).build()
                            .toByteString());
                        loadEstimator.track(matchRecordKey, LoadUnits.KEY_PUT);
                    }
                    String trafficId = parseTrafficId(matchRecordKey);
                    String topicFilter = parseTopicFilter(matchRecordKeyUtf8);
                    if (isWildcardTopicFilter(topicFilter)) {
                        touchedTraffics.add(parseTrafficId(matchRecordKey));
                    } else {
                        touchedTopics.add(ScopedTopic.builder()
                            .trafficId(trafficId)
                            .topic(topicFilter)
                            .range(reader.range())
                            .build());
                    }
                }
            }
        }
        return LeaveMatchGroupReply.getDefaultInstance();
    }

    private ClearSubInfoReply clearSubInfo(ClearSubInfo request, IKVReader reader, IKVWriter writer) {
        ClearSubInfoReply.Builder replyBuilder = ClearSubInfoReply.newBuilder();
        for (ByteString subInfoKey : request.getSubInfoKeyList()) {
            Optional<InboxSubInfo> subInfo = reader.get(subInfoKey).map(b -> {
                try {
                    return InboxSubInfo.parseFrom(b);
                } catch (InvalidProtocolBufferException e) {
                    return null;
                }
            });
            if (subInfo.isPresent()) {
                writer.delete(subInfoKey);
            }
            replyBuilder.addSubInfo(subInfo.orElse(InboxSubInfo.getDefaultInstance()));
        }
        return replyBuilder.build();
    }

    private CompletableFuture<BatchDistReply> dist(BatchDist request, IKVReader reader) {
        List<DistPack> distPackList = request.getDistPackList();
        if (distPackList.isEmpty()) {
            return CompletableFuture.completedFuture(BatchDistReply.newBuilder()
                .setReqId(request.getReqId())
                .build());
        }
        List<CompletableFuture<Map<String, Map<String, Integer>>>> distFanOutFutures = new ArrayList<>();
        for (DistPack distPack : distPackList) {
            String trafficId = distPack.getTrafficId();
            Range range = intersect(Range.newBuilder()
                .setStartKey(EntityUtil.matchRecordKeyPrefix(trafficId))
                .setEndKey(trafficUpperBound(trafficId))
                .build(), reader.range());
            if (isEmptyRange(range)) {
                continue;
            }
            for (TopicMessagePack topicMsgPack : distPack.getMsgPackList()) {
                String topic = topicMsgPack.getTopic();
                ScopedTopic scopedTopic = ScopedTopic.builder()
                    .trafficId(trafficId)
                    .topic(topic)
                    .range(reader.range())
                    .build();
                Map<ClientInfo, TopicMessagePack.SenderMessagePack> senderMsgPackMap =
                    topicMsgPack.getMessageList().stream().collect(Collectors.toMap(
                        TopicMessagePack.SenderMessagePack::getSender, e -> e));
                distFanOutFutures.add(routeCache.get(scopedTopic, senderMsgPackMap.keySet())
                    .thenApply(v -> {
                        if (v.size() > SEND_BATCH_SIZE) {
                            List<Map<NormalMatching, Set<ClientInfo>>> subRoutesList = new ArrayList<>();
                            Map<NormalMatching, Set<ClientInfo>> subRoutesMap = new HashMap<>();
                            for (Map.Entry<NormalMatching, Set<ClientInfo>> entry : v.entrySet()) {
                                subRoutesMap.put(entry.getKey(), entry.getValue());
                                if (subRoutesMap.size() == SEND_BATCH_SIZE) {
                                    subRoutesList.add(subRoutesMap);
                                    subRoutesMap = new HashMap<>();
                                }
                            }
                            subRoutesList.add(subRoutesMap);
                            subRoutesList.forEach(subRoutes -> CompletableFuture.runAsync(
                                () -> send(subRoutes, topicMsgPack, senderMsgPackMap), fanoutExecutor));
                        } else {
                            send(v, topicMsgPack, senderMsgPackMap);
                        }
                        return singletonMap(trafficId, singletonMap(topic, v.size()));
                    }));
            }
        }
        return CompletableFuture.allOf(distFanOutFutures.toArray(CompletableFuture[]::new))
            .thenApply(v -> distFanOutFutures.stream().map(CompletableFuture::join).collect(Collectors.toList()))
            .thenApply(v -> {
                // trafficId -> topic -> fanOut
                Map<String, Map<String, Integer>> trafficFanout = new HashMap<>();
                v.forEach(fanoutMap -> fanoutMap.forEach((trafficId, topicFanout) ->
                    trafficFanout.computeIfAbsent(trafficId, k -> new HashMap<>()).putAll(topicFanout)));
                return BatchDistReply.newBuilder()
                    .setReqId(request.getReqId())
                    .putAllResult(Maps.transformValues(trafficFanout,
                        f -> TopicFanout.newBuilder().putAllFanout(f).build()))
                    .build();
            });
    }

    private void send(Map<NormalMatching, Set<ClientInfo>> routes,
                      TopicMessagePack topicMsgPack,
                      Map<ClientInfo, TopicMessagePack.SenderMessagePack> senderMsgPackMap) {
        routes.forEach((route, senders) -> {
            if (senders.size() == senderMsgPackMap.size()) {
                send(topicMsgPack, route);
            } else {
                // ordered share sub
                TopicMessagePack subMsgPack = TopicMessagePack.newBuilder()
                    .setTopic(topicMsgPack.getTopic())
                    .addAllMessage(Maps.filterKeys(senderMsgPackMap, senders::contains).values())
                    .build();
                send(subMsgPack, route);
            }
        });
    }

    private void send(TopicMessagePack msgPack, NormalMatching matched) {
        SubInfo sub = matched.subInfo;
        if (!inboxBrokerManager.hasBroker(matched.brokerId)) {
            log.error("Invalid inbox broker[{}] for sub[inboxId={}, qos={}, topicFilter={}]",
                matched.brokerId,
                sub.getInboxId(),
                sub.getSubQoS(),
                sub.getTopicFilter());
            return;
        }
        scheduler.schedule(new InboxWriteRequest(matched, msgPack));
    }

    private CompletableFuture<GCReply> gc(GCRequest request, IKVReader reader) {
        List<CompletableFuture<ClearResult>> clearFutures = new ArrayList<>();
        IKVIterator itr = reader.iterator();
        for (itr.seekToFirst(); itr.isValid(); ) {
            String trafficId = parseTrafficId(itr.key());
            if (isSubInfoKey(itr.key())) {
                Inbox inbox = parseInbox(itr.key());
                clearFutures.add(inboxBrokerManager
                    .hasInbox(request.getReqId(), trafficId, inbox.inboxId, inbox.inboxGroupKey, inbox.broker)
                    .thenCompose(reply -> {
                        if (reply.type() == HasResult.Type.NO) {
                            return distClient.clear(request.getReqId(), inbox.inboxId, inbox.inboxGroupKey,
                                inbox.broker,
                                ClientInfo.newBuilder()
                                    .setTrafficId(trafficId)
                                    .setUserId("dist_gc-" + KVRangeIdUtil.toString(id))
                                    .setSysClientInfo(SysClientInfo
                                        .newBuilder()
                                        .setType("distservice")
                                        .build())
                                    .build());
                        }
                        return CompletableFuture.completedFuture(ClearResult.OK);
                    }));
            }
            itr.seek(trafficUpperBound(trafficId));
        }
        return allOf(clearFutures.toArray(new CompletableFuture[0]))
            .handle((v, e) -> GCReply.newBuilder().setReqId(request.getReqId()).build());
    }

    private CompletableFuture<CollectMetricsReply> collect(long reqId, IKVReader reader) {
        CollectMetricsReply.Builder builder = CollectMetricsReply.newBuilder().setReqId(reqId);
        IKVIterator itr = reader.iterator();
        for (itr.seekToFirst(); itr.isValid(); ) {
            String trafficId = parseTrafficId(itr.key());
            builder.putUsedSpaces(trafficId,
                reader.size(intersect(reader.range(), Range.newBuilder()
                    .setStartKey(trafficPrefix(trafficId))
                    .setEndKey(trafficUpperBound(trafficId))
                    .build())));
            itr.seek(trafficUpperBound(trafficId));
        }
        return CompletableFuture.completedFuture(builder.build());
    }
}
