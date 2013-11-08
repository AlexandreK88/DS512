package automatedClient;

import java.io.BufferedReader;


public class ClientCallerMaker {

	public static void main(String[] args) {
		int numberOfThreads = 2;
        int port = 10121;
        String host = "localhost";
        
        if (args.length == 0) {
        }
        if (args.length == 1)
        {
        	host = args[0];
        }
        if (args.length == 2)
        {
        	host = args[0];
        	port = Integer.parseInt(args[1]);
        }
        if (args.length == 3) {
        	numberOfThreads = Integer.parseInt(args[2]);
        }
        if (args.length > 3) {
        	System.out.println ("Usage: java ClientCallerMaker [host [port] [Number of threads]");
        }
        ClientCaller.initCaller();
        for (int i = 0; i < numberOfThreads; i++) {
        	ClientCaller cc = new ClientCaller(host, port);
        	cc.start();
        }
	}
	
}
