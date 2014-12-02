package com.smanzana.Project3.Node;

import java.io.IOException;
import java.net.ServerSocket;

import com.smanzana.Project3.Project3;
import com.smanzana.Project3.Frame.Frame;
import com.smanzana.Project3.Frame.Token;

/**
 * Node that monitors the ring and makes sure everything is going well.<br />
 * Performs routine checks on frames that pass through it, such as verifying the validity of the frame
 * and frame health.<br />
 * Will systematically drain frames that are orphaned.
 * @author Skyler
 *
 */
public class Monitor extends Node {

	public Monitor(int tokenHoldingTime, byte address) {
		super(tokenHoldingTime, address);
	}
	
	@Override
	public void run() {
		//setup();
		
		while (true) {
			//We don't have messages to send, so we don't need to worry about if we have the token or not.
			//instead, just try and fetch the next message
			
			byte[] frame, header = null;
			
			try {
				header = receive(5, tokenHoldingTime * Project3.lastAddress * 165); //10 milliseconds * number of nodes * THT);
				//I spent a good amount of time figuring out what the scaling (/\) factor should be. I thought 10 millis would be
				//good, but in practice it thought the token had been dropped when there was no chance of that a lot. I went
				//to 165 after 120 and 100 and 80 and 50 and 40 and 35 and 30 and 25 and 20 and 15 and 10 because it
				//gave no invalid 'token dropped' about 80% of the time. This was much better than any I had tested.
			} catch (IOException e) {
				e.printStackTrace();
				System.out.println("Error in node [" + address + "] when trying to receive the next frame header!");
				continue;
			}
			
			//if header isn't null, we have a frame potentially waiting
			//if it is null, we have a problem. We waited the maximum time it should take for the token to go around.
			//e.g. the token was dropped somehwere and we need to generate a new one
			if (header == null) {
				System.out.println("Monitor has detected that the token was dropped!\nGenerating a new token...");
				drainRing();
				token = new Token(null);
				passToken();
				continue;
			}
			
			//we got ah eader and want a body
			int size, i;
			size = (Frame.Header.getSize(header) & 0xFF); //like in regular node, we want the actual size which goes from 0 to 255
			
			frame = new byte[size + 6]; //5 bytes for header, 1 bite for FS byte, size bytes for data
			for (i = 0; i < 5; i++) {
				frame[i] = header[i]; //copy over header into whole frame
			}
			byte[] body = null;
			try {
				body = receive(size + 1, 200 + (20 * size));
			} catch (IOException e) {
				e.printStackTrace();
				System.out.println("Error encountered in node [" + address + "] when trying to fetch the body of the frame!");
				continue;
			}
			
			if (body == null) {
				System.out.println("Error encountered in node [" + address + "] when trying to fetch the body of the frame!");
				continue;
			}
			
			for (byte b : body) {
				frame[i] = b;
				i++;
			}
			
			//have the completed frame TODO this todo is to make it easier to find this line
			if (Frame.Header.isToken(header)) { //check if it's token
				token = new Token(frame);
				//Behind the scenes magic.
				//We store extra information in the token. To figure out if anyone is using the ring (and if not, close it)
				//we use the most significant bit in the FS byte to denote whether any node transmitted data (the ring is in use)
				//if it comes to the monitor as 0, no nodes used it since it was last at the monitor
				if (token.wasUsed()) {
					//it was set. The ring is in use.
					//set it bck to 0 
					token.setUsed(false);
					passToken();
					continue;
				}
				
				//else the ring is not in use and the token needs be drained. In it's place, issue a FINISH TOKEN
				//defined (by me) to be a token with the value 3 for the FC byte
				passFinish();
				//don't send the token, effectively draining it from the ring.
				continue;
			}
			if (Frame.Header.isKill(header)) { //else is it a kill frame?
				passKill();
				try {
					kill();
					System.out.println("Monitor has been killed. All nodes should now be killed.");
				} catch (IOException e) {
					e.printStackTrace();
					System.out.println("Encounted error killing the monitor node!");
					return;
				}
				return;
			}
			//neither token nore kill. Just a regular frame. Error checking!
			//make sure it's a valid frame. Check sizes against eachother
			if ((Frame.Header.getSize(header) & 0xFF) != Frame.getData(frame).length) {
				System.out.print("Monitor found error in frame from [" + Frame.Header.getSource(header) + "]!\n"
						+ "Sizes do not match!\n"
						+ "Draining ring...  ");
				//drain
				drainRing();
				System.out.println("Ring drained!");
				
				System.out.println("Monitor ssuing new token.");
				token = new Token(null); //make generic token
				passToken();
				continue;
			}
			//next, make sure it hasn't been orphaned (been to the monitor node twice, indicating it wasn't drained by its source)
			if (Frame.Header.getMonitor(header)) { //if monitor bit is set to 1, indicating we've seen it already
				System.out.println("Monitor found orphaned frame with source [" + Frame.Header.getSource(header) + "]!\n"
						+ "Draining..");
				continue; //draining means just don't retransmit it
			}
			
			
			//else just transmit the frame along. We do take time here to quickly set the monitor bit to 1 to indicate
			//we've seen this frame before
			byte AC = frame[0];
			AC = (byte) (AC | 8);//XXXX XXXX | 0000 1000 = XXXX 1XXXX  -- set the monitor bit to 1 and leave everything else the same
			frame[0] = AC;
			try {
				
				
				
				send(frame);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			
			
		}
		
		
	}
	
	private void drainRing() {
		byte rec[];
		while (true) {
			rec = null;
			//start draining the ring 1 byte at a time.
			//we will have a timeout time of just over the total amount of time it would take for anything to get around the ring
			try {
				rec = receive(1, tokenHoldingTime * Project3.lastAddress * 50); //50 milliseconds * number of nodes * THT
				//we wait the up to the whole time for each byte to be on the safe side
			} catch (IOException e) {
				e.printStackTrace();
				System.out.println("Error encountered when monitor node was draining ring!\n"
						+ "Ring may not have been drained properly!");
				return;
			}
			
			//if our byte is empty, we waited the whole time and got nothing, meaning the ring is empty
			if (rec == null) {
				return;
			}
			//else continue
			
		}
	}

}
