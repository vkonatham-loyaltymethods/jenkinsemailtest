################################################################################
# Java-based service image build functions
# v1.0.0    March 23, 2021
################################################################################
export DOCKER_CLI_EXPERIMENTAL=enabled
SUPPORTED_ARCH="amd64 arm64"

# determine ECR host that holds the base images
set_ecr_host_var() {
  export ECR_HOST=${ECR_HOST:-$(dig +short ecr.devops.lmvi.net TXT | sed 's/"//g')}
  if [ -z "$ECR_HOST" ]
  then
    echo "Cannot resolve ECR hostname.  Is dig installed or ecr.devops.lmvi.net TXT record configured?"
    exit 1
  fi
  REGION=$(echo $ECR_HOST | sed 's/^[0-9]*\.dkr\.ecr\.//; s/\.amazonaws\.com//')
  echo "Running in region $REGION"
  aws --region $REGION ecr get-login --no-include-email | bash
}

# determine build info from git
set_build_var() {
  BUILD=${BUILD:-$(python $(dirname $0)/version.py)}
  if [ -z "$BUILD" ]
  then
    echo "Could not determine build version!"
    exit 1
  fi
}

# create docker ignore for prod images (we don't want to include the git
# repo files)
create_dockerignore() {
  echo ".git" > $IGNORE_FILE
}

tag() {
    [ $BRANCH_NAME == "master" ] && echo ${BRANCH_NAME}-${BUILD} || echo ${BRANCH_NAME}-${BUILD}-$BUILD_ID
}

build_image() {
  set_ecr_host_var
  set_build_var
  local tag=$(tag)
  docker build \
    --build-arg ECR_HOST=$ECR_HOST \
    -t ${IMAGE_NAME}:${ARCH}-$tag \
    -f $(dirname $0)/docker/Dockerfile .
  [ $? -ne 0 ] && exit 1
  local latest_tag=${BRANCH_NAME}-latest
  docker tag ${IMAGE_NAME}:${ARCH}-${tag} ${IMAGE_NAME}:${ARCH}-${latest_tag}
  if [ "$ARCH" == "amd64" ]
  then
    start_scan_local ${IMAGE_NAME}:${ARCH}-${latest_tag}
    [ $? -ne 0 ] && { export SCAN_STATUS=fail ; echo "Image has vulnerabilities, please check and fix" ; } || :
  fi
  push_to_registries $tag
  push_to_registries $latest_tag
  create_manifests $tag
  create_manifests $latest_tag
  remove_images $tag
  remove_images $latest_tag
}

get_registries() {
  if [ -f $DIR/registry.conf ]
  then
    cat $DIR/registry.conf
  else
    dig +short registries.devops.lmvi.net TXT | sed 's/"//g'
  fi
}

# push the images to the needed registries
push_to_registries() {
  local tag=$1
  for acct_region in $(get_registries)
  do
    local acct=$(echo $acct_region | cut -f1 -d:)
    local region=$(echo $acct_region | cut -f2 -d:)
    local ecr_host="$acct.dkr.ecr.$region.amazonaws.com"
    aws ecr get-login --registry-ids $acct --region $region --no-include-email | bash

    echo "PUSHING tag $tag for $ARCH to $ecr_host"
    docker tag ${IMAGE_NAME}:${ARCH}-${tag} ${ecr_host}/${IMAGE_NAME}:${ARCH}-${tag}
    [ $? -ne 0 ] && exit 1
    docker push ${ecr_host}/${IMAGE_NAME}:${ARCH}-${tag}
    [ $? -ne 0 ] && exit 1
  done
}

# create manifests for multi-arch
create_manifests() {
  local tag=$1
  for acct_region in $(get_registries)
  do
    local acct=$(echo $acct_region | cut -f1 -d:)
    local region=$(echo $acct_region | cut -f2 -d:)
    local ecr_host="$acct.dkr.ecr.$region.amazonaws.com"
    for arch in $SUPPORTED_ARCH
    do
      docker manifest create --amend \
        ${ecr_host}/${IMAGE_NAME}:${tag} \
        ${ecr_host}/${IMAGE_NAME}:${arch}-${tag}
    done
    docker manifest push --purge ${ecr_host}/${IMAGE_NAME}:${tag}
    [ $? -ne 0 ] && exit 1
  done
}

# remove images
remove_images() {
  local tag=$1
  docker rmi ${IMAGE_NAME}:${ARCH}-${tag}
  for acct_region in $(get_registries)
  do
    local acct=$(echo $acct_region | cut -f1 -d:)
    local region=$(echo $acct_region | cut -f2 -d:)
    local ecr_host="$acct.dkr.ecr.$region.amazonaws.com"
    docker rmi ${ecr_host}/${IMAGE_NAME}:${ARCH}-${tag}
    [ $? -ne 0 ] && exit 1
  done
}

# pull latest images from ecr (in case of failover and images are not up to date or do not exist)
pull_java_base_images() {
  # default region should be set in /jenkins/.aws/config on jenkins server
  aws ecr get-login --no-include-email | bash
  [ $? -ne 0 ] && exit 1

  set_ecr_host_var
  for image in rcx-java-base:latest rcx-java-build:11-maven
  do
    docker pull $ECR_HOST/$image
    [ $? -ne 0 ] && exit 1
  done
}

# set ARCH variable to current architecture
set_ARCH() {
  case $(uname -p) in
    x86_64) ARCH=amd64;;
    aarch64) ARCH=arm64;;
    *) echo "Unsupported architecture" >&2; exit 1;;
  esac
  export ARCH
}
