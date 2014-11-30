package com.smanzana.Project3.Frame;

/**
 * Represents the token.<br />
 * This class is used to preserve token bit status between nodes. It also provides an easy way to manipulate the token
 * without getting into bit/byte specifics.
 * <p>The specifics of the token are hereby set to be:<br />
 * <ul><li>Token will have the <b>both</b> fourth bit in the AC byte and the whole FC byte set to 0.</li>
 * <li>Token will have a data size of 0 and no data attached.</li></ul>
 * A frame that breaks these will be considered to be a regular frame and processed as such.
 * </p>
 * @author Skyler
 */
public class Token {
	
	private byte AC, FC, DA, SA, FS;
	private int size;
	
	/**
	 * Tries to create a token based on the passed byte array.<br />
	 * This constructor assumes that the frame has been checked to be a token using Frame.Header's 
	 * {@link com.smanzana.Project3.Frame.Frame.Header#isToken(byte[]) isToken()} method. Only
	 * minimal error checking is performed.
	 * @param token A byte array containing the passed token.
	 */
	public Token(byte[] token) {
		this();
		if (token == null || token.length != 6) {
			System.out.println("Invalid token construction. Generating a generic token instead.");
			return;
		}
		
		//assume everything is okay.
		AC = token[0];
		FC = token[1];
		DA = token[2];
		SA = token[3];
		FS = token[5];
		size = token[4];
	}
	
	/**
	 * Private constructor for setting up defaults in the case an invalid token frame is handed to the regular constructor
	 */
	private Token() {
		AC = 0;
		FC = 0;
		DA = -1;
		SA = -1;
		FS = 0;
		size = 0;
	}
	
	public byte[] asBytes() {
		byte token[] = new byte[6];
		
		token[0] = AC;
		token[1] = FC;
		token[2] = DA;
		token[3] = SA;
		token[4] = (byte) size;
		token[5] = FS;
		
		return token;
	}
	
	public void setUsed(boolean usedBit) {
		//used bit is defined (again, by me. See the Monitor node's run method) as the most-significant in the FS byte
		if (usedBit) {
			FS = (byte) (FC | 128); //X000 00XX | 1000 0000  -- make sure the left-most is set to 1 and leave everything else the same
		}
		else {
			FS = (byte) (FC & 127); //X000 00XX & 0111 1111 -- make sure left-most is 0 and leave the rest the same
		}
	}
	
	public boolean wasUsed() {
		int used;
		used = (FS & 128); //X000 00XX & 1000 0000  or X000 0000
		if (used == 128) {
			//it is set to 1!
			return true;
		}
		return false;
	}
}
