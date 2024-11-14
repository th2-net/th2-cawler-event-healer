# Event healer processor (0.0.3)

Event healer processor fixes wrong status of events. When a child event has a "failed" status and its parent
has a "success" status, the status of the parent is wrong. Healer finds the parent event and makes its status "failed", too.

Sometimes parent event doesn't exist in cradle when the healer process its child event with "failed" states. 
In this case processor repeats searching of the parent event with interval according to `processorSettings`

## Configuration

There is an example of full configuration (infra-2.1) for the event healer processor

```yaml
apiVersion: th2.exactpro.com/v2
kind: Th2Box
metadata:
  name: event-healer
spec:
  imageName: ghcr.io/th2-net/th2-crawler-event-healer
  imageVersion: 0.0.3-dev
  type: th2-conn
  pins:
    grpc:
      client:
        - name: to_data_provider
          serviceClass: com.exactpro.th2.dataprovider.lw.grpc.DataProviderService
          linkTo:
            - box: lw-data-provider-grpc
              pin: server
        - name: to_data_provider_stream
          serviceClass: com.exactpro.th2.dataprovider.lw.grpc.QueueDataProviderService
          linkTo:
            - box: lw-data-provider-grpc
              pin: server
  customConfig:
    crawler:
      from: 2024-11-14T00:00:00.00Z
      intervalLength: PT10M
      syncInterval: PT10M
      awaitTimeout: 10
      awaitUnit: SECONDS
      events:
        bookToScopes:
          test_book: [ "script" ]
    processorSettings:
      maxCacheCapacity: 1024
      updateUnsubmittedEventInterval: 1
      updateUnsubmittedEventTimeUnit: SECONDS
      updateUnsubmittedEventAttempts: 100
  extendedSettings:
    envVariables:
      JAVA_TOOL_OPTIONS: >
        -XX:+ExitOnOutOfMemoryError
        -XX:+UseContainerSupport
        -Dlog4j2.shutdownHookEnabled=false
        -Xlog:gc,gc+heap*,gc+start,gc+metaspace::utc,level,tags
        -XX:MaxRAMPercentage=38
        -XX:MaxMetaspaceSize=80M
        -XX:CompressedClassSpaceSize=12M
        -XX:ReservedCodeCacheSize=35M
        -XX:MaxDirectMemorySize=15M
        -Ddatastax-java-driver.advanced.connection.init-query-timeout="5000 milliseconds"
        -Ddatastax-java-driver.basic.request.timeout="10 seconds"
    resources:
      limits:
        memory: 250Mi
        cpu: 200m
      requests:
        memory: 100Mi
        cpu: 50m
```

Please note the `th2-lw-data-provider` worked in gRPC mode is required for the current processor.
The main configuration of `th2-lw-data-provider` described below, full documentation can be found by the (link)[https://github.com/th2-net/th2-lw-data-provider]

```yaml
apiVersion: th2.exactpro.com/v2
kind: Th2CoreBox
metadata:
  name: lw-data-provider-grpc
spec:
  imageName: ghcr.io/th2-net/th2-lw-data-provider
  imageVersion: 2.12.0-dev
  type: th2-rpt-data-provider
  customConfig:
    grpcBackPressure: true
    hostname: 0.0.0.0
    port: 8080
    mode: GRPC
  extendedSettings:
    envVariables:
      JAVA_TOOL_OPTIONS: >
        -XX:+ExitOnOutOfMemoryError
        -XX:+UseContainerSupport 
        -Dlog4j2.shutdownHookEnabled=false
        -Xlog:gc,gc+heap*,gc+start,gc+metaspace::utc,level,tags
        -XX:MaxRAMPercentage=85
        -Ddatastax-java-driver.advanced.connection.init-query-timeout="5000 milliseconds"
        -Ddatastax-java-driver.basic.request.timeout="10 seconds"
    resources:
      limits:
        memory: 1000Mi
        ephemeral-storage: 500Mi
        cpu: 1000m
      requests:
        memory: 300Mi
        ephemeral-storage: 500Mi
        cpu: 50m
    service:
      enabled: true
      clusterIP:
        - name: grpc
          containerPort: 8080
  pins:
    grpc:
      server:
        - name: server
          serviceClasses:
            - com.exactpro.th2.dataprovider.lw.grpc.DataProviderService
            - com.exactpro.th2.dataprovider.lw.grpc.QueueDataProviderService
```

### Processor settings description

+ **maxCacheCapacity** (_**1024** by default_) - the maximum capacity of the cache that stores
  events processed by Healer. Caching events is useful in order to
  avoid their repeated retrieval from Cradle.
  After reaching the maximum capacity, the least recent accessed event
  from the cache will be removed, so no overflow occurs.
+ **updateUnsubmittedEventInterval** (_**1** by default_) - value of interval between attempts for updating parent event status if it doesn't exist
+ **updateUnsubmittedEventTimeUnit** (_**SECONDS** by default_) - time unit of interval between attempts for updating parent event status if it doesn't exist
+ **updateUnsubmittedEventAttempts** (_**100**_ by default_) - number of attempts to update parent event status if it doesn't exist

# Useful links

The event-healer based on the th2-processor-core project, you can look at its readme to read about processor configuration.   

+ th2-processor-core - https://github.com/th2-net/th2-processor-core-j
+ th2-lw-data-provider - https://github.com/th2-net/th2-lw-data-provider
+ th2-common - https://github.com/th2-net/th2-common-j

## Release notes

### 0.0.3
* Migrated to th2 gradle plugin `0.1.4` (based on th2-bom: `4.8.0`)
* Updated:
  * common: `5.14.0-dev`
  * common-utils: `2.3.0-dev`
  * processor-core: `0.3.0-dev`
  * cradle-cassandra: `5.4.4-dev`
  * auto-service: `1.1.1`
  * caffeine: `3.1.8`
  * kotlin-logging: `3.0.5`
  * kotlin: `1.8.22`