#!/bin/bash

#
# Copyright (c) 2023. Baidu, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#    http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and limitations under the License.
#

BASE_DIR=$(cd "$(dirname "$0")";pwd)/..
export LOG_DIR=$BASE_DIR/logs/retain-store

if [ $# -lt 1 ];
then
  echo "USAGE: $0 {start|stop} [-fg]"
  exit 1
fi

COMMAND=$1
shift

if [ $COMMAND = "start" ]; then
  # k8s deploy: in the first statefulset pod, bootstrap = true
  if [ -z "${STATEFULSET_HOSTNAME}" ] || [ "${STATEFULSET_HOSTNAME##*-}" == "0" ]; then
    exec $BASE_DIR/bin/bifromq-start.sh -c com.baidu.bifromq.starter.RetainStoreStarter -f retain-store.yml "$@"
  else
    echo "start with no bootstrap"
    exec $BASE_DIR/bin/bifromq-start.sh -c com.baidu.bifromq.starter.RetainStoreStarter -f retain-store-no-bootstrap.yml "$@"
  fi
elif [ $COMMAND = "stop" ]; then
  exec $BASE_DIR/bin/bifromq-stop.sh RetainStoreStarter
fi
