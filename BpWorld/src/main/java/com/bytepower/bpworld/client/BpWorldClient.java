package com.bytepower.bpworld.client;

import com.hry.spring.grpc.mystream.HelloStreamGrpc;
import com.hry.spring.grpc.mystream.Simple;
import com.hry.spring.grpc.mystream.SimpleFeature;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BpWorldClient {
    private static final Logger logger = LoggerFactory.getLogger(BpWorldClient.class);

    private final ManagedChannel channel;
    private final HelloStreamGrpc.HelloStreamBlockingStub blockingStub;
    private final HelloStreamGrpc.HelloStreamStub asyncStub;

    private Random random = new Random();

    public BpWorldClient(String host, int port) {
        ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true);
        channel = channelBuilder.build();
        // 创建一个阻塞客户端，支持简单一元服务和流输出调用服务
        blockingStub = HelloStreamGrpc.newBlockingStub(channel);
        // 创建一个异步客户端，支持所有类型调用
        asyncStub = HelloStreamGrpc.newStub(channel);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * 一元服务调用
     */
    public void simpleRpc(int num) {
        logger.info(">>> simpleRpc: num={}", num);
        Simple simple = Simple.newBuilder().setName("simpleRpc").setNum(num).build();
        SimpleFeature feature;
        try {
            feature = blockingStub.simpleRpc(simple);
        } catch (StatusRuntimeException e) {
            logger.info("RPC failed: {}", e.getStatus());
            return;
        }
        logger.info("<<< simpleRpc end called {}", feature);
    }

    /**
     * 双向流
     *
     * @throws InterruptedException
     */
    public void bindirectionalStreamRpc() throws InterruptedException {
        logger.info(">>> bindirectionalStreamRpc");
        final CountDownLatch finishLatch = new CountDownLatch(1);
        StreamObserver<Simple> requestObserver = asyncStub.bindirectionalStreamRpc(new StreamObserver<Simple>() {
            @Override
            public void onNext(Simple value) {
                logger.info("bindirectionalStreamRpc receive message : {}", value);
            }

            @Override
            public void onError(Throwable t) {
                logger.error("onError: bindirectionalStreamRpc Failed: {0}", Status.fromThrowable(t));
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                logger.info("onCompleted: Finished bindirectionalStreamRpc");
                finishLatch.countDown();
            }
        });

        try {
            Simple[] requests = { newSimple(407838351), newSimple(2), newSimple(408122808), newSimple(4) };
            char i = 'a';
            while (i != 'x') {
                for (Simple request : requests) {
                    logger.info("Sending message {}", request);
                    requestObserver.onNext(request);
                }
                try {
                    logger.info("Please input a char...");
                    i = (char) System.in.read();
                    logger.info("your char is :"+i);
                }
                catch (IOException e) {
                    logger.info("IO exception :" + e);
                }
            }
        } catch (RuntimeException e) {
            // Cancel RPC
            requestObserver.onError(e);
            throw e;
        }
        logger.info("Call Server onComplete");
        requestObserver.onCompleted();
        logger.info("Call Server onComplete done");

        if (!finishLatch.await(1, TimeUnit.MINUTES)) {
            logger.error("routeChat can not finish within 1 minutes");
        }
        logger.info("<<< bindirectionalStreamRpc");
    }

    // 创建Simple对象
    private Simple newSimple(int num) {
        return Simple.newBuilder().setName("simple" + num).setNum(num).build();
    }

    public static void main(String[] args) throws InterruptedException {
        logger.info("Start BpWorldClient...");
        BpWorldClient client = new BpWorldClient("10.11.97.13", 8980);
        try {
            // simple2 rpc
			client.simpleRpc(1);
            logger.info("Please input a char to call bidirection stream");
            char c = (char) System.in.read();

//			// server2ClientRpc
//			client.server2ClientRpc(407838351, 413628156);
//
//			// client2ServerRpc
//			client.client2ServerRpc(10000);
//
//			// bindirectionalStreamRpc
            for (int i=0; i<2; i++) {
                client.bindirectionalStreamRpc();
                Thread.sleep(5000);
            }
            logger.info("Please input a char to exit");
            c = (char) System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {

            client.shutdown();
        }
    }
}
