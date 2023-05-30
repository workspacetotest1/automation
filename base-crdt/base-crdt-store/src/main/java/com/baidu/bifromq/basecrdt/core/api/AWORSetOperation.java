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

package com.baidu.bifromq.basecrdt.core.api;

import com.google.protobuf.ByteString;
import lombok.ToString;

@ToString
public final class AWORSetOperation implements ICRDTOperation {
    private static final AWORSetOperation CLEAR = new AWORSetOperation(Type.Clear, ByteString.EMPTY);

    public enum Type {
        Add, Remove, Clear
    }


    public final Type type;
    public final ByteString element;

    public static AWORSetOperation add(ByteString e) {
        return new AWORSetOperation(Type.Add, e);
    }

    public static AWORSetOperation remove(ByteString e) {
        return new AWORSetOperation(Type.Remove, e);
    }

    public static AWORSetOperation clear() {
        return CLEAR;
    }

    private AWORSetOperation(Type type, ByteString element) {
        this.type = type;
        this.element = element;
    }
}
