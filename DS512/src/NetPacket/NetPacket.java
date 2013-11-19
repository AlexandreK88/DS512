package NetPacket;

// This class represents the data that will pass between the client and the middleware, 
// and the middleware and the servers.
public class NetPacket {
	
	// Used to marshall packets and unmarshall them.
	public static final String COMMAND_SEPARATOR = ",";
	public static final String PACKET_SEPARATOR = "::";
	// Keeps track of who sends the packet.
	private int senderID;
	// May be useful to count items processed.
	private int packetID;
	// Main command provided.
	private String type;
	// Arguments sent.
	private String[] content;

	public NetPacket(int sender, int packet, String t, String[] c) {
		senderID = sender;
		packetID = packet;
		type = t;
		content = c;
	}
	
	// Returns a marshalled version of itself.
	public String fromPacketToString() {
		String message = Integer.toString(senderID) + PACKET_SEPARATOR + Integer.toString(packetID) + PACKET_SEPARATOR + type + PACKET_SEPARATOR;
		for (String word:content) {
			message = message + word + PACKET_SEPARATOR;
		}
		return message;
	}
	
	// Takes a string and unmarshalls it.
	public static NetPacket fromStringToPacket(String s) {
		System.out.println(s);
		String[] allContent = s.split(PACKET_SEPARATOR);
		String[] c = new String[allContent.length - 3];
		for (int i = 0; i < allContent.length - 3; i++) {
			c[i] = allContent[i+3];
		}
		int sender = new Integer(allContent[0]);
		int packet = new Integer(allContent[1]);
		String t = allContent[2];
		NetPacket n = new NetPacket(sender, packet, t, c);
		return n;
	}

	public int getSender() {
		return senderID;
	}
	
	public int getPacketID() {
		return packetID;
	}
	
	public String getType() {
		return type;
	}
	
	public String[] getContent() {
		return content;
	}
	
	public String toString() {
		return fromPacketToString();
	}
	
}