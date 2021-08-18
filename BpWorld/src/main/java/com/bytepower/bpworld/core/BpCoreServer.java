package com.bytepower.bpworld.core;

import com.bytepower.common.grpc.BpServerGrpc;
import com.bytepower.common.grpc.Message;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class BpCoreServer {
	private static final Logger logger = LoggerFactory.getLogger(BpCoreServer.class);

	private final int port;
	private final Server server;

	public BpCoreServer(int port) throws IOException {
		this.port = port;
		this.server = ServerBuilder.forPort(port).addService(new BpServerService()).build();
	}

	// 启动服务
	public void start() throws IOException {
		server.start();
		logger.info("BpCoreServer started, listening on " + port);
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				System.err.println("*** shutting down gRPC server since JVM is shutting down");
				BpCoreServer.this.stop();
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
		BpCoreServer server = new BpCoreServer(10090);
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
			logger.info(">>> unifyServerCmd() coreServer recv request:{}", message);
			Message resp = Message.newBuilder().setName("fromBpCoreServer").setCode(200).build();
			responseObserver.onNext(resp);
			responseObserver.onCompleted();
			logger.info("<<< unifyServerCmd() coreServer send response:{}", resp);
		}
	}
}