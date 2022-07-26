#!/bin/sh

# Absolute path to this script, e.g. /home/user/bin/foo.sh
SCRIPT_PATH=$(readlink -f "$0")
# Absolute path this script is in, thus /home/user/bin
SCRIPT_DIR=$(dirname "${SCRIPT_PATH}")

cd ${SCRIPT_DIR}

if ! [[ "$(kind get clusters)" =~ "dd-trace-java" ]] ;  then
    kind create cluster --name dd-trace-java --config src/test/resources/kind-config.yaml
fi

helm repo add datadog https://helm.datadoghq.com
helm repo update

helm install datadog --set datadog.apiKey=${DD_API_KEY} --set datadog.appKey=${DD_APP_KEY} -f src/test/resources/end-to-end/helm-values.yaml datadog/datadog
sleep 5
POD_NAME=$(kubectl get pods -l app=datadog-cluster-agent -o name)
kubectl wait $POD_NAME --for condition=ready --timeout=5m

sleep 5
POD_NAME=$(kubectl get pods -l app=datadog -o name)
kubectl wait $POD_NAME --for condition=ready --timeout=5m

mkdir -p ../dd-java-agent/build/libs
wget -O ../dd-java-agent/build/libs/dd-java-agent.jar https://dtdg.co/latest-java-tracer

if ! [[ "$(docker buildx ls)" =~ "dd-trace-java" ]] ;  then
    docker buildx create --name dd-trace-java
fi

docker buildx use dd-trace-java

./build.sh docker.io/${1}/dd-java-agent-init local

cat <<EOF > ${SCRIPT_DIR}/build/app-config.yaml
apiVersion: v1
kind: Pod
metadata:
  labels:
    app: my-app
    admission.datadoghq.com/enabled: "true"
    tags.datadoghq.com/env: "local"
    tags.datadoghq.com/service: "my-app"
    tags.datadoghq.com/version: "local"
  annotations:
    admission.datadoghq.com/java-tracer.custom-image: docker.io/${1}/dd-java-agent-init:local
  name: my-app
spec:
  containers:
    - image: docker.io/bdevinssureshatddog/k8s-lib-injection-app:latest
      name: my-app
      env:
        - name: SERVER_PORT
          value: "18080"
        - name: DD_ENV
          valueFrom:
            fieldRef:
              fieldPath: metadata.labels['tags.datadoghq.com/env']
        - name: DD_SERVICE
          valueFrom:
            fieldRef:
              fieldPath: metadata.labels['tags.datadoghq.com/service']
        - name: DD_VERSION
          valueFrom:
            fieldRef:
              fieldPath: metadata.labels['tags.datadoghq.com/version']
        - name: DD_LOGS_INJECTION
          value: "true"
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
EOF

kubectl apply -f ${SCRIPT_DIR}/build/app-config.yaml
kubectl wait pod/my-app --for condition=ready --timeout=5m

sleep 5
curl http://localhost:18126/test/traces