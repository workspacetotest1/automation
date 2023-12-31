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

package com.baidu.bifromq.starter.config;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.io.File;
import java.nio.file.Paths;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

@Slf4j
public class StandaloneConfigTest {

    @SneakyThrows
    @Test
    public void buildFromYaml() {
        File confFile = Paths.get(this.getClass().getResource("/standalone.yml").toURI()).toFile();
        StandaloneConfig config = StandaloneConfig.build(confFile);
        assertEquals(config.getHost(), "0.0.0.0");
        assertNotNull(config.getDistWorkerConfig());
        assertNotNull(config.getInboxStoreConfig());
        assertNotNull(config.getRetainStoreConfig());
    }
}
