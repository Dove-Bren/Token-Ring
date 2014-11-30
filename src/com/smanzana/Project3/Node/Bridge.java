package com.smanzana.Project3.Node;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import com.smanzana.Project3.Frame.Frame;

public class Bridge extends Node {
	
	private Socket bridgeSock;
	
	private boolean connected;
	
	private enum STDMessage {
		FINISH;
		
	}
	
	
	public Bridge(int tokenHoldingTime, byte address, ServerSocket sSock, int bridgePort) {
		super(tokenHoldingTime, address, sSock);
		
		try {
			bridgeSock = new Socket("127.0.0.1", bridgePort);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.out.println("Encountered Unknown Host Exception!\n "
					+ "Bridge is NOT connected!");
			bridgeSock = null;
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Encountered Unknown Host Exception!\n "
					+ "Bridge is NOT connected!");
			bridgeSock = null;
		}
		
		//bridgeSock will be null if connection issues arise
		
		if (bridgeSock == null) {
			connected = false;
		} else {
			connected = true;
		}
	}
	
	@Override
	public void run() {
		
		byte[] frame;
		boolean go = true;
		while (true) {
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
			}
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
		byte[] body = receive(Frame.Header.getSize(header) + 1, 200 + (Frame.Header.getSize(header) * 20));
		
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
		this.input = this.bridgeSock;
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
		data[0] = (byte) msg.ordinal();
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
		
		
		return false;
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
		
		OutputStream output = bridgeSock.getOutputStream();
		
		output.write(frame);
		output.flush();
		
	}

}
