package com.smanzana.Project3.Frame;

/**
 * A class that represents a frame. This class is meant to be a static helper class to
 * abstract all of the frame-interpretting methods into one place.
 * <p>
 * Methods defined herein include:
 * <ul>
 * <li>Methods that determine the destination address</li>
 * <li>Methods to figure out if the frame is a token</li>
 * <li>Methods to fetch the data section</li>
 * <li>etc</li></ul></p>
 * <p style="font-size: 7pt">Aside: I did think about making this an actual class worth instantiating. It would stop you from
 * having to pass the array around all the time. In addition, it would better exemplify Object Oriented design
 * in that the header would be an object instead of just a chunk of data. This code would work well in C, where we
 * would typedef the array to be a 'header' and pass that around. <br />
 * I decided against making it an object because it really doesn't have any functionality. It doesn't do anything.
 * All methods defined in this static class just get specific pieces of data. It justs gets associates byte order with
 * a name (e.g. byte 3 is the destination address byte!). It doesn't actually do anything on its own.</p>
 * @author Skyler
 *
 */
public final class Frame {
	

	public static int headerLength = 5; //default to 5. This can be changed externally to
										//make out code a little less rigid
	
	/**
	 * Nested header of a frame that more specifically works on the 5 byte header
	 * @author Skyler
	 *
	 */
	public  static final class Header {
		
		
		/**
		 * Tries to assess whether the passed header denotes the frame is a token.<br />
		 * If the header is not the right size (5 bytes) or is null, returns false<br />
		 * This method takes into consideration <b>both</b> the token bit in the AC byte (bit 4) AND
		 * the FC byte. Both denote whether or not this frame is a token.<br />
		 * If both values don't agree, <u>frame is considered not to be a token</u>
		 * @param header
		 * @return True only if the header of a token is passed. false is anything (including <i>null</i>) else is.
		 */
		public  static boolean isToken(byte[] header) {
			if (header == null || header.length != headerLength)
				return false;
			
			byte AC, mask = 16; //0001 0000  - a mask to get the token bit
			AC = header[0];
			
			if ((AC & mask) == 0) {
				//if that bit was not set, which is indicitive of a token.
				//we not check the second byte in the header -- the Frame control byte
				byte FC = header[1];
				if (FC == 0) {
					//both said it was a token, so we'll say it's a token
					return true;
				}
			}
			//either the 4th bit in the AC byte OR the second byte said it wasn't a token. 
			return false;
		}
		
		/**
		 * Checks to see whether the frame is one that is telling this node to kill itself.<br />
		 * A kill node is defined (by me) as:<br />
		 * Any frame such that the FC byte is <b>2</b>. 
		 * @param header
		 * @return true if the frame is a kill frame, false otherwise (including on error)
		 */
		public static boolean isKill(byte[] header) {
			if (header == null || header.length != headerLength) {
				return false;
			}
			
			if (header[1] == 2) {
				return true;
			}
			//imp. else
			return false;
		}
		
		/**
		 * Determines if the passed frame('s header) denotes a FINISH frame.
		 * <p>FINISH frames are used to indicate that a ring is done generating data to transmit. This is one step behind
		 * a kill frame, because the nodes in the ring may still be future destinations.</p>
		 * <p>Only the bridge node in the ring cares about finish frames. It relays the info that this ring is finished
		 * to the actual bridge. It is then updated remotely.
		 * When all rings have finished transmitting, the remote bridge is responsible for flooding a kill message to all rings
		 * so that they all will be killed only after every single one is done transmitting.</p>
		 * @param header
		 * @return True if the frame is a FINISH frame. If it's not, or it's null, will return false.
		 */
		public static boolean isFinish(byte[] header) {
			if (header == null || header.length != headerLength) {
				return false;
			}
			
			if (header[1] == 3) {
				//3 is our signal for FINISH frame
				return true;
			}
			
			return false;
		}
		
		/**
		 * Returns the priority of a frame as a byte. <br />
		 * The priority in our frame structure is only 3 bits, so it shouldn't be bigger than 7. This method
		 * does not make that check though.<br />
		 * If something is wrong (null header, wrong size, etc), this method returns -1
		 * @param header
		 * @return -1 on error, the priority otherwise
		 */
		public static byte getPriority(byte[] header) {
			if (header == null || header.length != headerLength) {
				return -1;
			}
			
			byte AC, mask = -32; //1110 0000  or the three first bits which are our priority
			AC = header[0];
			return (byte) (AC & mask);
		}
		
