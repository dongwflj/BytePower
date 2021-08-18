package com.bytepower.bpworld.client;

import com.bytepower.common.grpc.BpProxyGrpc;
import com.bytepower.common.grpc.Message;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class BpWorldClient {
    private static final Logger logger = LoggerFactory.getLogger(BpWorldClient.class);

    private final ManagedChannel channel;
    private final BpProxyGrpc.BpProxyBlockingStub blockingStub;
    private final BpProxyGrpc.BpProxyStub asyncStub;
    private StreamObserver<Message> requestSender;

    public BpWorldClient(String host, int port) {
        ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true);
        channel = channelBuilder.build();
        // 创建一个阻塞客户端，支持简单一元服务和流输出调用服务
        blockingStub = BpProxyGrpc.newBlockingStub(channel);
        // 创建一个异步客户端，支持所有类型调用
        asyncStub = BpProxyGrpc.newStub(channel);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * 一元服务调用
     */
    public void sendCmd(String from) {
        logger.info(">>> sendCmd: from={}", from);
        Message req = Message.newBuilder().setName("CONNECT").setFrom(from).build();
        Message resp;
        try {
            resp = blockingStub.unifyCmd(req);
        } catch (StatusRuntimeException e) {
            logger.info("RPC failed: {}", e.getStatus());
            return;
        }
        logger.info("<<< sendCmd recv response:{}", resp);
    }

    /**
     * 双向流
     *
     * @throws InterruptedException
     */
    public void connect2Server() throws InterruptedException {
        logger.info(">>> connect2Server");
        StreamObserver<Message> requestObserver = asyncStub.bidirectionCmd(new StreamObserver<Message>() {
            @Override
            public void onNext(Message value) {
                logger.info("Stream receive message : {}", value);
            }

            @Override
            public void onError(Throwable t) {
                logger.error("onError: stream Failed: {}", Status.fromThrowable(t));
            }

            @Override
            public void onCompleted() {
                logger.info("onCompleted: Finished bindirconnect2ServerectionalStreamRpc");
            }
        });
        requestSender = requestObserver;
        logger.info("<<< connect2Server");
    }

    public void sendStreamCmd() {
        logger.info(">>> sendStreamCmd");
        Scanner sc = new Scanner(System.in);
        String input;
        do {
            logger.info("Please input a char to send bistream");
            input = sc.nextLine();
            logger.info("Your input: {}", input);
            try {
                Message req = Message.newBuilder().setCode(1).setName("CONNECT").setFrom("TestClient")
                        .setId("1234343434").build();
                logger.info("Sending message {}", req);
                requestSender.onNext(req);
            } catch (RuntimeException e) {
                // Cancel RPC
                requestSender.onError(e);
                throw e;
            }
        } while (input != "x");
        logger.info("<<< sendStreamCmd");
    }

    public static void main(String[] args) throws InterruptedException {
        logger.info("Start BpWorldClient...");
        ///BpWorldClient client = new BpWorldClient("10.11.97.13", 8980);
        BpWorldClient client = new BpWorldClient("127.0.0.1", 10080);
        try {
            // simple2 rpc
            String from = "r1@bp.com";
			client.sendCmd(from);
            client.connect2Server();
            client.sendStreamCmd();
        }
        finally {
            client.shutdown();
        }
    }
}
