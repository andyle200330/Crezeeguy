import java.net.*;
import java.io.*;

public class Server {
    public static void main(String[] args) throws IOException{
        ServerSocket serverSocket = new ServerSocket(8888);
        Socket socket = serverSocket.accept();

        System.out.println("client connect!!!");

   }

}
