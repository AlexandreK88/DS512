package Middleware;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class MidUI extends Thread {

	private String server;
	BufferedReader stdin;
	boolean shouldRun;
	private int port;
	public MidUI(int p, String s) {
		super("ServerControl");
        //Container c = getContentPane();
        //JPanel panel = new JPanel();
        stdin = new BufferedReader(new InputStreamReader(System.in));
        server = s;
        port = p;
        shouldRun = true;
        //serverControl = new JButton("Set server off");
        //serverControl.setActionCommand("yes");
        //serverControl.addActionListener(this);
        //panel.add(serverControl);
        //c.add(panel);
        // arrange components
        //pack();

        //setFocusable(true);

        // set visible so window appears on screen
        //setVisible(true);

        // make the frame appear at the center of the screen
        //setLocationRelativeTo(null);
        start();
		
	}

	public void run() {
		try {
			while (true) {
				if (stdin.ready()) {
					String command = stdin.readLine();
					if (command.equals("quit") || command.equals("exit")) {
						shouldRun = false;
						stdin.close();
						try {
							Socket socket = new Socket("localhost", port);
						} catch (UnknownHostException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						return;
					}
				}
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public boolean isUp() {
		//if (serverControl.getActionCommand().equals("yes")) {
		//    return true;
		//}

		return shouldRun;
	}

	//@Override
	/*public void actionPerformed(ActionEvent action) {
        JButton component = (JButton)action.getSource();
        component.setActionCommand("no");
        try {
            Socket socket = new Socket("localhost", port);
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }*/
} 
