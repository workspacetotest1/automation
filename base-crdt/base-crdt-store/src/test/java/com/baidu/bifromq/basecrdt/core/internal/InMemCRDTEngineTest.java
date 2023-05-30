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

package com.baidu.bifromq.basecrdt.core.internal;

import static com.baidu.bifromq.basecrdt.core.api.CRDTURI.toURI;
import static com.baidu.bifromq.basecrdt.core.api.CausalCRDTType.aworset;
import static com.baidu.bifromq.basecrdt.core.api.CausalCRDTType.mvreg;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.baidu.bifromq.basecrdt.core.api.CRDTEngineOptions;
import com.baidu.bifromq.basecrdt.proto.Replica;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class InMemCRDTEngineTest {
    private InMemCRDTEngine engine;

    @Before
    public void setup() {
        engine = new InMemCRDTEngine(CRDTEngineOptions.builder().build());
        engine.start();
    }

    @After
    public void teardown() {
        engine.stop();
    }

    @Test
    public void testHostWithReplicaIdSpecified() {
        Replica mvRegReplica = engine.host(toURI(mvreg, "mvreg"));
        Replica aworsetReplica = engine.host(toURI(aworset, "aworset"), mvRegReplica.getId());

        assertEquals(mvRegReplica.getId(), aworsetReplica.getId());
        assertNotEquals(engine.get(toURI(mvreg, "mvreg")).get(), engine.get(toURI(aworset, "aworset")).get());
    }
}
