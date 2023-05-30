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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class KVRangeQueryLinearizerTest {
    @Mock
    private Supplier<CompletableFuture<Long>> readIndexSupplier;

    @Test
    public void linearize() {
        KVRangeQueryLinearizer linearizer =
            new KVRangeQueryLinearizer(readIndexSupplier, MoreExecutors.directExecutor());
        when(readIndexSupplier.get())
            .thenReturn(CompletableFuture.completedFuture(1L),
                CompletableFuture.completedFuture(1L),
                CompletableFuture.completedFuture(2L),
                CompletableFuture.completedFuture(2L));
        CompletableFuture<Void> t1 = linearizer.linearize().toCompletableFuture();
        CompletableFuture<Void> t2 = linearizer.linearize().toCompletableFuture();
        CompletableFuture<Void> t3 = linearizer.linearize().toCompletableFuture();
        assertFalse(t1.isDone());
        assertFalse(t2.isDone());
        assertFalse(t3.isDone());
        linearizer.afterLogApplied(1);
        assertTrue(t1.isDone());
        assertTrue(t2.isDone());
        assertFalse(t3.isDone());
        linearizer.afterLogApplied(2L);
        assertTrue(t3.isDone());

        assertTrue(linearizer.linearize().toCompletableFuture().isDone());
    }
}
