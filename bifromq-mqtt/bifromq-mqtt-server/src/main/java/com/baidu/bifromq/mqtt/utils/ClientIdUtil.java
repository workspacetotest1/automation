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

package com.baidu.bifromq.mqtt.utils;

import com.google.common.collect.Lists;
import com.google.common.primitives.Chars;
import java.util.HashSet;
import java.util.Set;

public class ClientIdUtil {

    private static final int MAX_CLIENT_LEN = 128;

    private static final Set<Character> CLIENT_ID_CHARS = new HashSet<>(Lists.newArrayList(
        'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
        'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '-', '_'));

    public static boolean validateClientId(String clientId) {
        return clientId != null && clientId.length() <= MAX_CLIENT_LEN &&
            CLIENT_ID_CHARS.containsAll(Chars.asList(clientId.toLowerCase().toCharArray()));
    }
}
