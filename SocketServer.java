import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class SocketServer {
    ServerSocket server;
    Socket sk;
    InetAddress addr;

    private Set<String> userList = new HashSet<>();
    public void addUser(String name) {
        userList.add(name);
        updateUserList();
    }

    public void removeUser(String name) {
        userList.remove(name);
        updateUserList();
    }

    private void updateUserList() {
        String userListString = "Users: " + String.join(", ", userList);
        broadCast(userListString);
    }
    
    ArrayList<ServerThread> list = new ArrayList<ServerThread>();

    private static final Logger logger = Logger.getLogger(SocketServer.class.getName());

    static {
        try {
            FileHandler fileHandler = new FileHandler("server.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public SocketServer() {
        try {
        	addr = InetAddress.getByName("127.0.0.1");
        	//addr = InetAddress.getByName("192.168.43.1");
            
        	server = new ServerSocket(1234,50,addr);
            System.out.println("\n Waiting for Client connection");
            SocketClient.main(null);
            while(true) {
                sk = server.accept();
                System.out.println(sk.getInetAddress() + " connect");

                //Thread connected clients to ArrayList
                ServerThread st = new ServerThread(this);
                addThread(st);
                st.start();
            }
        } catch(IOException e) {
            System.out.println(e + "-> ServerSocket failed");
        }
    }

    public void addThread(ServerThread st) {

        list.add(st);
        logger.info("Added new thread for: " + st.getName());
    }

    public void removeThread(ServerThread st){

        list.remove(st); //remove
        logger.info("Removed thread for: " + st.getName());

    }

    public void broadCast(String message){
        logger.info("Broadcasting a message: " + message);
        for(ServerThread st : list){
            st.pw.println(message);
        }
    }

    public Set<String> getUserList(){
        return userList;
    }

    public static void main(String[] args) {
        new SocketServer();
    }
}

class ServerThread extends Thread {
    private static final Logger logger = Logger.getLogger(ServerThread.class.getName());
    SocketServer server;
    PrintWriter pw;
    String name;

    public ServerThread(SocketServer server) {
        this.server = server;
    }

    @Override
    public void run() {
        try {
            // read
            BufferedReader br = new BufferedReader(new InputStreamReader(server.sk.getInputStream()));

            // writing
            pw = new PrintWriter(server.sk.getOutputStream(), true);
            name = br.readLine();
            server.addUser(name);
            logger.info(name + " connected.");
            server.broadCast("**["+name+"] Entered**");

            String data;
            while((data = br.readLine()) != null ){
                if(data == "/list"){
                    pw.println("Users: " + String.join(", ", server.getUserList()));
//                    pw.println("a");
                } else if (data.startsWith("weather")) {
                    pw.println("Weather info: Sunny 25°C.");
                } else if (data.startsWith("IMAGE:")){
                    handleImage(server.sk);
                } else {
                    server.broadCast("[" + name + "] " + data);
                }
                logger.info("Recieved message from " + name + ": " + data);
            }
        } catch (Exception e) {
            //Remove the current thread from the ArrayList.
            server.removeThread(this);
            server.removeUser(name);
            server.broadCast("**["+name+"] Left**");
            logger.info(name + " has left the chat.");
            System.out.println(server.sk.getInetAddress()+" - ["+name+"] Exit");
            System.out.println(e + "---->");
        }
    }

    public void handleImage(Socket clientSocket) {
        try {
            InputStream inputStream = clientSocket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            String metadata = reader.readLine();
            String[] parts = metadata.split(":");
            String fileType = parts[1];
            long fileSize = Long.parseLong(parts[2]);

            File file = new File("received_image." + fileType);
            FileOutputStream fileOutputStream = new FileOutputStream(file);

            byte[] buffer = new byte[4096];
            long remaining = fileSize;
            int bytesRead;
            while (remaining > 0 && (bytesRead = inputStream.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                fileOutputStream.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
            }
            fileOutputStream.close();

            PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
            writer.println("Image received and saved as " + file.getName());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}