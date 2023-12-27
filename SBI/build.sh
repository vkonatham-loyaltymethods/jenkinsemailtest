#!/bin/bash

if [[ ! "$(docker network ls --format "{{.Name}}" | grep rcx_sbi)" ]];then
 docker network create -d bridge rcx_sbi
fi

LOG_FILE=build_fail.log
[ -s ${LOG_FILE} ] && >${LOG_FILE}
exec > >(tee -a ${LOG_FILE} )
exec 2>&1

export PATH="$PATH:/usr/local/bin"
export USERID=$(id -u)
export GROUPID=$(id -g)
echo "Running as UID=$USERID, GID=$GROUPID"
DIR=$(dirname $0)
cd $DIR
source lib/functions.bash

pull_java_base_images

CONTAINER_NAME="builder-$(echo ${JOB_NAME} | tr '/ ' '._').${BRANCH_NAME}"
[ -n "$CHANGE_ID" ] && CONTAINER_NAME="${CONTAINER_NAME}-PR${CHANGE_ID}"
CONTAINER_NAME="${CONTAINER_NAME}-${BUILD_ID}"
[ $BRANCH_NAME == "master" ] && export NEXUS_REPO=rcx-releases || export NEXUS_REPO=rcx-snapshots

docker-compose -f test-bed.yml run \
    --rm -w "$WORKSPACE" \
    --entrypoint "mvn -U -s settings.xml clean package" \
    --name "$CONTAINER_NAME" \
    maven-app-build-11
exit $?
