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

package com.baidu.bifromq.basekv.balance.command;

import com.baidu.bifromq.basekv.utils.KVRangeIdUtil;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
public class ChangeConfigCommand extends BalanceCommand {

    private Set<String> voters;
    private Set<String> learners;

    @Override
    public CommandType type() {
        return CommandType.CHANGE_CONFIG;
    }

    @Override
    public String toString() {
        return String.format("ChangeConfigCommand{toStore=%s, kvRangeId=%s, expectedVer=%d, voters=%s, learner=%s}",
            getToStore(), KVRangeIdUtil.toString(getKvRangeId()), getExpectedVer(), voters, learners);
    }
}
