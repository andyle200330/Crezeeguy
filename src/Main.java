import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.*;
import java.io.*;

public class Main extends JFrame {
    private JLabel jsfirstname;
    private JTextField Jschat;
    private JButton Jbutton;
    private JPanel MainPanel;

    private ObjectOutputStream out;  // Gửi dữ liệu



    public Main (ObjectOutputStream outStream){

        this.out = outStream;

        setContentPane(MainPanel);
        setTitle("Chat");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(300,200);
        setLocationRelativeTo(null);
        setVisible(true);


        Jbutton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String message = Jschat.getText();

                try {
                    out.writeObject(message);
                    out.flush();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                Jschat.setText("");
            }
        });
    }

    public static void main(String[] args) throws IOException {
        Socket socket = new Socket("localhost",8888);
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));


        new Main(out);

    }
}
