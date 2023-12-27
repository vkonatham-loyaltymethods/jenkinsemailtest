#!/bin/bash

LOG_FILE=build_fail.log
if grep "BUILD FAILURE" ${LOG_FILE} >> /dev/null;then
 :
 else
 mv ${LOG_FILE} ${LOG_FILE}-txt
 fi
exec > >(tee -a ${LOG_FILE} )
exec 2>&1

export PATH="$PATH:/usr/local/bin"
export USERID=$(id -u)
export GROUPID=$(id -g)
DIR=$(dirname $0)
cd $DIR

CONTAINER_NAME="tester-$(echo ${JOB_NAME} | tr '/ ' '._').${BRANCH_NAME}"
[ -n "$CHANGE_ID" ] && CONTAINER_NAME="${CONTAINER_NAME}-PR${CHANGE_ID}"
CONTAINER_NAME="${CONTAINER_NAME}-${BUILD_ID}"
[ $BRANCH_NAME == "master" ] && export NEXUS_REPO=rcx-releases || export NEXUS_REPO=rcx-snapshots

source lib/functions.bash
set_ecr_host_var
set_ARCH

docker-compose -f test-bed.yml run \
    --rm -w "$WORKSPACE" \
    --entrypoint "SBI/runtests.sh" \
    --name "$CONTAINER_NAME" \
    maven-app-build-11
exit $?
