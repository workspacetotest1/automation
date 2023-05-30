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

package com.baidu.bifromq.basekv.store.range;

import static com.baidu.bifromq.basekv.Constants.EMPTY_RANGE;
import static com.baidu.bifromq.basekv.Constants.FULL_RANGE;
import static com.baidu.bifromq.basekv.store.range.KVRangeKeys.dataKey;
import static com.baidu.bifromq.basekv.store.range.KVRangeKeys.lastAppliedIndexKey;
import static com.baidu.bifromq.basekv.store.range.KVRangeKeys.rangeKey;
import static com.baidu.bifromq.basekv.store.range.KVRangeKeys.stateKey;
import static com.baidu.bifromq.basekv.store.range.KVRangeKeys.verKey;
import static com.baidu.bifromq.basekv.store.util.KVUtil.toByteString;
import static com.baidu.bifromq.basekv.store.util.KVUtil.toByteStringNativeOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baidu.bifromq.basekv.localengine.IKVEngine;
import com.baidu.bifromq.basekv.localengine.IKVEngineIterator;
import com.baidu.bifromq.basekv.proto.KVRangeId;
import com.baidu.bifromq.basekv.proto.Range;
import com.baidu.bifromq.basekv.proto.State;
import com.baidu.bifromq.basekv.utils.KVRangeIdUtil;
import com.google.protobuf.ByteString;
import io.reactivex.rxjava3.core.Maybe;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class KVRangeMetadataAccessorTest {
    @Mock
    IKVEngine engine;

    @Mock
    IKVEngineIterator itr;

    @Test
    public void initWithNoData() {
        KVRangeId id = KVRangeIdUtil.generate();
        when(engine.get(anyString(), any(ByteString.class))).thenReturn(Optional.empty());
        try {
            KVRangeMetadataAccessor metadata = new KVRangeMetadataAccessor(id, engine);
            assertEquals(-1L, metadata.version());
            assertEquals(-1, metadata.lastAppliedIndex());
            assertEquals(EMPTY_RANGE, metadata.range());
            Maybe<IKVRangeState.KVRangeMeta> metaMayBe = metadata.source().firstElement();
            metadata.destroy(true);
            assertNull(metaMayBe.blockingGet());
        } catch (Throwable e) {
            fail();
        }
    }

    @Test
    public void initWithCompleteData() {
        KVRangeId id = KVRangeIdUtil.generate();
        int keyRangeId = 1;
        long ver = 10;
        Range range = FULL_RANGE;
        Range dataBound = KVRangeKeys.dataBound(range);
        long lastAppliedIndex = 10;
        State state = State.newBuilder().setType(State.StateType.Normal).build();

        when(engine.registerKeyRange(IKVEngine.DEFAULT_NS, dataBound.getStartKey(), dataBound.getEndKey()))
            .thenReturn(keyRangeId);
        when(engine.get(IKVEngine.DEFAULT_NS, verKey(id))).thenReturn(Optional.of(toByteStringNativeOrder(ver)));
        when(engine.get(IKVEngine.DEFAULT_NS, rangeKey(id))).thenReturn(Optional.of(range.toByteString()));
        when(engine.get(IKVEngine.DEFAULT_NS, lastAppliedIndexKey(id)))
            .thenReturn(Optional.of(toByteString(lastAppliedIndex)));
        when(engine.get(IKVEngine.DEFAULT_NS, stateKey(id))).thenReturn(Optional.of(state.toByteString()));

        KVRangeMetadataAccessor metadata = new KVRangeMetadataAccessor(id, engine);
        assertEquals(ver, metadata.version());
        assertEquals(range, metadata.range());
        assertEquals(lastAppliedIndex, metadata.lastAppliedIndex());
        assertEquals(state, metadata.state());
        assertEquals(keyRangeId, metadata.dataBoundId());

        IKVRangeState.KVRangeMeta meta = metadata.source().blockingFirst();
        assertEquals(ver, meta.ver);
        assertEquals(range, meta.range);
        assertEquals(state, meta.state);
    }

    @Test
    public void lastAppliedIndex() {
        KVRangeId id = KVRangeIdUtil.generate();
        int keyRangeId = 1;
        long ver = 10;
        Range range = FULL_RANGE;
        Range dataBound = KVRangeKeys.dataBound(range);
        long lastAppliedIndex = 10;
        State state = State.newBuilder().setType(State.StateType.Normal).build();

        when(engine.registerKeyRange(IKVEngine.DEFAULT_NS, dataBound.getStartKey(), dataBound.getEndKey()))
            .thenReturn(keyRangeId);
        when(engine.get(IKVEngine.DEFAULT_NS, verKey(id))).thenReturn(Optional.of(toByteStringNativeOrder(ver)));
        when(engine.get(IKVEngine.DEFAULT_NS, rangeKey(id))).thenReturn(Optional.of(range.toByteString()));
        when(engine.get(IKVEngine.DEFAULT_NS, lastAppliedIndexKey(id)))
            .thenReturn(Optional.of(toByteString(lastAppliedIndex)));
        when(engine.get(IKVEngine.DEFAULT_NS, stateKey(id))).thenReturn(Optional.of(state.toByteString()));

        KVRangeMetadataAccessor metadata = new KVRangeMetadataAccessor(id, engine);
        assertEquals(lastAppliedIndex, metadata.lastAppliedIndex());

        lastAppliedIndex = 11;
        when(engine.get(IKVEngine.DEFAULT_NS, lastAppliedIndexKey(id)))
            .thenReturn(Optional.of(toByteString(lastAppliedIndex)));
        assertEquals(lastAppliedIndex, metadata.lastAppliedIndex());
    }

    @Test
    public void loadRangeWhenVersionIsZero() {
        KVRangeId id = KVRangeIdUtil.generate();
        int keyRangeId = 1;
        long ver = 0;
        Range range = EMPTY_RANGE;
        Range dataBound = KVRangeKeys.dataBound(range);
        long lastAppliedIndex = 0;
        State state = State.newBuilder().setType(State.StateType.Normal).build();

        when(engine.registerKeyRange(IKVEngine.DEFAULT_NS, dataBound.getStartKey(), dataBound.getEndKey()))
            .thenReturn(keyRangeId);
        when(engine.get(IKVEngine.DEFAULT_NS, verKey(id))).thenReturn(Optional.of(toByteStringNativeOrder(ver)));
        when(engine.get(IKVEngine.DEFAULT_NS, rangeKey(id))).thenReturn(Optional.of(range.toByteString()));
        when(engine.get(IKVEngine.DEFAULT_NS, lastAppliedIndexKey(id)))
            .thenReturn(Optional.of(toByteString(lastAppliedIndex)));
        when(engine.get(IKVEngine.DEFAULT_NS, stateKey(id))).thenReturn(Optional.of(state.toByteString()));

        KVRangeMetadataAccessor metadata = new KVRangeMetadataAccessor(id, engine);
        IKVRangeState.KVRangeMeta meta = metadata.source().blockingFirst();
        // version upgrade to 2;
        long newVer = 2;
        int newKeyRangeId = 2;
        Range newRange = Range.newBuilder()
            .setStartKey(ByteString.copyFromUtf8("a"))
            .setEndKey(ByteString.copyFromUtf8("b"))
            .build();
        Range newBound = KVRangeKeys.dataBound(newRange);

        when(engine.registerKeyRange(IKVEngine.DEFAULT_NS, newBound.getStartKey(), newBound.getEndKey()))
            .thenReturn(newKeyRangeId);
        when(engine.get(IKVEngine.DEFAULT_NS, verKey(id))).thenReturn(Optional.of(toByteStringNativeOrder(newVer)));
        when(engine.get(IKVEngine.DEFAULT_NS, rangeKey(id))).thenReturn(Optional.of(newRange.toByteString()));

        metadata.refresh();
        IKVRangeState.KVRangeMeta newMeta = metadata.source().blockingFirst();
        assertEquals(newVer, metadata.version());
        assertEquals(newRange, metadata.range());
        assertEquals(lastAppliedIndex, metadata.lastAppliedIndex());
        assertEquals(newKeyRangeId, metadata.dataBoundId());
        assertNotEquals(meta, newMeta);
    }

    @Test
    public void refreshWithNoRangeChange() {
        KVRangeId id = KVRangeIdUtil.generate();
        int keyRangeId = 1;
        long ver = 10;
        Range range = FULL_RANGE;
        Range dataBound = KVRangeKeys.dataBound(range);
        long lastAppliedIndex = 10;
        State state = State.newBuilder().setType(State.StateType.Normal).build();

        when(engine.registerKeyRange(IKVEngine.DEFAULT_NS, dataBound.getStartKey(), dataBound.getEndKey()))
            .thenReturn(keyRangeId);
        when(engine.get(IKVEngine.DEFAULT_NS, verKey(id))).thenReturn(Optional.of(toByteStringNativeOrder(ver)));
        when(engine.get(IKVEngine.DEFAULT_NS, rangeKey(id))).thenReturn(Optional.of(range.toByteString()));
        when(engine.get(IKVEngine.DEFAULT_NS, lastAppliedIndexKey(id)))
            .thenReturn(Optional.of(toByteString(lastAppliedIndex)));
        when(engine.get(IKVEngine.DEFAULT_NS, stateKey(id))).thenReturn(Optional.of(state.toByteString()));

        KVRangeMetadataAccessor metadata = new KVRangeMetadataAccessor(id, engine);
        IKVRangeState.KVRangeMeta meta = metadata.source().blockingFirst();
        // version upgrade to 12;
        long newVer = 12;
        State newState = State.newBuilder().setType(State.StateType.ConfigChanging).build();

        when(engine.get(IKVEngine.DEFAULT_NS, verKey(id))).thenReturn(Optional.of(toByteStringNativeOrder(newVer)));
        when(engine.get(IKVEngine.DEFAULT_NS, stateKey(id))).thenReturn(Optional.of(newState.toByteString()));

        metadata.refresh();
        IKVRangeState.KVRangeMeta newMeta = metadata.source().blockingFirst();
        assertEquals(newVer, metadata.version());
        assertEquals(range, metadata.range());
        assertEquals(lastAppliedIndex, metadata.lastAppliedIndex());
        assertEquals(newState, metadata.state());
        assertEquals(keyRangeId, metadata.dataBoundId());

        assertNotEquals(meta, newMeta);
    }

    @Test
    public void refreshWithRangeChanged() {
        KVRangeId id = KVRangeIdUtil.generate();
        int keyRangeId = 1;
        long ver = 10;
        Range range = FULL_RANGE;
        Range dataBound = KVRangeKeys.dataBound(range);
        State state = State.newBuilder().setType(State.StateType.Normal).build();

        when(engine.registerKeyRange(IKVEngine.DEFAULT_NS, dataBound.getStartKey(), dataBound.getEndKey()))
            .thenReturn(keyRangeId);
        when(engine.get(IKVEngine.DEFAULT_NS, verKey(id))).thenReturn(Optional.of(toByteStringNativeOrder(ver)));
        when(engine.get(IKVEngine.DEFAULT_NS, rangeKey(id))).thenReturn(Optional.of(range.toByteString()));
        when(engine.get(IKVEngine.DEFAULT_NS, stateKey(id))).thenReturn(Optional.of(state.toByteString()));

        KVRangeMetadataAccessor metadata = new KVRangeMetadataAccessor(id, engine);
        assertEquals(KVRangeKeys.dataBound(FULL_RANGE), metadata.dataBound());
        IKVRangeState.KVRangeMeta meta = metadata.source().blockingFirst();

        // version upgrade to 11;
        long newVer = 11;
        int newKeyRangeId = 2;
        Range newRange = Range.newBuilder()
            .setStartKey(ByteString.copyFromUtf8("a"))
            .setEndKey(ByteString.copyFromUtf8("b"))
            .build();
        Range newBound = KVRangeKeys.dataBound(newRange);

        when(engine.registerKeyRange(IKVEngine.DEFAULT_NS, newBound.getStartKey(), newBound.getEndKey()))
            .thenReturn(newKeyRangeId);
        when(engine.get(IKVEngine.DEFAULT_NS, verKey(id))).thenReturn(Optional.of(toByteStringNativeOrder(newVer)));
        when(engine.get(IKVEngine.DEFAULT_NS, rangeKey(id))).thenReturn(Optional.of(newRange.toByteString()));

        metadata.refresh();
        IKVRangeState.KVRangeMeta newMeta = metadata.source().blockingFirst();

        verify(engine).unregisterKeyRange(keyRangeId);
        assertEquals(newVer, metadata.version());
        assertEquals(newRange, metadata.range());
        assertEquals(KVRangeKeys.dataBound(newRange), newBound);
        assertEquals(newKeyRangeId, metadata.dataBoundId());
        assertNotEquals(meta, newMeta);
    }

    @Test
    public void destroyWithDataKept() {
        KVRangeId id = KVRangeIdUtil.generate();
        int keyRangeId = 1;
        long ver = 10;
        Range range = FULL_RANGE;
        Range dataBound = KVRangeKeys.dataBound(range);
        State state = State.newBuilder().setType(State.StateType.Normal).build();

        when(engine.registerKeyRange(IKVEngine.DEFAULT_NS, dataBound.getStartKey(), dataBound.getEndKey()))
            .thenReturn(keyRangeId);
        when(engine.get(IKVEngine.DEFAULT_NS, verKey(id))).thenReturn(Optional.of(toByteStringNativeOrder(ver)));
        when(engine.get(IKVEngine.DEFAULT_NS, rangeKey(id))).thenReturn(Optional.of(range.toByteString()));
        when(engine.get(IKVEngine.DEFAULT_NS, stateKey(id))).thenReturn(Optional.of(state.toByteString()));

        KVRangeMetadataAccessor metadata = new KVRangeMetadataAccessor(id, engine);
        int batchId = 1;
        when(engine.startBatch()).thenReturn(1);
        metadata.destroy(false);

        verify(engine).delete(batchId, IKVEngine.DEFAULT_NS, verKey(id));
        verify(engine).delete(batchId, IKVEngine.DEFAULT_NS, rangeKey(id));
        verify(engine).delete(batchId, IKVEngine.DEFAULT_NS, lastAppliedIndexKey(id));
        verify(engine).delete(batchId, IKVEngine.DEFAULT_NS, stateKey(id));
        verify(engine).endBatch(batchId);
        verify(engine).unregisterKeyRange(keyRangeId);
    }

    @Test
    public void destroyWithEmptyRange() {
        KVRangeId id = KVRangeIdUtil.generate();
        int keyRangeId = 1;
        long ver = 10;
        Range range = EMPTY_RANGE;
        Range dataBound = KVRangeKeys.dataBound(range);
        State state = State.newBuilder().setType(State.StateType.Normal).build();

        when(engine.registerKeyRange(IKVEngine.DEFAULT_NS, dataBound.getStartKey(), dataBound.getEndKey()))
            .thenReturn(keyRangeId);
        when(engine.get(IKVEngine.DEFAULT_NS, verKey(id))).thenReturn(Optional.of(toByteStringNativeOrder(ver)));
        when(engine.get(IKVEngine.DEFAULT_NS, rangeKey(id))).thenReturn(Optional.of(range.toByteString()));
        when(engine.get(IKVEngine.DEFAULT_NS, stateKey(id))).thenReturn(Optional.of(state.toByteString()));

        KVRangeMetadataAccessor metadata = new KVRangeMetadataAccessor(id, engine);

        int batchId = 1;
        when(engine.startBatch()).thenReturn(1);
        metadata.destroy(true);

        verify(engine).delete(batchId, IKVEngine.DEFAULT_NS, verKey(id));
        verify(engine).delete(batchId, IKVEngine.DEFAULT_NS, rangeKey(id));
        verify(engine).delete(batchId, IKVEngine.DEFAULT_NS, lastAppliedIndexKey(id));
        verify(engine).delete(batchId, IKVEngine.DEFAULT_NS, stateKey(id));
        verify(engine).endBatch(batchId);
        verify(engine).unregisterKeyRange(keyRangeId);
    }

    @Test
    public void destroyWithData() {
        KVRangeId id = KVRangeIdUtil.generate();
        int keyRangeId = 1;
        long ver = 10;
        Range range = FULL_RANGE;
        Range dataBound = KVRangeKeys.dataBound(range);
        State state = State.newBuilder().setType(State.StateType.Normal).build();

        when(engine.registerKeyRange(IKVEngine.DEFAULT_NS, dataBound.getStartKey(), dataBound.getEndKey()))
            .thenReturn(keyRangeId);
        when(engine.get(IKVEngine.DEFAULT_NS, verKey(id))).thenReturn(Optional.of(toByteStringNativeOrder(ver)));
        when(engine.get(IKVEngine.DEFAULT_NS, rangeKey(id))).thenReturn(Optional.of(range.toByteString()));
        when(engine.get(IKVEngine.DEFAULT_NS, stateKey(id))).thenReturn(Optional.of(state.toByteString()));

        KVRangeMetadataAccessor metadata = new KVRangeMetadataAccessor(id, engine);

        int batchId = 1;
        ByteString dataKey = dataKey(ByteString.copyFromUtf8("a"));
        when(engine.startBatch()).thenReturn(1);
        when(engine.newIterator(keyRangeId)).thenReturn(itr);
        when(itr.isValid()).thenReturn(true).thenReturn(false);
        when(itr.key()).thenReturn(dataKey);
        metadata.destroy(true);

        verify(engine).delete(batchId, IKVEngine.DEFAULT_NS, verKey(id));
        verify(engine).delete(batchId, IKVEngine.DEFAULT_NS, rangeKey(id));
        verify(engine).delete(batchId, IKVEngine.DEFAULT_NS, lastAppliedIndexKey(id));
        verify(engine).delete(batchId, IKVEngine.DEFAULT_NS, stateKey(id));
        verify(engine).endBatch(batchId);
        verify(engine).unregisterKeyRange(keyRangeId);
        verify(itr).seekToFirst();
        verify(itr).next();
        verify(engine).delete(batchId, keyRangeId, dataKey);
    }
}

