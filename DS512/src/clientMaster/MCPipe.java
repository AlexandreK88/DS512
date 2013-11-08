package clientMaster;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.LinkedList;

import NetPacket.NetPacket;

public class MCPipe  extends Thread {

	private Socket socket;
	private BufferedReader in;
	private PrintWriter out;
	private int packetID;
	private int serverID;
	private LinkedList<TestResult> testResults;
	private LinkedList<NetPacket> packetsToSend;
	
	public MCPipe (Socket s, int sID) {
		super("Pipe" + sID);
		packetID = 0;
		socket = s;
		testResults = new LinkedList<TestResult>();
		packetsToSend = new LinkedList<NetPacket>();
		

		if (s == null || sID < 0) {
			serverID = sID;
			this.interrupt();
		} else {
			// So far, it's one serverThread for one client. If there is ever more than
			// one client per thread, a list of clientIDs and sockets will be kept instead of the serverID and single socket.
			// The middleware will provide all the clientIDs to its own thread and the serverThreads.
			// when opening connection. 
			socket = s;
			serverID = sID;
			packetID = 0;
			packetsToSend = new LinkedList<NetPacket>();
		}
	}
	
	public void run() {
		try {
			out = new PrintWriter(socket.getOutputStream(), true);
			in = new BufferedReader(
					new InputStreamReader(
							socket.getInputStream()));
			String inputLine;
			String[] content = {Integer.toString(serverID)};
			System.out.println("Pipe ready");
			packetID++;
			while (!socket.isClosed()) {
				if (in.ready()) {
					inputLine = in.readLine();
					NetPacket p = NetPacket.fromStringToPacket(inputLine);
					decode(p);
					if (socket.isClosed()) {
						return;
					}
				}
				synchronized(packetsToSend) {
					while (!packetsToSend.isEmpty()) {
						NetPacket p = packetsToSend.removeFirst();
						out.println(p.fromPacketToString());
					}
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void decode(NetPacket p) {
		try {
			System.out.println("Just received a response " + p.getType());
			if (p.getType().equalsIgnoreCase("Throughput")) {
				String[] parameters = {p.getContent()[0]};
				testResults.add(new TestResult(Integer.parseInt(p.getContent()[p.getContent().length-2]), p.getType(), parameters, p.getContent()[p.getContent().length-1]));
			} else if (p.getType().equalsIgnoreCase("ResponseTime")) {
				String[] parameters = {p.getContent()[0], p.getContent()[1]};
				testResults.add(new TestResult(Integer.parseInt(p.getContent()[p.getContent().length-2]), p.getType(), parameters, p.getContent()[p.getContent().length-1]));
			}
			synchronized(Master.awaitedResponses) {
				Master.awaitedResponses--;
			}
		} catch (Exception e) {
			System.out.println(e.getClass().toString());
			System.out.println("Something went wrong with receiving test results.");
		}
	}

	
	public void packetToSend(NetPacket packet) {
		packetsToSend.addLast(packet);
	}

	
	public void packetToSend(String type, String[] content) {
		packetToSend(new NetPacket(serverID, packetID, type, content));
		packetID++;
	}

	
	
	public void close() {

		String[] empty = {"empty"};
		NetPacket closingPacket = new NetPacket(serverID, packetID, "close connection", empty);
		out.println(closingPacket.fromPacketToString());
		out.close();
		try {
			in.close();
			socket.close();	
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public LinkedList<TestResult> getTestResults() {
		return testResults;
	}
}
