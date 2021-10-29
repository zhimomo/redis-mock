package com.github.microwww.redis.protocal.operation;

import com.github.microwww.AbstractRedisTest;
import com.github.microwww.redis.exception.Run;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.BinaryJedisPubSub;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PubSubOperationTest extends AbstractRedisTest {
    private static final Logger logger = LoggerFactory.getLogger(PubSubOperationTest.class);

    //PSUBSCRIBE
    @Test(timeout = 1_000)
    public void testPSUBSCRIBE() {
        byte[][] channels = {"PSUBSCRIBE.*.test".getBytes(StandardCharsets.UTF_8),
                "PSUBSCRIBE.test.*".getBytes(StandardCharsets.UTF_8)};
        CountDownLatch down = new CountDownLatch(channels.length);
        new Thread(() -> {
            try {
                down.await();
                long cont = this.connection().publish("PSUBSCRIBE.test.1.test".getBytes(), UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
                Assert.assertEquals(2, cont);
            } catch (Exception e) {
            }
        }).start();
        // subscribe will block
        jedis.psubscribe(new BinaryJedisPubSub() {
            @Override
            public void onPMessage(byte[] pattern, byte[] channel, byte[] message) {
                logger.info("PMessage :{}, {}, {}", new String(pattern), new String(channel), new String(message));
                this.punsubscribe(pattern);
            }

            @Override
            public void onPSubscribe(byte[] pattern, int subscribedChannels) {
                logger.info("PSubscribe :{}, {}, {}", new String(pattern), subscribedChannels);
                down.countDown();
            }

            @Override
            public void onPUnsubscribe(byte[] pattern, int subscribedChannels) {
                logger.info("PUnsubscribe :{}, {}", new String(pattern), subscribedChannels);
            }
        }, channels);
    }

    //PUBLISH
    //PUBSUB
    //PUNSUBSCRIBE
    @Test(timeout = 1_000)
    public void testPUNSUBSCRIBE() {
        byte[][] channels = {"PUNSUBSCRIBE.*.test".getBytes(StandardCharsets.UTF_8),
                "PUNSUBSCRIBE.test.*".getBytes(StandardCharsets.UTF_8)};
        CountDownLatch down = new CountDownLatch(channels.length);
        new Thread(() -> {
            try {
                down.await();
                long cont = this.connection().publish("PUNSUBSCRIBE.test.1.test".getBytes(), UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
                Assert.assertEquals(2, cont);
            } catch (Exception e) {
            }
        }).start();
        // subscribe will block
        jedis.psubscribe(new BinaryJedisPubSub() {
            int i = 0;

            @Override
            public void onPMessage(byte[] pattern, byte[] channel, byte[] message) {
                logger.info("PMessage :{}, {}, {}", new String(pattern), new String(channel), new String(message));
                i++;
                if (i == 2) {
                    this.punsubscribe();
                }
            }

            @Override
            public void onPSubscribe(byte[] pattern, int subscribedChannels) {
                logger.info("PSubscribe :{}, {}, {}", new String(pattern), subscribedChannels);
                down.countDown();
            }

            @Override
            public void onPUnsubscribe(byte[] pattern, int subscribedChannels) {
                logger.info("PUnsubscribe :{}, {}", new String(pattern), subscribedChannels);
            }
        }, channels);
    }

    //SUBSCRIBE
    @Test(timeout = 1_000)
    public void testSUBSCRIBE() {
        byte[][] channels = {"test0".getBytes(StandardCharsets.UTF_8),
                "test1".getBytes(StandardCharsets.UTF_8),
                "test2".getBytes(StandardCharsets.UTF_8)};
        CountDownLatch down = new CountDownLatch(1);
        new Thread(() -> {
            try {
                down.await();
                long cont = this.connection().publish(channels[0], UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
                Assert.assertEquals(1, cont);
            } catch (Exception e) {
            }
        }).start();
        // subscribe will block
        jedis.subscribe(new BinaryJedisPubSub() {
            int less = 0;

            @Override
            public void onPong(byte[] pattern) {// TODO :: when ?
                logger.info("TODO: pong, any time ??");
            }

            @Override
            public void onSubscribe(byte[] channel, int subscribedChannels) {
                logger.info("Subscribe channel: {} ok, {}", new String(channel), subscribedChannels);
                less++;
                Assert.assertEquals(less, subscribedChannels);
                down.countDown();
            }

            @Override
            public void onMessage(byte[] channel, byte[] message) {
                logger.info("Channel: {}, message: {}", new String(channel), new String(message));
                this.unsubscribe();
            }

            @Override
            public void onUnsubscribe(byte[] channel, int subscribedChannels) {
                logger.info("Unsubscribe Channel: {}, less {}", new String(channel), subscribedChannels);
                less--;
                Assert.assertEquals(less, subscribedChannels);
            }
        }, channels);
    }

    //UNSUBSCRIBE
    @Test(timeout = 1_000)
    public void testUNSUBSCRIBE() throws ExecutionException, InterruptedException {
        int count = 5;
        byte[][] channels = {"test0".getBytes(StandardCharsets.UTF_8),
                "test1".getBytes(StandardCharsets.UTF_8),
                "test2".getBytes(StandardCharsets.UTF_8)};
        CountDownLatch down = new CountDownLatch(channels.length * count);
        threads.execute(() -> {
            try {
                down.await();
                for (byte[] channel : channels) {
                    long clients = this.connection().publish(channel, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
                    Assert.assertEquals(count, clients);
                }
            } catch (Exception e) {
            }
        });
        // subscribe will block
        Callable<Integer> task = () -> {
            connection().subscribe(new BinaryJedisPubSub() {
                @Override
                public void onPong(byte[] pattern) {
                    System.out.println("pong");
                }

                @Override
                public void onSubscribe(byte[] channel, int subscribedChannels) {
                    logger.info("Subscribe channel: {} ok, {}", new String(channel), subscribedChannels);
                    down.countDown();
                }

                @Override
                public void onMessage(byte[] channel, byte[] message) {
                    logger.info("Channel: {}, message: {}", new String(channel), new String(message));
                    this.unsubscribe(channel);
                }
            }, channels);
            return 0;
        };
        IntStream.range(0, count).mapToObj(i -> {
            return threads.submit(task); // TASK
        }).collect(Collectors.toList()).stream().forEach(e -> {
            Run.silentException(e::get);
        });
    }

    @Test(timeout = 1_000)
    public void test_SUBSCRIBE_same() throws ExecutionException, InterruptedException {
        int count = 5;
        byte[][] channels = {"test0".getBytes(StandardCharsets.UTF_8),
                "test1".getBytes(StandardCharsets.UTF_8),
                "test0".getBytes(StandardCharsets.UTF_8)};
        CountDownLatch down = new CountDownLatch(channels.length * count);
        threads.execute(() -> {
            try {
                down.await();
                for (int i = 0; i < 2; i++) {
                    long clients = this.connection().publish(channels[i], UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
                    logger.info("Publish channel: {}, {}", new String(channels[i]), clients);
                    Assert.assertEquals(count, clients);
                }
                long clients = this.connection().publish(channels[2], UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
                logger.info("Publish channel: {}, {}", new String(channels[0]), clients);
                Assert.assertEquals(0, clients);
            } catch (Exception e) {
            }
        });
        // subscribe will block
        Callable<Integer> task = () -> {
            connection().subscribe(new BinaryJedisPubSub() {
                @Override
                public void onPong(byte[] pattern) {
                    System.out.println("pong");
                }

                @Override
                public void onSubscribe(byte[] channel, int subscribedChannels) {
                    logger.info("Subscribe channel: {} ok, {}", new String(channel), subscribedChannels);
                    down.countDown();
                }

                @Override
                public void onMessage(byte[] channel, byte[] message) {
                    logger.info("Channel: {}, message: {}", new String(channel), new String(message));
                    this.unsubscribe(channel);
                }
            }, channels);
            return 0;
        };
        IntStream.range(0, count).mapToObj(i -> {
            return threads.submit(task); // TASK
        }).collect(Collectors.toList()).stream().forEach(e -> {
            Run.silentException(e::get);
        });
    }
}