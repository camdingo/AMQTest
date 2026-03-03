package com.example;

import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class ActiveMQStressTest {

    private static final String BROKER_URL = "tcp://localhost:61616";
    private static final String QUEUE_NAME = "TEST.PERFORMANCE";

    private static final int MESSAGE_SIZE_BYTES = 50_000;   // 50kb payload
    private static final int SEND_COUNT = 500_000;        // total messages
    private static final int SEND_DELAY_MS = 0;           // 0 = fastest

    public static void main(String[] args) throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(BROKER_URL);
        factory.setUseAsyncSend(true);

        Connection connection = factory.createConnection();
        connection.start();

        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue(QUEUE_NAME);

        MessageProducer producer = session.createProducer(queue);
        producer.setDeliveryMode(DeliveryMode.PERSISTENT);

        MessageConsumer consumer = session.createConsumer(queue);

        AtomicLong sentCount = new AtomicLong();
        AtomicLong receivedCount = new AtomicLong();
        AtomicLong totalLatency = new AtomicLong();
        AtomicLong maxLatency = new AtomicLong();
        AtomicLong mismatches = new AtomicLong();

        // Consumer Thread
        new Thread(() -> {
            try {
                while (true) {
                    Message message = consumer.receive();
                    if (message == null) continue;

                    String msgId = message.getStringProperty("msgId");
                    long sendTime = message.getLongProperty("sendTime");

                    long now = System.nanoTime();
                    long latencyMicros = (now - sendTime) / 1_000;

                    totalLatency.addAndGet(latencyMicros);
                    maxLatency.updateAndGet(prev -> Math.max(prev, latencyMicros));

                    long count = receivedCount.incrementAndGet();

                    // Verify payload UUID matches
                    BytesMessage bm = (BytesMessage) message;
                    byte[] payload = new byte[(int) bm.getBodyLength()];
                    bm.readBytes(payload);
                    String payloadUuid = new String(payload); // payload is UUID string

                    if (!msgId.equals(payloadUuid)) {
                        long bad = mismatches.incrementAndGet();
                        System.err.println("Payload mismatch! Count=" + count + " Total mismatches=" + bad);
                    }

                    if (count % 10_000 == 0) {
                        System.out.println(String.format(
                                "Received: %d | Avg Latency(us): %d | Max Latency(us): %d | Mismatches: %d",
                                count,
                                totalLatency.get() / count,
                                maxLatency.get(),
                                mismatches.get()
                        ));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // Producer Loop
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < SEND_COUNT; i++) {
            BytesMessage msg = session.createBytesMessage();

            String uuid = UUID.randomUUID().toString();
            byte[] payload = uuid.getBytes(); // UUID as payload
            msg.writeBytes(payload);

            msg.setStringProperty("msgId", uuid);
            msg.setLongProperty("sendTime", System.nanoTime());

            long sendStart = System.nanoTime();
            producer.send(msg);
            long sendDurationMicros = (System.nanoTime() - sendStart) / 1_000;

            sentCount.incrementAndGet();

            if (i % 10_000 == 0) {
                System.out.println("Sent: " + i + " | Last send duration(us): " + sendDurationMicros);
            }

            if (SEND_DELAY_MS > 0) {
                Thread.sleep(SEND_DELAY_MS);
            }
        }

        long endTime = System.currentTimeMillis();
        System.out.println("Finished sending " + SEND_COUNT + " messages in " + (endTime - startTime) + " ms");
    }
}
