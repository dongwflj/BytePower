package com.bytepower.bpworld.core;

import com.bytepower.common.grpc.BpServerGrpc;
import com.bytepower.common.grpc.BpProxyGrpc;
import com.bytepower.common.grpc.Message;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;

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
		private HashMap<String, BpServerGrpc.BpServerBlockingStub> proxyContactMap;
		private HashMap<String, BpServerGrpc.BpServerBlockingStub> proxyFromMap;

		public BpServerService() {
			proxyContactMap = new HashMap<>();
			proxyFromMap = new HashMap<>();
		}

		@Override
		public void unifyServerCmd(Message message, StreamObserver<Message> responseObserver) {
			logger.info(">>> unifyServerCmd() coreServer recv request:{}", message);
			Message resp = Message.newBuilder().setName("fromBpCoreServer").setCode(200).build();
			responseObserver.onNext(resp);
			responseObserver.onCompleted();
			String cmdName = message.getName();
			if (cmdName.equals("CONNECT")) {
				String[] address = message.getContact().split(":");
				if (address.length == 2) {
					ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder.forAddress(address[0],
							Integer.parseInt(address[1])).usePlaintext(true);
					ManagedChannel channel = channelBuilder.build();
					BpServerGrpc.BpServerBlockingStub blockingStub = BpServerGrpc.newBlockingStub(channel);
					proxyContactMap.put(message.getContact(), blockingStub);
					proxyFromMap.put(message.getFrom(), blockingStub);

					Message notify = Message.newBuilder().setName("NOTIFY").setBody("From core server").build();
					Message notifyResp = blockingStub.unifyServerCmd(notify);
					logger.info("Core server recv notify resp:{}", notifyResp);
				} else {
					logger.error("Proxy address is not valid:{}", address);
				}
			}
			logger.info("<<< unifyServerCmd() coreServer send response:{}", resp);
		}
	}
}