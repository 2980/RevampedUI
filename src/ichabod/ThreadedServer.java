/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ichabod;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Starts a server that simply listens for clients and then spawns a threaded listener to handle requests
 * 
 */
public class ThreadedServer {
    
    public static void main(String[] args) throws IOException {
        new ThreadedServer(); //Call the constructor to get us out of a static context.
    }

    public ThreadedServer() throws IOException {
        
        //Give the current working directory to make sure we're serving images from the place we think we are
        System.out.println("Started Ichabod in " +
              System.getProperty("user.dir") + ". Have a nice day.");
        
        //The socket we're attached to. 
        int socketNum = 5001;
        
        //Create a socket listener
        ServerSocket serverSocket = new ServerSocket(socketNum);
        
        System.out.println("Ichabod is listening on " + socketNum + ". Hope your day is even better.");
        
        
        //Loop forever
        while(true)
        {
            //Wait for a connection
            Socket socket = serverSocket.accept();
            
            //Instatiatiate a new listener
            StarterSocket runnableSocket = new StarterSocket(socket);
            
            //Start the listener in a new thread.
            new Thread(runnableSocket).start();
        }
        
    }
}
    
    
    
