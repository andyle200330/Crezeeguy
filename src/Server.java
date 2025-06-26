import java.net.*;
import java.io.*;

public class Server {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        ServerSocket serverSocket = new ServerSocket(8888);

        Socket socket = serverSocket.accept();

        System.out.println("client connect!!!");

        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

        while(true){
            String message = (String) in.readObject();
            System.out.println("client: "+message);

        }

   }

}
