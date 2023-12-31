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

package com.baidu.bifromq.basekv.localengine;

import static java.util.Collections.singletonList;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

import com.baidu.bifromq.baseenv.EnvProvider;
import com.google.common.util.concurrent.MoreExecutors;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

@Slf4j
public class InMemoryKVEngineTest extends AbstractKVEngineTest {
    private ScheduledExecutorService maintenanceTaskExecutor;

    @BeforeMethod
    public void setup() {
        maintenanceTaskExecutor =
            newSingleThreadScheduledExecutor(EnvProvider.INSTANCE.newThreadFactory("Checkpoint GC"));
        InMemoryKVEngineConfigurator configurator = new InMemoryKVEngineConfigurator().setGcIntervalInSec(60000);
        kvEngine = new InMemoryKVEngine(null, singletonList(NS), this::isUsed, configurator,
            Duration.ofSeconds(-1));
        kvEngine.start(maintenanceTaskExecutor);
    }

    @AfterMethod
    public void teardown() {
        kvEngine.stop();
        MoreExecutors.shutdownAndAwaitTermination(maintenanceTaskExecutor, 5, TimeUnit.SECONDS);
    }
}
