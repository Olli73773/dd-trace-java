#!/bin/sh

# Absolute path to this script, e.g. /home/user/bin/foo.sh
SCRIPT_PATH=$(readlink -f "$0")
# Absolute path this script is in, thus /home/user/bin
SCRIPT_DIR=$(dirname "${SCRIPT_PATH}")
DOCKER_DIR=${SCRIPT_DIR}/src/main/docker
DOCKER_BUILD_DIR=${SCRIPT_DIR}/build/docker

IMAGE_NAME=${1}
IMAGE_TAG=${2}

if [ -z "${BUILDX_PLATFORMS}" ] ; then
  BUILDX_PLATFORMS=`docker buildx imagetools inspect --raw busybox:latest | jq -r 'reduce (.manifests[] | [ .platform.os, .platform.architecture, .platform.variant ] | join("/") | sub("\\/$"; "")) as $item (""; . + "," + $item)' | sed 's/,//'`
fi

mkdir -p ${DOCKER_BUILD_DIR}
cp ${DOCKER_DIR}/* ${DOCKER_BUILD_DIR}/.

if [ -z ${CI} ] ; then
  echo "Running manually"
  cp ${SCRIPT_DIR}/../dd-java-agent/build/libs/dd-java-agent.jar ${DOCKER_BUILD_DIR}
else
  echo "Running on CI"
  cp ${SCRIPT_DIR}/../workspace/dd-java-agent/build/libs/*.jar ${DOCKER_BUILD_DIR}
  mv ${DOCKER_BUILD_DIR}/*.jar ${DOCKER_BUILD_DIR}/dd-java-agent.jar
fi

cd ${DOCKER_BUILD_DIR}
docker buildx build --platform ${BUILDX_PLATFORMS} -t "${IMAGE_NAME}:${IMAGE_TAG}" --push .
