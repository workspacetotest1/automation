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

package com.baidu.bifromq.metrics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class TrafficMeterTest {
    @Test
    public void test() throws InterruptedException {
        TrafficMeter meter = TrafficMeter.get("trafficA");
        meter.recordCount(TrafficMetric.MqttConnectCount);
        assertFalse(Metrics.globalRegistry.getMeters().isEmpty());
        meter = null;
        System.gc();
        TrafficMeter.cleanUp();
        System.gc();
        Thread.sleep(100);
        assertTrue(Metrics.globalRegistry.getMeters().isEmpty());
    }
}
