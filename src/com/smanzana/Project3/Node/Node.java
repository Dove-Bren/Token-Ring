package com.smanzana.Project3.Node;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;

import com.smanzana.Project3.Project3;
import com.smanzana.Project3.Frame.Frame;
import com.smanzana.Project3.Frame.Token;

/**
 * A simple node that works both as a transmitter as well as a piece of a pipe.<br />
 * Nodes will have data they each need to send, but will wait to transmit until they receive a token.
 * This follows the token-ring model we are emulating.<br />
 * Nodes will always shift data that doesn't belong to them or that isn't a token regardless of if they have the token
 * or not -- acting as a pipe.
 * @author Skyler
 */
public class Node extends Thread {
	
	protected Socket input, output;
	protected int port;
	private boolean hasToken;
	private List<String> messages, sentMessages;
	protected byte address;
	protected int tokenHoldingTime, framesTransferred;
	private File outputFile;
	private PrintWriter writer;
	protected Token token;
	
	
	/**
	 * Creates a node with the passed socket. The node has no message it needs to send and does not have the token.<br />
	 * The node needs a holding time and address for obvious reasons, but also needs a server socket.
	 * @param tokenHoldingTime How many frames this node can send upon receipt of the token
	 * @param address A byte-wide address of this node. This is pretty much the node's ID
	 */
	public Node(int tokenHoldingTime, byte address, int port) {
		this.tokenHoldingTime = tokenHoldingTime;
		this.address = address;
		this.token = null;
		this.port = port;
		hasToken = false;
		messages = new LinkedList<String>(); //messages we need to send
		sentMessages = new LinkedList<String>(); //messages we have sent but have yet to receive ack
		if (this.output == null) {
			this.output = new Socket();
		}
		//set out output
		outputFile = new File("output-file-" + address);
		if (!outputFile.exists()) {
			try {
				outputFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
				System.out.println("Error when trying to create output file for node [" + address + "]!!!\n\n\n");
			}
		}
		

		try {
			writer = new PrintWriter(outputFile);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Connects the node to the provided socket
	 * @param addr
	 */
	public void connect(SocketAddress addr) {
		if (output == null || output.isClosed()) {
			System.out.println("Unable to connect the socket because it is either closed or doesn't exist.");
			return;
		}
		if (output.isConnected()) {
			System.out.println("Socket is already connected! Attempting to connect anyways..");
		}
		
		try {
			output.connect(addr);
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error when trying to connect in node [" + address + "] !\n\n");
			return;
		}
	}
	
	public void listen() {
		
		ServerSocket sSock = null;
		try {
			sSock = new ServerSocket();
			sSock.bind(new InetSocketAddress("127.0.0.1", port));
		} catch (IOException e) {
			System.out.println("Error when creating server socket!");
			return;
		}
		
		try {
			input = sSock.accept();
			sSock.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error when trying to listen on server socket in node [" + address + "] !\n\n");
		}
	}
	
	/**
	 * Begins the repeated execution of the node's processes, including:<br />
	 * <ul>
	 * <li>Checking for input from socket</li>
	 * <li>Processing any input received</li>
	 * <li>Correctly handling frames that do not belong to this node</li>
	 * </ul>
	 */
	public void run() {
		
		setup();
			
		while (true) {
			if (output == null || output.isClosed() || !output.isConnected()) {
				//System.out.println("Error in node with address " + address + "!\nOutput socket is either closed, not connected, or "
						//+ "it doesn't exist!");
				continue;
			}
			if (input == null || input.isClosed() || !input.isConnected()) {
				System.out.println("Error in node with address " + address + "!\nInput socket is either closed, not connected, or "
						+ "it doesn't exist!");
				continue;
			}
			
			//assume everything is okay. We don't care about server socket
			if (this.hasToken) {
				if (framesTransferred >= tokenHoldingTime) {
					//we have passed all the frames we can, so we need to pass the token
					this.hasToken = false;
					Random rand = new Random();
					if (rand.nextInt(20) == 0) {//5% for testing purposes.
						System.out.println("Node [" + address + "] dropping the token, for testing purposes :D");
					}
					else {
						passToken(); 		
					}
					continue;
				}
				//has token, so pass our own frames
								
				if (messages.isEmpty()) {
					//pass token, because we don't need it
					hasToken = false;
					passToken();
					continue;
				}
				else {
					//have a frame to transfer
					try {
						//input is in format:
						//   <Destination>, <size of data>, <data>
						String msg = messages.get(0), pieces[];
						pieces = msg.split(","); //told they are separated by commas
						
						if (pieces == null || pieces.length < 3) {
							//wrong format
							System.out.println("Invalid input format for node [" + address + "]:\n" + msg + "\n"
									+ "Skipping this line in input...");
							messages.remove(0);
							continue;
						}
						
						//address is first piece
						byte addr = Byte.parseByte(pieces[0]);
						int size = Integer.parseInt(pieces[1]);
						byte data[] = pieces[2].getBytes("UTF-8");
						
						byte frame[] = assembleFrame(addr, size, data);
						
						send(frame);
					} catch (IOException e) {
						e.printStackTrace();
						System.out.println("Error when trying to send a message in node [" + address + "]!\n"
								+ "IOException generated when trying to send.");
						continue;
					}
					
	
					sentMessages.add(messages.remove(0)); //transfer the message from 'need to send' to 'sent, waiting ack'
					framesTransferred++; //keep count of how many 
					//make sure the token has it's 'use' bit set
					token.setUsed(true);
					continue;
				}
			}
			
			//if it gets here, it either doesn't have the token or it doesn't have a message of its own to send.
			//either way, we check our input and make sure we don't have anything that needs dealt with or passed along
			int avail = 0;
			
			try {
				avail = input.getInputStream().available();
			} catch (IOException e) {
				e.printStackTrace();
				System.out.println("Error encounted when trying to get the input stream for the socket of node [" + address + "]!");
				continue;
			}
			
			if (avail <= 0) {
				//has nothing to receive
				continue;
			}
			
			//has something to receive
			byte[] header = new byte[5]; //5 bytes. This is taken straight from our specified frame specs given in the pdf!!!!
										 //In this way, this is a magic number!!!
			try {
				header = receive(5, 50); //tries to get the 5 byte header with a timeout time of only 50 millis
			} catch (IOException e) {
				e.printStackTrace();
				System.out.println("Error in node [" + address + "]! Encountered error when trying to receive header!");
				continue;
			}
			
			//is header null? If so, we just timed out... :(
			if (header == null) {
				continue;
			}
			
			//we got our header!
			//now how big is our frame supposed to be?
			int size = (Frame.Header.getSize(header) & 0xFF); //the byte, when type cast to an int, keeps its sign. We want this as
															//as an unsigned 0-255 instead of -128 - 127. So we do an AND, which
															//keeps the bits but fills with 0s to be an int
			
			if (size == -1) {
				//error occurred, as indicated in Frame.Header.getSize()
				System.out.println("Error occured when trying to parse frame header in node [" + address + "]!\n"
						+ "Discarding bad header and continuing from here...");
				continue;
			}
			
			byte[] body = null;// = new byte[size + 1]; //+1 so we can get the FS byte at the end as well
			
			try {
				body = receive(size + 1, 200 + (20 * size));//wait 200 milliseconds + 20 per extra byte cause reality
			} catch (IOException e) {
				e.printStackTrace();
			} 
			
			if (body == null) {
				System.out.println("Node [" + address + " timed out when waiting for message from " + Frame.Header.getSource(header));
				//this is a big deal because we already have the header so we lost the packet somewhere!!
				continue;
			}
			
			byte[] frame = new byte[header.length + body.length];
			for (int i = 0; i < header.length + body.length; i++) {
				if (i < header.length) {
					frame[i] = header[i];
				}
				else {
					frame[i] = body[i - header.length];
				}
			}
			
			//is this a kill frame?
			if (Frame.Header.isKill(header)) {
				passKill();
				try {
					kill();
				} catch (IOException e) {
					e.printStackTrace();
					System.out.println("Error when trying to kill node [" + address + "]!!");
				}
				return;
			}
			
			//Is this the token?
			if (Frame.Header.isToken(header)) {
				this.hasToken = true;
				framesTransferred = 0;
  				token = new Token(frame);
  				
  				//receiving the token means any messages in our sentMessage list never was sent back an ACK following that the token
  				//is sent after the original message, which isn't ever sent out of order in relation to the token (even with priority)
  				if (sentMessages.isEmpty()) {
  					continue; //nothing in the list so we just ignore for now
  				}
  				
  				//instead of just copying over the messages to the end of the list, or pushing to the front of the list, we
  				//walk the list backward and push to the front. This preserves the order and makes the messages that never
  				//got ack be sent right away before sending even more messages
  				ListIterator<String> it = sentMessages.listIterator(sentMessages.size() - 1); //iterator set to the last message
  				messages.add(0, it.next()); //adds element[size() - 1]
  				it.previous(); //need to step backwards so we dont get a duplicate
  				while (it.hasPrevious()) {
  					messages.add(0, it.previous());
  				}
  				
  				sentMessages.clear();
  				
				continue;
			}
			
			//we got the header and the body. Or at least enough bytes to pretend we did
			
			//check dest. and source. If we are dest, process as receipt. If we are source,
			//process as a drain. If neither, pass along.
			if (Frame.Header.getDestination(header) == address) {
				//we are destination! Accept it!
				
				//FS is frame[length - 1]
				
				//Before accepting/rejecting the frame, make sure it hasn't already been received and is orphaned.
				//this is done by making sure the FS byte is 0 instead of 2 or 3 or anything else.
				if (frame[frame.length - 1] != 0) {
					//was already received and marked as such
					//just pass it along
					try {
						send(frame);
					} catch (IOException e) {
						e.printStackTrace();
					}
					continue;
				}
				
				//we have to decide if we're going to accept or reject, as specifies in the PDF
				//have a 20 percent chance of rejecting, or 1/5
				Random rand = new Random();
				if (rand.nextInt(5) == 0) { //if (0, 1, 2, 3, or 4) == 0
					//.2 chance of getting here
					frame[frame.length-1] = 3; //rejected
					System.out.println("Node [" + address + "] rejected incoming frame as part of testing...");
				}
				else {
					frame[frame.length - 1] = 2;
					//if we're here, it means it's our message and we need to process it
					//as described in project specs, we write the addresses, size, and data to output file
					byte[] data = Frame.getData(frame);
					writer.println(Frame.Header.getSource(header) + "," + address + "," + size + "," + new String(data));
										
					writer.flush();
				}
				try {
					send(frame);
				} catch (IOException e) {
					e.printStackTrace();
					System.out.println("Error encountered when node [" + address + "] was trying to pass back an accepted frame!");
				}
				continue;
			}
			else if (Frame.Header.getSource(header) == address) {
				//We are the source. Did it make to the destination??
				//check the FS byte
				byte FS = Frame.getFrameStatus(frame);
				if (FS == -1) {
					//error
					System.out.println("Encountered non-fatal error when processing incoming message from [" + Frame.Header.getSource(header) + "] "
							+ "in node [" + address + "] : FS is set to -1!");
					continue; //drains the frame by not sendin git
				}
				if (FS == 2) {
					//it was accepted. Drain it.
					//Actually, simulate the 'orphaning' of a frame here. We are told we have a 
					
					//we needto clear it out of our sendMessages list to indicate it's been transferred and everything worked
					//Assemble original message from frame
					String msg;
					//<dest>,<size>,<data>
					msg = "";
					msg += Frame.Header.getDestination(header) + ",";
					msg += (Frame.Header.getSize(header) & 0xFF) + ",";
					msg += new String(Frame.getData(frame));
					sentMessages.remove(msg);
				}
				else if (FS == 3){
					//rejected
					//have to add frame to be sent again
					//have to fetch message form back from the frame format
					String msg;
					//<dest>,<size>,<data>
					msg = "";
					msg += Frame.Header.getDestination(header) + ",";
					msg += (Frame.Header.getSize(header) & 0xFF) + ",";
					msg += new String(Frame.getData(frame));
					//push messages back to the front of the queue
					messages.add(0, msg); //sets it to front of the list
					sentMessages.remove(msg);
				}
				else if (FS == 0) {
					continue;
				}
				
				//Regardless of if it was properly received or rejected, we now have a chance of 'forgetting' to 
				//drain the frame. Chance is ~2%. WE're going to make ours exactly 2%
				Random rand = new Random();
				int roll = rand.nextInt(100);
				if (roll < 2) { //if roll == 0 or 1, where roll is 0-99 inclusive
					//transfer the frame, simulating 'forgetting to drain'.
					System.out.println("Node [" + address + "] 'forgetting' to drain own frame!");
					try {
						send(frame);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				
			}
			else {
				//not our frame, just pass it on
				try {
					send(frame);
					continue;
				} catch (IOException e) {
					e.printStackTrace();
					System.out.println("Error when passing token originally from [" + Frame.Header.getSource(header) + "] in node "
							+ "[" + address + "]!");
					continue;
				}
			}
		}
	}
	
	public void setup() {
		
		
		System.out.println("node " + address + " listening to port " + port + "...");
		listen();
		System.out.println("node " + address + " connecting to port " + (port - 1) + "...");
		connect(new InetSocketAddress("127.0.0.1", port - 1));
		System.out.println("node " + address + " connected!");

	}
	
	/**
	 * Kills the node. This deals with the socket held by the node.
	 * @throws IOException 
	 */
	public void kill() throws IOException {
		System.out.println("Killing node [" + address + "]");
		this.messages.clear();
		this.messages = null;
		if (input != null && !input.isClosed())
			input.close();
		if (output != null && !output.isClosed())
			output.close();
		writer.close();
	}
	
	/**
	 * Attempts to send the passed bytes through the output socket
	 * @param message
	 * @throws IOException
	 */
	protected void send(byte[] message) throws IOException {
		if (output == null || output.isClosed()) {
			System.out.println("Error when trying to transmit frame by node with address " + address + "!\n"
					+ "Output Socket is closed or does not exist!");
			return;
		}
		
		output.getOutputStream().write(message);
		output.getOutputStream().flush();
		
	}
	
	/**
	 * Reads in any incoming message from the input socket and returns it.<br />
	 * This method waits for input if none currently exist.
	 * @throws IOException 
	 */
	protected byte[] receive() throws IOException {
		int size;
		byte[] data;
		InputStream ins;
		ins = input.getInputStream();
		size = ins.available();
		
		if (size < 1) {
			//less than to cover options
			size = 16; 
		}
		
		data = new byte[size];
		
		ins.read(data);
		
		
		return data;
	}
	
	/**
	 * Received the passed number of bytes of information from the underlying input Socket.<br />
	 * This method will block until there are enough bytes ready to be read to satisfy the demand. With great power...
	 * @param size How many bytes to read 
	 * @return
	 * @throws IOException
	 */
	protected byte[] receive(int size) throws IOException {
		byte[] data;

		InputStream ins;
		ins = input.getInputStream();
		
		data = new byte[size];
		
		while (ins.available() < size) {
			try {
				sleep(10); //to avoid trying every single cycle, sleep a few milliseconds and then try again
			} catch (InterruptedException e) {
				//Do nothing. An interupted thread is an interupted thread. WE are sleeping to waste time so this doesn't matter.
				//at least how I understand it.
			}
		}
		
		ins.read(data);
		
		
		return data;
		
	}
	
	/**
	 * Tries to get the passed amount of bytes from the input socket.<br />
	 * This method will block until the number of bytes is ready to be ready, or it times out.<br />
	 * Timeout time is approximate. In reality, it will be rounded up to the next multiple of 10 milliseconds.
	 * <p>
	 * If, after the timeout period, the socket still does not have enough bytes of data to pass through this method,
	 * it will instead return null. It will not read any bytes nor modify the socket in any way, even if the socket has
	 * some amount of bytes (that's less than the requested size) ready to be delivered.
	 * </p>
	 * @param size How many bytes to try and receive
	 * @param timeout The time until this method times out, in milliseconds
	 * @return an array list of bytes read, or null if it timed out
	 * @throws IOException
	 */
	protected byte[] receive(int size, int timeout) throws IOException {
		byte[] data;
		int sleepCount = 0;

		InputStream ins;
		ins = input.getInputStream();
		
		data = new byte[size];
		
		while (ins.available() < size) {
			try {
				sleep(10); //to avoid trying every single cycle, sleep a few milliseconds and then try again
				//We have a timeout to keep in mind. We have to keep how long we've been sleeping and check it each time
				sleepCount++;
				if (sleepCount >= (timeout) / 10) {
					break; //if we've slept past our timeout, break;
				}
			} catch (InterruptedException e) {
				//Do nothing. An interupted thread is an interupted thread. WE are sleeping to waste time so this doesn't matter.
				//at least how I understand it.
			}
		}
		
		//do we still not have enough data (e.g. a timeout occured)
		if (ins.available() < size) {
			//timed out
			return null;
		}
		
		ins.read(data);
		
		
		return data;
		
	}
	
	/**
	 * Returns the next byte in the queue. This will block if nothing is available.
	 * @return
	 * @throws IOException
	 */
	protected byte receiveByte() throws IOException {
		return (byte) input.getInputStream().read();
		
	}
	
	/**
	 * Generates the byte-equiv of a token and passes it to the next node in the ring.
	 */
	public void passToken() {
		byte t[];
		
		t = token.asBytes();
		
		try {
			send(t);
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Failed to pass token in node [" + address + "]!");
		}
	}
	
	/**
	 * Passes a kill frame. In other words, generates a kill frame and then passes it through output
	 */
	protected void passKill() {
		//kill frame is any frame with a FC byte value of 2
		byte[] kill = new byte[6];
		kill[0] = 0;
		kill[1] = 2;
		kill[2] = 0;
		kill[3] = 0;
		kill[4] = 0;
		kill[5] = 0;
		
		try {
			send(kill);
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Unable to pass kill to output from node [" + address + "]!");
		}
	}
	
	/**
	 * Passes a finish frame, which is a message from the monitor to the bridge. It lets the remote bridge know
	 * that this ring is finished sending data, but doesn't kill it. Instead, the ring is kept online so that it
	 * can still recieve messages.
	 */
	protected void passFinish() {
		//finish frame is much like a kill frame, except the FC byte is 3
		byte[] finish = new byte[6];
		finish[0] = 0;
		finish[1] = 3;
		finish[2] = 0;
		finish[3] = 0;
		finish[4] = 0;
		finish[5] = 0;
		
		try {
			send(finish);
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Unable to pass finish to output from node [" + address + "]!");
		}
	}
	
	
	/**
	 * Creates a frame with the given information. This handles putting the bytes in the right order.<br />
	 * This defaults priority and reservation bits to 0
	 * @param destination the byte-long address of the node to send to
	 * @param size how big the data section is. This should only be up to 254 bytes..?
	 * @param data an array of bytes that is the data.
	 * @return
	 */
	protected byte[] assembleFrame(byte destination, int size, byte[] data) {
		//little error checking first.
		if (size != 0 && data == null) {
			//not valid. We would accept data as null if size was 0
			System.out.println("Fatal Error when assembling frame in [" + address + "]!\nData is null while size is non-zero!\n");
			return null; //return null to indicate fatal error
		}
		if (data != null && (size != data.length)) {
			//sizes don't match. Security issue!
			System.out.println("Fatal error when assembling frame in [" + address + "]!\nData length (" + data.length + ") does"
					+ " not match the passed size (" + size + ")!!");
			return null; //quit. We aren't going to try and deal with this. Security and such.
		}
		if (size > 254) {
			size = 254; //only take 254 bytes of data..
			System.out.println("Invalid frame size of " + size + " being packaged by [" + address + "]!\n"
					+ "Only using the first 254 bytes of data...");
		}
		
		
		byte[] frame = new byte[size + 6]; //size of data plus 6 bytes of header + tailer
		
		//no priority info TODO
		byte by;
		by = 0;
		//leave priority bits 0
		//set fourth bit to 1, because this is not a token
		by = (byte) (by | 16); //16 is 0001 0000
		//monitor bit defaults to 0 so we leave it 0
		//reservation bits are unimplemented, so leave them 0
		
		frame[0] = by;
		
		//next byte is FC byte, which is 0 is token and 1 if frame. this is a frame.
		by = 1;
		frame[1] = by;
		
		//destination byte is next, which is passed to us
		frame[2] = destination;
		
		//source is our address
		frame[3] = address;
		
		//size is passed but needs to be brought down to a byte
		frame[4] = (byte) size;
		
		//next bytes are the data part
		for (int i = 0; i < size; i++) {
			frame[5 + i] = data[i]; //5 + i because 0-4 are header bytes
		}
		
		//last byte starts as 0. it's used for ack, but must be set to 0 to begin with
		frame[size + 5] = 0; 
		
		return frame;
	}
	
	public void addMessage(String msg) {
		messages.add(msg);
	}

}
