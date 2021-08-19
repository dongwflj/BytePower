package com.bytepower.bpworld.proxy;

import com.bytepower.common.grpc.BpProxyGrpc;
import com.bytepower.common.grpc.Message;
import com.bytepower.common.grpc.BpServerGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;

public class BpProxyServer {
	private static final Logger logger = LoggerFactory.getLogger(BpProxyServer.class);
	private final int port;
	private final Server server;

	public BpProxyServer(int port) throws IOException {
		this.port = port;
		this.server = ServerBuilder.forPort(port).addService(new BpProxyService())
									.addService(new BpServerService()).build();
	}

	// 启动服务
	public void start() throws IOException {
		server.start();
		logger.info("BpProxyServer started, listening on " + port);
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				System.err.println("*** shutting down gRPC server since JVM is shutting down");
				BpProxyServer.this.stop();
				System.err.println("*** server shut down");
			}
		});
	}

	// 启动服务
	public void stop() {
		if (server != null) {
			server.shutdown();
		}
	}

	/**
	 * Await termination on the main thread since the grpc library uses daemon
	 * threads.
	 */
	private void blockUntilShutdown() throws InterruptedException {
		if (server != null) {
			server.awaitTermination();
		}
	}

	public static void main(String[] args) throws Exception {
		BpProxyServer server = new BpProxyServer(10080);
		server.start();
		server.blockUntilShutdown();
	}

	/**
	 * 服务端类的实现
	 *
	 */
	private static class BpServerService extends BpServerGrpc.BpServerImplBase {
		public BpServerService() {
		}
		@Override
		public void unifyServerCmd(Message message, StreamObserver<Message> responseObserver) {
			logger.info(">>> unifyServerCmd() proxyServer recv server request:{}", message);
			Message resp = Message.newBuilder().setName("NOTIFYRESP").setCode(200).build();
			responseObserver.onNext(resp);
			responseObserver.onCompleted();
			logger.info("<<< unifyServerCmd() proxyServer send response:{}", resp);
		}
	}

	private static class BpProxyService extends BpProxyGrpc.BpProxyImplBase {
		private final ManagedChannel channel;
		private final BpServerGrpc.BpServerBlockingStub blockingStub;

		public BpProxyService() {
			// Client role
			String coreServerAddr = "127.0.0.1";
			int coreServerPort = 10090;
			ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder.forAddress(coreServerAddr, 10090).usePlaintext(true);
			channel = channelBuilder.build();
			// 创建一个阻塞客户端，支持简单一元服务和流输出调用服务
			blockingStub = BpServerGrpc.newBlockingStub(channel);
		}

		@Override
		public void unifyCmd(Message message, StreamObserver<Message> responseObserver) {
			logger.info(">>> unifyCmd() BpProxy recv cmd:{}", message);
			Message proxyReq = Message.newBuilder(message).setContact("127.0.0.1:10080").build();
			logger.info("Proxy add contact for message:{}", proxyReq);
			Message resp = blockingStub.unifyServerCmd(proxyReq);
			responseObserver.onNext(resp);
			responseObserver.onCompleted();
			logger.info("<<< unifyCmd() BpProxy send response:{}", resp);
		}

		/**
		 * 每接收一个请求，立即返回一个对象
		 */
		@Override
		public StreamObserver<Message> bidirectionCmd(final StreamObserver<Message> responseObserver) {
			return new StreamObserver<Message>() {
				@Override
				public void onNext(Message value) {
					logger.info(">>> bidirectionCmd client stream onNext()：content={} ", value);
					Message resp = Message.newBuilder().setName("fromBpProxy").setCode(200).build();
					responseObserver.onNext(resp);
					logger.info("<<< bidirectionCmd");
				}

				@Override
				public void onError(Throwable t) {
					logger.error("bidirectionCmd client stream error cancelled, e={}", t);
				}

				@Override
				public void onCompleted() {
					logger.info(">>> bidirectionCmd client stream onCompleted()");
					responseObserver.onCompleted();
					logger.info("<<< bidirectionCmd onCompleted()");
				}
			};
		}
	}
}