		/**
		 * Returns the monitor bit in boolean format.<br />
		 * This method returns false on error (e.g. null header or wrong length)
		 * @param header
		 * @return
		 */
		public static boolean getMonitor(byte[] header) {
			if (header == null || header.length != headerLength) {
				return false;
			}
			
			byte AC = header[0], mask = 8; //0000 1000
			return( (AC & mask) == mask);
		}
		
		/**
		 * Returns the number stored in the reservation bits (right-most 3 in our implementation)
		 * @param header
		 * @return
		 */
		public static byte getReservation(byte[] header) {
			if (header == null || header.length != headerLength) {
				return -1;
			}
			
			byte AC = header[0], mask = 7; //0000 0111
			
			return (byte) (AC & mask);
		}
		
		/**
		 * Gets the destination address (byte) from the frame. Returns -1 if something goes wrong
		 * @param header
		 * @return The address, or -1 on error
		 */
		public static byte getDestination(byte[] header) {
			if (header == null || header.length != headerLength) {
				return -1;
			}
			
			return header[2];
		}
		
		/**
		 * Gets the source address and returns it
		 * @param header
		 * @return The source address, or -1 on error
		 */
		public static byte getSource(byte[] header) {
			if (header == null || header.length != headerLength) {
				return -1;
			}
			
			return header[3];			
		}
		
		/**
		 * Returns the byte equivalent of the size byte.<br />
		 * This is <b>signed</b>, whereas the size can be up to 254 bytes as per protocol.
		 * @param header
		 * @return
		 */
		public static byte getSize(byte[] header) {
			if (header == null || header.length != headerLength) {
				return -1;
			}
			return header[4];
		}
			
		
	}
	
	/**
	 * Tries to return the header of a frame.
	 * @param frame
	 * @return The frame header, or null on error
	 */
	public static byte[] getHeader(byte[] frame) {
		if (frame == null || frame.length < headerLength) {
			return null;
		}
		byte[] header = new byte[headerLength];
		for (int i = 0; i < headerLength; i++) {
			header[i] = frame[i];
		}
		return header;
	}
	
	/**
	 * Extracts the data portion of the frame and returns it.<br />
	 * The data portion of the frame is determined using the size indicated in the frame header. If the header
	 * does not meet the defined specs, returns null.
	 * @param frame
	 * @return A byte array with only the data portion of the frame, or null if something is awry...
	 */
	public static byte[] getData(byte[] frame) {
		if (frame == null || frame.length < headerLength) {
			//auto disqualify
			return null;
		}
		
		int size = (Header.getSize(getHeader(frame)) & 0xFF); //see line below this for explanation
		//getSize returns a byte that has the proper bits set. Unfortunately, java uses all signed things. If we
		//were to just cast this to an int, all the negative values would be carried over. EG we would have
		//a size from -128 to 127. We want from 0 to 255 (254 specifically). To carry over which bits are set
		//and not care about the sign, we just do a bitwise AND with 1111 1111. All bits that are set will be carried over
		//and its converted to an int without the need for specific casting rules.
		
		if (size == -1) {
			//error code
			return null;
		}
		if (frame.length < headerLength + 1 + size) {
			//frame needs to be the length of the header + the length of the data + 1 (for the FS byte at the end).
			//if it's not, the frame is invalid
			return null;
		}
		
		byte data[] = new byte[size];
		
		if (size == 0) {
			return data; //quit now, because our size was 0. Returns an empty array as per method definition
		}
		
		for (int i = 0; i < size; i++) {
			//out data starts on byte 6, so we add an offset of 6
			data[i] = frame[5 + i];
		}
		
		return data;
	}
	
	/**
	 * Returns the Frame Status byte, located at the end of the frame.
	 * @param frame
	 * @return The FS byte, or -1 on error
	 */
	public static byte getFrameStatus(byte[] frame) {
		if (frame == null || frame.length < headerLength + 1) {
			//right away know it is invalid
			return -1;
		}
		int size = (Header.getSize(getHeader(frame)) & 0xFF);
		
		if (size == -1) {
			return -1;
		}
		
		if (size == 0) {
			return frame[headerLength]; //if no data, the width is just headerLength + 1.
										//because this is 0 indexed, we just use headerLength
		}
		
		if (frame.length < headerLength + size + 1) {
			return -1; //problem! the frame isn't as big as it claims it is...
		}
		
		return frame[headerLength + size];
	}
	
}
