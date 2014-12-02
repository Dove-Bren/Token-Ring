package com.smanzana.Project3.Node;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import com.smanzana.Project3.Project3;
import com.smanzana.Project3.Frame.Frame;

public class Bridge extends Node {
	
	/**
	 * The socket that the remote bridge will send data over
	 */
	private Socket bridgeIn;
	
	/**
	 * The socket this bridge can send data over
	 */
	private Socket bridgeOut;
	
	private boolean connected;
	
	private SocketAddress remoteAddress;
	
	public enum STDMessage {
		FINISH((byte) 1),
		KILL((byte) 2);
		
		public byte id;
		
		private STDMessage(byte id) {
			this.id = id;
		}
		
		public static STDMessage fromId(byte ID) {
			switch (ID) {
			case 1:
			default:
				return STDMessage.FINISH;
				//break;
			case 2:
				return STDMessage.KILL;
			}
		}
		
	}
	
	
	public Bridge(int tokenHoldingTime, byte address, int port, SocketAddress remoteBridgeAddr) {
		super(tokenHoldingTime, address, port);
		this.remoteAddress = remoteBridgeAddr;
	}
	
	@Override
	public void run() {
		
		setup();
		
		if (remoteAddress == null) {
			connected = false;
			System.out.println("\tBridge is in offline mode!");
			connected = false;
			//do not connect to remote bridge.
		} else {
		
			try {
				bridgeOut = new Socket();
				bridgeOut.connect(remoteAddress);
			} catch (UnknownHostException e) {
				System.out.println("Encountered Unknown Host Exception!\n "
						+ "Bridge is NOT connected!");
				bridgeOut = null;
			} catch (IOException e) {
				System.out.println("Encountered Unknown Host Exception!\n "
						+ "Bridge is NOT connected!");
				bridgeOut = null;
			}
			
			//bridgeSock will be null if connection issues arise
			
			if (bridgeOut == null) {
				connected = false;
			} else {
				connected = true;
				//We still need a way for this socket to receive data, so we send
				//a port we will now listen on
				byte offset = (byte) Project3.rand.nextInt(256);
				int tmpPort = 7000 + offset;
				
				ServerSocket sSock;
				try {
					sendBridge(new byte[] {offset});
				sSock = new ServerSocket();
				sSock.bind(new InetSocketAddress("127.0.0.1", tmpPort));
				bridgeIn = sSock.accept(); 
				sSock.close();
				} catch (IOException e) {
					e.printStackTrace();
					System.out.println("Error when creating bridge's input socket!");
					return;
				}
				System.out.println("Bridge has connected to the remote bridge!");
			}
		}
		
		byte[] frame;
		while (true) {
			
			if (connected) {
				frame = getBridgeFrame();
				
				if (frame != null) {
					if (processBridgeFrame(frame)) {
						try {
							kill();
						} catch (IOException e) {
							e.printStackTrace();
							System.out.println("ERROR when trying to kill bridge!!!!!!");
						}
						return;
					}
					continue;
				}
			}
			
			
			try {
				frame = getFrame();
			} catch (IOException e) {
				e.printStackTrace();
				System.out.println("Generated error when trying to fetch a frame!");
				return;
			}
			
			if (frame != null) {
				if (processRingFrame(frame)) {
					try {
						kill();
					} catch (IOException e) {
						e.printStackTrace();
						System.out.println("ERROR when trying to kill bridge!!!!!!");
					}
					return;
				}
				
			}
			
			//regardless of if we got a ring frame, now try and fetch/process a bridge frame
			//this is how we avoid starving the bridge of ring. We do one of either if they have it
			
//			if (!connected) {
//				continue;
//			}
//			frame = getBridgeFrame();
//			
//			if (frame != null) {
//				if (processBridgeFrame(frame)) {
//					try {
//						kill();
//					} catch (IOException e) {
//						e.printStackTrace();
//						System.out.println("ERROR when trying to kill bridge!!!!!!");
//					}
//					return;
//				}
//				return;
//			}
		}
		
		
	}
	
