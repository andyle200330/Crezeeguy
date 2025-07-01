import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import java.awt.event.*;
import java.net.*;
import java.io.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main extends JFrame {
    private JLabel jsfirstname;
    private JTextField Jschat;
    private JButton Jbutton;
    private JPanel MainPanel;

    private ObjectOutputStream out;
    private ObjectInputStream in;

    private PrivateKey privateKey;
    private PublicKey publicKey;
    private SecretKey aesKey;
    private IvParameterSpec iv;

    public Main(Socket socket) {
        try {
            this.out = new ObjectOutputStream(socket.getOutputStream());
            this.in = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("Lỗi tạo stream: " + e.getMessage());
        }

        // Tạo khóa RSA và AES
        try {
            generateKeys();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        setContentPane(MainPanel);
        setTitle("Chat");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(300, 200);
        setLocationRelativeTo(null);
        setVisible(true);

        Jbutton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String message = Jschat.getText().trim();

                if (!message.isEmpty()) {
                    try {
                        sendSecureMessage(message);

                        // Nhận phản hồi từ server
                        Object response = in.readObject();
                        if (response instanceof String) {
                            JOptionPane.showMessageDialog(Main.this, "Server phản hồi:\n" + response);
                        }

                    } catch (Exception ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(Main.this, "Lỗi gửi/nhận: " + ex.getMessage());
                    }
                    Jschat.setText("");
                }
            }
        });
    }

    private void generateKeys() throws Exception {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(2048);
        KeyPair keyPair = keyPairGen.generateKeyPair();
        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();

        savePrivateKeyToFile(privateKey);

        KeyGenerator aesKeyGen = KeyGenerator.getInstance("AES");
        aesKeyGen.init(256);
        this.aesKey = aesKeyGen.generateKey();

        SecureRandom random = new SecureRandom();
        byte[] ivBytes = new byte[16];
        random.nextBytes(ivBytes);
        this.iv = new IvParameterSpec(ivBytes);
    }

    private void savePrivateKeyToFile(PrivateKey privateKey) throws IOException {
        byte[] encoded = privateKey.getEncoded();
        String privateKeyPEM = "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getEncoder().encodeToString(encoded) + "\n" +
                "-----END PRIVATE KEY-----";
        Files.write(Paths.get("private_key.pem"), privateKeyPEM.getBytes());
        System.out.println("Private key đã được lưu vào private_key.pem");
    }

    private void sendSecureMessage(String message) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(message.getBytes("UTF-8"));
        byte[] digitalSignature = signature.sign();

        String base64Signature = Base64.getEncoder().encodeToString(digitalSignature);

        Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, iv);
        byte[] encryptedPublicKey = aesCipher.doFinal(publicKey.getEncoded());
        String base64EncryptedPublicKey = Base64.getEncoder().encodeToString(encryptedPublicKey);

        SecureMessage secureMessage = new SecureMessage(
                message,
                base64Signature,
                base64EncryptedPublicKey,
                Base64.getEncoder().encodeToString(aesKey.getEncoded()),
                Base64.getEncoder().encodeToString(iv.getIV())
        );

        out.writeObject(secureMessage);
        out.flush();
    }

    public static void main(String[] args) {
        try {
            Socket socket = new Socket("localhost", 8888);
            SwingUtilities.invokeLater(() -> new Main(socket));
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Không thể kết nối đến server: " + e.getMessage());
        }
    }
}