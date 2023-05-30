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

package com.baidu.bifromq.basecrdt.service;

import static org.junit.Assert.fail;

import com.baidu.bifromq.basecluster.AgentHostOptions;
import com.baidu.bifromq.basecrdt.service.annotation.ServiceCfg;
import com.baidu.bifromq.basecrdt.service.annotation.ServiceCfgs;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

@Slf4j
public class CRDTServiceTestTemplate {
    protected CRDTServiceTestCluster testCluster;

    @Rule
    public final TestRule rule = new TestWatcher() {

        @Override
        protected void starting(Description description) {
            super.starting(description);
            testCluster = new CRDTServiceTestCluster();
            ServiceCfgs serviceCfgs = description.getAnnotation(ServiceCfgs.class);
            ServiceCfg serviceCfg = description.getAnnotation(ServiceCfg.class);
            String seedStoreId = null;
            if (testCluster != null) {
                if (serviceCfgs != null) {
                    for (ServiceCfg cfg : serviceCfgs.services()) {
                        testCluster.newService(cfg.id(), buildHostOptions(cfg), buildCrdtServiceOptions(cfg));
                        if (cfg.isSeed()) {
                            seedStoreId = cfg.id();
                        }
                    }
                }
                if (serviceCfg != null) {
                    testCluster.newService(serviceCfg.id(),
                        buildHostOptions(serviceCfg),
                        buildCrdtServiceOptions(serviceCfg));
                }
                if (seedStoreId != null && serviceCfgs != null) {
                    for (ServiceCfg cfg : serviceCfgs.services()) {
                        if (!cfg.id().equals(seedStoreId)) {
                            try {
                                testCluster.join(cfg.id(), seedStoreId);
                            } catch (Exception e) {
                                log.error("Join failed", e);
                            }
                        }
                    }
                }
            }
            log.info("Starting test: " + description.getMethodName());
        }
    };

    @After
    public void teardown() {
        if (testCluster != null) {
            log.info("Shutting down test cluster");
            testCluster.shutdown();
        }
    }

    public void awaitUntilTrue(Callable<Boolean> condition) {
        awaitUntilTrue(condition, 5000);
    }

    public void awaitUntilTrue(Callable<Boolean> condition, long timeoutInMS) {
        try {
            long waitingTime = 0;
            while (!condition.call()) {
                Thread.sleep(100);
                waitingTime += 100;
                if (waitingTime > timeoutInMS) {
                    fail();
                }
            }
        } catch (Exception e) {
            fail();
        }
    }

    private CRDTServiceOptions buildCrdtServiceOptions(ServiceCfg cfg) {
        return new CRDTServiceOptions();
    }

    private AgentHostOptions buildHostOptions(ServiceCfg cfg) {
        // expose more options
        return new AgentHostOptions()
            .addr(cfg.bindAddr())
            .port(cfg.bindPort());
    }
}
