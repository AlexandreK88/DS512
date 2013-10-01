package Client;

import java.io.File;

public class RunClient {

    public static void main(String[] args) {
    	
    	System.out.println("We're using GIT, YOUHOUUUUUU");
        
        int port = 10121;
        String host = "localhost";
        if (args.length == 0) {
            new Client(host, port);
        }
        if (args.length == 1)
        {
            new Client(args[0], port);
        }
        if (args.length == 2)
        {
            new Client(args[0], Integer.parseInt(args[1]));
        }
        if (args.length > 3) {
            
            if (args[2].equals("-RS")) {
                File file = new File(args[3]);
                if (file != null)
                	new Client(args[0], Integer.parseInt(args[1]), file);
                else
                	System.out.println("Can't find file.");
                System.exit(1);
            } else {
                System.out.println ("Usage: java client [host [port] -RS ScriptPath/ScriptName");
            }
        }
        if (args.length == 3 || args.length > 4)
        {
            System.out.println ("Usage: java client [host [port] -RS ScriptPath/ScriptName");
        }
        
        
    }
}
