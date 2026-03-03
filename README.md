# ActiveMQ Stress & Integrity Testing

## Objective

The purpose of this session was to **stress-test Apache ActiveMQ 5.15.15** in order to observe broker behavior under high load, including:

- Persistent store pressure
- Producer flow control
- Message latency
- Potential out-of-memory (OOM) conditions
- Message integrity under stress

We aimed to reproduce conditions where the broker might fall behind, delay messages, or encounter resource limits, and to measure how these scenarios affect latency and message delivery.

---

## Test Environment

- **ActiveMQ Version:** 5.15.15
- **Deployment:** Podman container
- **Broker Config Adjustments:**
  - Reduced `memoryUsage` to 32 MB to force in-memory paging
  - Reduced `storeUsage` to 512 MB to trigger persistent storage limits quickly
  - Optional: disabled `producerFlowControl` to allow messages to continue flooding the broker
  - Logging levels increased (`DEBUG`) for usage and KahaDB store tracking
- **Java Stress Test:**
  - Messages sent with a **UUID** per message
  - Payload includes the same UUID for integrity verification
  - Send timestamps included to measure **latency**
  - Slow consumer simulated to let queues build up
  - Tracks metrics: total messages sent/received, average latency, max latency, payload mismatches

---

## Test Goals

1. **Observe Persistent Storage Saturation**
   - Verify that `storeUsage` warnings appear in `activemq.log`
   - Track when producers start to block or throw exceptions
2. **Measure Message Latency**
   - Average latency vs maximum latency
   - Identify spikes caused by memory/store pressure
3. **Verify Message Integrity**
   - Each message carries a UUID in a property and in the payload
   - Consumer checks that UUID matches the payload
   - Logs any mismatches
4. **Simulate Potential OOM**
   - Small JVM heap combined with rapid producer sends
   - Monitor JVM memory and container memory usage
   - Observe broker behavior when memory and store limits are exceeded
5. **Container Resource Awareness**
   - Check available disk space in the Podman container
   - Ensure broker limits (`storeUsage` and log size) do not exceed container disk capacity

---

## Observations / Findings

- `activemq.log` showed **persistent store full warnings** when queues grew faster than consumers could drain them.
- Max latency measurements spiked significantly under store pressure, while average latency remained moderate.
- Consumers verified **UUID payload integrity**, ensuring no messages were lost or corrupted during the test.
- Container disk space and JVM heap were potential limiting factors. Without careful configuration, writing too many messages could trigger disk-full errors or OOM.
- Logging too much without limits in a small container can cause **write failures** and missing log entries.

---

## Recommendations for Safe Stress Testing

1. **Mount host volumes** for broker data and logs to avoid overlay storage limits.
2. **Set storeUsage** and log file limits below available disk in the container.
3. Use **small heap** if testing memory pressure, but monitor JVM memory with `jconsole` or `jvisualvm`.
4. Monitor **memoryUsage, storeUsage, and tempUsage** via JMX or admin console.
5. Consider **slow consumer simulation** to safely reproduce latency and backpressure.

---

## Next Steps / Extensions

- Multi-threaded producers to increase stress
- Multiple slow consumers for more realistic message backlog scenarios
- Automated capture of **producer send blocking time** along with latency metrics
- Integration with monitoring tools (Prometheus/Grafana) to visualize stress over time

## Build / Run

Compile
`cd ~/CODE/java/amq-test`
`mvn clean compile`

Run
- `mvn exec:java -Dexec.mainClass=com.example.ActiveMQStressTest`
