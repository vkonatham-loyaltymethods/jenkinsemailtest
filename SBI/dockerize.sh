#!/bin/bash

LOG_FILE=build_fail.log
SCAN="scan_status.log"

if grep "BUILD FAILURE" ${LOG_FILE} >> /dev/null;then
 :
 else
 mv ${LOG_FILE} ${LOG_FILE}-txt-$$
 fi
exec > >(tee -a ${LOG_FILE} )
exec 2>&1

shopt -s extglob

DIR=$(dirname $0)
# what the docker image will be called
IMAGE_NAME=rcx-event-publisher

# docker ignore file (specifies which files to not include in the docker
# build context
IGNORE_FILE=$DIR/../.dockerignore

# load functions
source $DIR/lib/functions.bash
source $DIR/scan.sh

# remove the docker ignore file if it exists
[ -f $IGNORE_FILE ] && rm $IGNORE_FILE
[ -s $LOGFILE ] && cat /dev/null > $LOGFILE
[ -s $SCAN ] && cat /dev/null > $SCAN

# set ARCH variable to current architecture
set_ARCH

# we want to be able to behave differently for each branch
case $BRANCH_NAME in
  develop | feature-*  | bug-*)
    build_image
    ;;
  *)
    if [ -z "$BRANCH_NAME" ]
    then
      echo "No branch name provided!"
      exit 1
    fi
    create_dockerignore
    build_image
    ;;
esac

# remove the docker ignore file if it exists
[ -f $IGNORE_FILE ] && rm $IGNORE_FILE

# exporting image scan status
echo $SCAN_STATUS > $SCAN
exit 0
