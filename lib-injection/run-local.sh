#!/bin/sh

# Absolute path to this script, e.g. /home/user/bin/foo.sh
SCRIPT_PATH=$(readlink -f "$0")
# Absolute path this script is in, thus /home/user/bin
SCRIPT_DIR=$(dirname "${SCRIPT_PATH}")

cd ${SCRIPT_DIR}

if ! [[ "$(kind get clusters)" =~ "dd-trace-java" ]] ;  then
    kind create cluster --name dd-trace-java --config src/test/resources/kind-config.yaml
fi

kubectl apply -f src/test/resources/dd-apm-test-agent-config.yaml
kubectl rollout status daemonset/datadog-agent
sleep 5
POD_NAME=$(kubectl get pods -l app=datadog-agent -o name)
kubectl wait $POD_NAME --for condition=ready

mkdir -p ../dd-java-agent/build/libs
wget -O ../dd-java-agent/build/libs/dd-java-agent.jar https://dtdg.co/latest-java-tracer

if ! [[ "$(docker buildx ls)" =~ "dd-trace-java" ]] ;  then
    docker buildx create --name dd-trace-java
fi

docker buildx use dd-trace-java
export BUILDX_PLATFORMS=`docker buildx imagetools inspect --raw busybox:latest | jq -r 'reduce (.manifests[] | [ .platform.os, .platform.architecture, .platform.variant ] | join("/") | sub("\\/$"; "")) as $item (""; . + "," + $item)' | sed 's/,//'`

./build.sh docker.io/${1}/dd-java-agent-init local

cat <<EOF > ${SCRIPT_DIR}/build/app-config.yaml
apiVersion: v1
kind: Pod
metadata:
  labels:
    app: my-app
  name: my-app
spec:
  initContainers:
    - image: docker.io/${1}/dd-java-agent-init:local
      command: ["sh", "copy-javaagent.sh", "/datadog"]
      name: copy-sdk
      volumeMounts:
        - name: apm-sdk-volume
          mountPath: /datadog
  containers:
    - image: docker.io/bdevinssureshatddog/k8s-lib-injection-app:latest
      env:
        - name: SERVER_PORT
          value: "18080"
        - name: JAVA_TOOL_OPTIONS
          value: "-javaagent:/datadog/dd-java-agent.jar"
        - name: DD_AGENT_HOST
          valueFrom:
            fieldRef:
              fieldPath: status.hostIP
        - name: DD_AGENT_PORT
          value: "18126"
      name: my-app
      readinessProbe:
        initialDelaySeconds: 1
        periodSeconds: 2
        timeoutSeconds: 1
        successThreshold: 1
        failureThreshold: 1
        httpGet:
          host:
          scheme: HTTP
          path: /
          port: 18080
        initialDelaySeconds: 5
        periodSeconds: 5
      ports:
        - containerPort: 18080
          hostPort: 18080
          protocol: TCP
      volumeMounts:
        - name: apm-sdk-volume
          mountPath: /datadog
  volumes:
    - name: apm-sdk-volume
      emptyDir: {}
EOF

kubectl apply -f ${SCRIPT_DIR}/build/app-config.yaml
kubectl wait pod/my-app --for condition=ready --timeout=5m

sleep 5
curl http://localhost:18126/test/traces