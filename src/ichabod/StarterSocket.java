package ichabod;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StarterSocket implements Runnable {

    /** The socket we are attached to */
    private Socket socket;
    
    /** The stream we send information to, i.e., what we send to the browser */
    PrintWriter out;
    
    /** The scanner we read from, i.e., what we read from the browser. */
    Scanner in;

    /** A reference to our processor class, which handles the actual image processing. */
    Processor processor;

    /**
     * Create an instance of this object with a reference to the socket we need
     *
     * @param inSocket The socket to process
     */
    public StarterSocket(Socket inSocket) {
        this.socket = inSocket;

        processor = new Processor();
    }

    /**
     * The actual processing of our socket. We grab the GET header line and then
     * process the provided path
     */
    @Override
    public void run() {
        try {
            //Create the streams
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new Scanner(socket.getInputStream());

            //The browser will send over lots of information. 
            //We only care about the GET line so we know what it wants
            boolean cont = true;
            
            //Loop until we find our line or until the browser is done talking
            while (cont && in.hasNextLine()) {
                String line = in.nextLine();

                // We only care about lines that start with GET
                if (line.startsWith("GET ")) {
                    //Chop up the line into it's parts
                    String[] splits = line.split(" ");
                    //The second part should be the URL
                    String url = splits[1];
                    System.out.println("GET request for " + url);
                    //Do something with the request
                    handleRequest(url);
                } else if (line.equals("")) {
                    //This means the header is finished, so we are done processing.
                    cont = false;
                }
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            //No matter what, close the stream.
            out.close();
            in.close();
        }
        try {
            //Close the socket
            socket.close();
        } catch (IOException ex) {
            Logger.getLogger(StarterSocket.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Do something with the path we got from the GET request
     *
     * @param url The requested path (potentially including variables)
     */
    private void handleRequest(String url) {

        
        url = url.substring(1); //Get rid of the leading /

        //Handle the empty path case
        if (url.equals("")) {
            url = "index.html";
        }

        //Check to make sure the file exists
        if (isFile(url)) {
            handle200(Paths.get(url));

        } else {
            //If it's not a file we have, check to see if it's a command we understand
            if (!processCommand(url)) //If not, then it's an error
            {
                handle404();
            }
        }
    }

    /**
     * Respond with 404
     */
    private void handle404() {
        String response = "Bad news, couldn't find that page."; //This could be anything, or even blank.

        out.println("HTTP/1.0 404 Not Found");
        out.println("Content-Type: text/html");
        out.println("Content-Length: " + response.length());

        out.println();
        out.println(response);
    }

    /**
     * Respond with a 200 OK and send a file
     *
     * @param path The path to the file we want to send
     */
    private void handle200(Path path) {

        try {
            out.print("HTTP/1.0 200 OK\r\n");
            byte[] bytes = Files.readAllBytes(path);
            out.print("Content-Length: " + bytes.length + "\r\n");
            out.print("\r\n");
            out.flush();
            socket.getOutputStream().write(bytes, 0, bytes.length);
        } catch (IOException ex) {
            Logger.getLogger(StarterSocket.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Respond with 200 OK and send a string
     * @param string The string to send
     */
    private void handle200(String string) {

        out.print("HTTP/1.0 200 OK\r\n");

        out.print("Content-Length: " + string.length() + "\r\n");
        out.print("\r\n");
        out.flush();
        out.println(string);

    }

    private void handle200(byte[] bytes) {

        try {
            out.print("HTTP/1.0 200 OK\r\n");
            
            out.print("Content-Length: " + bytes.length+ "\r\n");
            out.print("\r\n");
            out.flush();
            socket.getOutputStream().write(bytes, 0, bytes.length);
        } catch (IOException ex) {
            Logger.getLogger(StarterSocket.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
    /**
     * Determine if the string is the path to a file
     *
     * @param path The name of the file
     * @return true if it is a file, false if the file doesn't exist or the path
     * is a directory. Also false if it is similar to a command so that files don't mask commands.
     */
    private boolean isFile(String path) {

        if (path.startsWith("process") || path.startsWith("getFileList") || path.startsWith("getCommandList")) {
            return false; //That's a command, so we ignore it. This prevents a file that starts with process from crashing everything.
        }
        File f = new File(path);
        return f.exists() && !f.isDirectory();
    }

    /**
     * Do something if we get a command
     * 
     * @param command The command to process
     * @return True if we successfully processed the command, false otherwise
     */
    private boolean processCommand(String command) {
        
        //Return the list of images we can serve
        if (command.startsWith("getFileList")) {

            List<String> fileNames = new ArrayList<>();
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(Paths.get("."))) {
                for (Path path : directoryStream) {
                    if (isImagePath(path)) {
                        //Ignore temp files
                        if (!path.toString().contains("0.")) {
                            fileNames.add(path.toString().substring(1));
                        }
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            //Put the list into one string separated by |
            String list = fileNames.stream().reduce("", (a, b) -> a + "|" + b);
            handle200(list);

            return true; //We dealt with it, so we're happy.
        } else if(command.startsWith("getCommandList")){
            //Get a list of commands the user can issue by querying the processor
            String list = Arrays.stream(processor.validCommands()).reduce("", (a,b)->a + "|" + b);
            handle200(list);
        }         
        else if (command.startsWith("process") || command.startsWith("static")) {
            //Actually run a command on an image
            
            //Split on ?
            String[] arguments = command.split("\\?");
            if (arguments.length != 2) {
                return false;
            }

            String allArguments = arguments[1];

            //Split on &
            String[] pairs = allArguments.split("&");

            HashMap<String, String> keyValuePairs = new HashMap<String, String>();

            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length != 2) {
                    return false;
                }
                keyValuePairs.put(keyValue[0], keyValue[1]);
            }

            //Make sure we have a command
            if (!keyValuePairs.containsKey("command")) {
                return false;
            }

            //Make sure we have an image
            if (!keyValuePairs.containsKey("image")) {
                return false;
            }

            
            String commandName = keyValuePairs.get("command");
            String imageName = keyValuePairs.get("image");

            //Make sure the image exists
            if (!isFile(imageName)) {
                return false;
            }
            
            //Process the command
            byte[] result = processor.Process(commandName, imageName, keyValuePairs);

            //Make sure the command succeeded
            if (result == null) {
                return false;
            }
            
            
            
            handle200(result);
            
            return true;
            

        }
        //check for other commands
        return false; //We didn't catch the command, so return false so we know to send a 404
    }

    //List of image types we support. Any other file extensions will not be served by our system as images.
    private String[] imageExtensions = new String[]{".jpeg", ".jpg", ".gif", ".bmp", ".png"};

    /**
     * Determines if the path belongs to a file with an image file extension
     * @param path The path to check
     * @return True if it has an image fie extension, false otherwise
     */
    private boolean isImagePath(Path path) {
        for (String extension : imageExtensions) {
            if (path.toString().endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

}
