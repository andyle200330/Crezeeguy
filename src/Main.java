import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class Main extends JFrame {
    private JLabel jsfirstname;
    private JTextField Jschat;
    private JButton Jbutton;
    private JPanel MainPanel;

    public Main (){
        setContentPane(MainPanel);
        setTitle("Chat");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(300,200);
        setLocationRelativeTo(null);
        setVisible(true);

        Jbutton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(Main.this, "testing button");
            }
        });
    }

    public static void main(String[] args) {
        new Main();
    }
}