	/**
	 * Gets the next frame from the ring this bridge is part of.<br />
	 * This <b>does not</b> get a frame from the remote bridge. It only fetches from the ring.
	 * @return the frame, or null if no frame was ready
	 * @throws IOException
	 */
	private byte[] getFrame() throws IOException {
		
		byte[] header = receive(Frame.headerLength, 100);
		if (header == null) {
			//no frame ready to be picked up
			return null;
		}
		byte[] body = receive((Frame.Header.getSize(header) & 0xFF) + 1, 200 + (Frame.Header.getSize(header) * 20));
		
		byte[] frame = new byte[header.length + body.length];
		
		int i;
		for (i = 0; i < header.length; i++) {
			frame[i] = header[i];
		}
		for (; i < frame.length; i++) {
			frame[i] = body[i - header.length];
		}
		
		return frame;
	}
	
	/**
	 * Fetches a frame from the remote bridge.
	 * <br/>This is a very lazy implementation. I didn't want to go back and screw with the Node receive methods, so I did this 
	 * instead.
	 * @return The frame, or null timeout or error occur
	 */
	private byte[] getBridgeFrame() {
		Socket inputHolder = this.input;
		this.input = this.bridgeIn;
		byte[] frame;
		try {
			frame = getFrame();
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error when fetching a frame from the remote bridge!");
			frame = null;
		}
		finally {
			this.input = inputHolder; //super important we swap back in the reference to our actual input
		}
		return frame;
	}
	
	private void communicate(STDMessage msg) {
		//to denote a message TO the bridge itself, we use a source of 0
		//these special frames are going to carry the message in the data. The frame will obey all the same rules
		//as all other frames.
		//the data will be the enum constant STDMessage defines.
		byte[] data = new byte[1];
		data[0] = (byte) msg.id;
		byte[] frame = assembleFrame((byte) 0, 1, data);
		
		frame[3] = 0; //set source to 0
		
		try {
			sendBridge(frame);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	private boolean processRingFrame(byte[] frame) {
		if (Frame.Header.isToken(Frame.getHeader(frame))) {

			try {
				send(frame); //pass token
			} catch (IOException e) {
				System.out.println("Error passing token in bridge!");
			} 
			return false;
		}
		
		if (Frame.Header.isFinish(Frame.getHeader(frame))) {
			//communicate to remote that this ring is finished
			communicate(STDMessage.FINISH);
			//implicit drain of frame
			return false;
		}
		
		if (Frame.Header.isKill(Frame.getHeader(frame))) {
			//the kill finally came around, so kill self and be done
			return true;
		}
		
		//forward to remote
		try {
			sendBridge(frame);
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error when trying to forward frame to remote bridge!");
		}
		
		
		return false;
	}
	
	private boolean processBridgeFrame(byte[] frame) {
		
		//It's important to understand we can get two types of messages:
		//Message forwarded to this ring from the bridge
		//OR message from the bridge to this bridge node
		//messages to this bridge node will have source address 0 and 1 byte-wide data
		/*
		 * We will get a KILL frame from the bridge if any. If we get somethign else, we assume it was wrong.
		 */
		byte[] data = Frame.getData(frame);
		if (data == null) {
			return false;
		}
		
		if (Frame.Header.getSource(Frame.getHeader(frame)) == (byte) 0 && data.length == 1) {		
			//source was 0, size is 1, this is a communication frame
			System.out.println("Received a inter-bridge communication frame!");
			STDMessage msg = STDMessage.fromId(data[0]);
			
			
			switch (msg) {
			case KILL:
				System.out.println("Got a kill frame from remote! Forwarding!");
				passKill();
				return false;
			default:
				return false;
			}
		} else {
			System.out.println("Forwarding message from remote...");
			try {
				send(frame);
			} catch (IOException e) {
				e.printStackTrace();
				System.out.println("Encountered an error in the bridge when trying to forward a frame from the remote into the local");
			}
			return false;
		}
	}
	
	private void sendBridge(byte[] frame) throws IOException {
		if (!connected) {
			System.out.println("Silently ignoring send-request to unconnected bridge...");
			return;
		}
		
		if (frame == null) {
			System.out.println("Tried to send a null frame in bridge!");
			return;
		}
		
		OutputStream output = bridgeOut.getOutputStream();
		
		output.write(frame);
		output.flush();
		
	}

}
