package com.smanzana.Project3;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;

import com.smanzana.Project3.Node.Monitor;
import com.smanzana.Project3.Node.Node;

public class Project3 {
	
	public static byte lastAddress = 0;
	public static int portOffset;
	public static int THT = 10;
	public static Random rand;
	
	private static int nodeCount;
	
	public static void main(String[] args) { //has to be 2 or more!
		
		rand = new Random();
		
		if (args.length != 1 && args.length != 2) {
			//invalid args
			System.out.println("Usage: java -jar jar_name.jar ring_config.conf [port offset]");
			return;
		}
		
		//open file and make sure it's valid
		File file = new File(args[0]);
		if (!file.exists()) {
			System.out.println("Unable to find the config file: " + args[0]);
			return;
		}
		
		Scanner input = null;
		try {
			input = new Scanner(file);
		} catch (FileNotFoundException e2) {
			e2.printStackTrace();
			System.out.println("Error when trying to find file: " + args[0]);
			return;
		}
		
		//generate a port offset. If we're not passed one, generate a random one
		if (args.length == 2) {
			portOffset = Integer.parseInt(args[1]);
		} else {
			System.out.print("Generating random port offset...  ");
			int offsetOffset = rand.nextInt(40000);
			portOffset = 20000 + offsetOffset;
			System.out.println(portOffset);
		}
		
		Node monitor = new Monitor(THT, (byte) 0);
		monitor.listen(new SocketAddress());
		
		
		
		
		
		
		
		
		
		
		
		int offset = 1;
		
		byte address;
		Node lastNode = null, node;
		while (input.hasNextLine()) {
			address = (byte) Integer.parseInt(input.nextLine());
			node = new Node(THT, address);
			
			if (lastNode != null) {
				//this isn't the first node, so connect it to the previous
				
			}
		}
		
		
		
		Project3.nodeCount = Integer.parseInt(args[0]);
		if (args.length == 2) {
			Project3.THT = Integer.parseInt(args[1]);
		}
		
		if (Project3.nodeCount < 2 || Project3.nodeCount > 254) {
			System.out.println("Invalid number of nodes. Must be between 2 and 254 inclusive!");
			return;
		}
		
		if (Project3.THT < 1) {
			System.out.println("Invalid supplied THT time. THT must be 1 or greater!");
			return;
		}
		
		Project3.lastAddress = (byte) Project3.nodeCount;
		
		ArrayList<Node> nodes = new ArrayList<Node>(); //for holding all our nodes
		ServerSocket sock;
		Scanner scan;
		//go through and add all our nodes, starting with the monitor node
		try {
			sock = new ServerSocket(Project3.portOffset);
		} catch (IOException e) {
			System.out.println("Encountered an error creating the server socket for the monitor!");
			return;
		}
		Node node = new Monitor(THT, (byte) 0, sock);
		nodes.add(node);
		for (int i = 1; i <= nodeCount; i++) {
			//make each a server socket
			try {
				sock = new ServerSocket(Project3.portOffset + i);
			} catch (IOException e) {
				System.out.println("Encountered an error creating the server socket for node [" + i + "]!");
				for (Node n : nodes) {
					//close them down
					try {
						n.kill();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}
				return;
			}
			
			//use our server socket to create the node
			node = new Node(THT, (byte) i, sock);
			
			//add to nodes
			nodes.add(node);
			
			//next, get this node's input file and queue its messages
			try {
				scan = new Scanner(new File("input-file-" + i));
			} catch (FileNotFoundException e) {
				System.out.println("Unable to find input file: \"input-file" + i + "\"");
				for (Node n : nodes) {
					//close them down
					try {
						n.kill();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}
				return;
			}
			
			//queue the messages
			while (scan.hasNextLine()) {
				node.addMessage(scan.nextLine());
			}
			
			scan.close();
		}
		
		
//		
//		
//		Node a, b, c;
//		int pa, pb, pc;
//		pa = portOffset;
//		pb = portOffset + 1;
//		pc = portOffset + 2;
////		ServerSocket sock = null;
//		try {
//			sock = new ServerSocket(pa);
//		} catch (IOException e) {
//			e.printStackTrace();
//			return;
//		}
//		a = new Monitor(2, (byte) 0, sock);
//		
//		try {
//			sock = new ServerSocket(pb);
//		} catch (IOException e) {
//			e.printStackTrace();
//			return;
//		}
//		b = new Node(2, (byte) 1, sock);
//		
//		try {
//			sock = new ServerSocket(pc);
//		} catch (IOException e) {
//			e.printStackTrace();
//			return;
//		}
//		c = new Node(2, (byte) 2, sock);
//		
//		Scanner scanner;
//		try {
////			scanner = new Scanner(new File("input-file0.txt"));
////			while (scanner.hasNextLine()) {
////				a.addMessage(scanner.nextLine());
////			}
////			scanner.close();
//			scanner = new Scanner(new File("input-file1.txt"));
//			while (scanner.hasNextLine()) {
//				b.addMessage(scanner.nextLine());
//			}
//			scanner.close();
//			scanner = new Scanner(new File("input-file2.txt"));
//			while (scanner.hasNextLine()) {
//				c.addMessage(scanner.nextLine());
//			}
//			scanner.close();
//		} catch (FileNotFoundException e) {
//			e.printStackTrace();
//			return;
//		}
		
		System.out.println("starting...");
		
		for (Node n : nodes) {
			n.start();
		}
		
				
		System.out.println("Connected and running!");
		
	}

}
