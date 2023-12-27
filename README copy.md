# rcx-event-publisher

The rcx-event-publisher service aims to replace the existing NodeJS MEP/AEP services. THere are a couple of reasons to do this:

- The MEP/AEP present a significant infrasturcture risk becuase the shards they process are hard-coded.
- The MEP/AEP is currently not scaling beyond 100-200tps in the Perf environment. After that it starts to fall behind on consuming the oplog.
- The NodejS Kafka interface is not well supported, leading to other risks as we move into future versions of Kafka.

What we propose is a Java version of this service that is able to:

- Utilize the Helix framework to dynamically partition the workload, based on consumers coming and going.
- Utilize the spring-boot framework in concert with our other services that have been proven in production already.

