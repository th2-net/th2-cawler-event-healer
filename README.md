# Healer data service (0.0.1)

Healer data service fixes wrong status of events. When a child event has a "failed" status and its parent
has a "success" status, the status of the parent is wrong. Healer finds the parent event and makes its status "failed", too.

## Configuration

There is an example of full configuration for the data service

```yaml
apiVersion: th2.exactpro.com/v1
kind: Th2Box
metadata:
  name: event-healer
spec:
  image-name: ghcr.io/th2-net/th2-crawler-event-healer
  image-version: <verison>
  type: th2-conn
  custom-config:
    name: test-event-healer
    version: 1.0.0
    maxCacheCapacity: 100000
  pins:
    - name: server
      connection-type: grpc
  extended-settings:
    service:
      enabled: true
      type: NodePort
      endpoints:
        - name: 'grpc'
          targetPort: 8080
          nodePort: <free port>
    envVariables:
      JAVA_TOOL_OPTIONS: '-XX:+ExitOnOutOfMemoryError -XX:+UseContainerSupport -XX:MaxRAMPercentage=85'
  resources:
    limits:
      memory: 200Mi
      cpu: 200m
    requests:
      memory: 100Mi
      cpu: 50m
```

# Useful links

+ https://github.com/th2-net/th2-crawler-event-healer