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

package com.baidu.bifromq.basekv.exception;

public class BaseKVException extends RuntimeException {
    public static final BaseKVException SERVER_NOT_FOUND = new BaseKVException("Server not found");

    public BaseKVException(String message) {
        super(message);
    }

    public BaseKVException(String message, Throwable cause) {
        super(message, cause);
    }
}
