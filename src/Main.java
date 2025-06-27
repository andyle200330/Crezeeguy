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

    private ObjectOutputStream out;  // Gửi dữ liệu

    private PrivateKey privateKey;
    private PublicKey publicKey;
    private  SecretKey aesKey;
    private  IvParameterSpec iv;




    public Main (ObjectOutputStream outStream){

        this.out = outStream;

        //tạo RSA key pair và AES key
        try{
            generateKeys();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        setContentPane(MainPanel);
        setTitle("Chat");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(300,200);
        setLocationRelativeTo(null);
        setVisible(true);


        Jbutton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String message = Jschat.getText().trim();

                if (!message.isEmpty()) {
                    try {
                        sendSecureMessage(message);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(Main.this, "Lỗi gửi tin nhắn: " + ex.getMessage());
                    }
                    Jschat.setText("");
                }
            }
        });
    }
    private void generateKeys() throws  Exception{
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(2048);
        KeyPair keyPair = keyPairGen.generateKeyPair();
        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();

        // Lưu private key vào file
        savePrivateKeyToFile(privateKey);

        // Tạo AES key và IV
        KeyGenerator aesKeyGen = KeyGenerator.getInstance("AES");
        aesKeyGen.init(256);
        this.aesKey =aesKeyGen.generateKey();

        // Tạo IV ngẫu nhiên cho AES CBC
        SecureRandom random = new SecureRandom();
        byte[] ivBytes = new byte[16];
        random.nextBytes(ivBytes);
        this.iv= new IvParameterSpec(ivBytes);


    }

    private void savePrivateKeyToFile(PrivateKey privateKey) throws IOException {
        byte[] encoded = privateKey.getEncoded();
        String privateKeyPEM = "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getEncoder().encodeToString(encoded) + "\n" +
                "-----END PRIVATE KEY-----";
        Files.write(Paths.get("private_key.pem"), privateKeyPEM.getBytes());
        System.out.println("Private key đã được lưu vào private_key.pem");
    }

    private void sendSecureMessage(String message) throws Exception{
        // 1. Tạo chữ ký số cho message bằng SHA256withRSA
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(message.getBytes("UTF-8"));
        byte[] digitalSignature = signature.sign();

        // 2. Mã hóa chữ ký số bằng Base64
        String base64Signature = Base64.getEncoder().encodeToString(digitalSignature);

        // 3. Mã hóa public key bằng AES CBC
        Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, iv);
        byte[] encryptedPublicKey = aesCipher.doFinal(publicKey.getEncoded());
        String base64EncryptedPublicKey = Base64.getEncoder().encodeToString(encryptedPublicKey);

        // 4. Tạo đối tượng SecureMessage để gửi

        SecureMessage secureMessage = new SecureMessage(
                message,                    // raw message
                base64Signature,           // chữ ký số đã mã hóa Base64
                base64EncryptedPublicKey,  // public key đã mã hóa AES
                Base64.getEncoder().encodeToString(aesKey.getEncoded()), // AES key
                Base64.getEncoder().encodeToString(iv.getIV())           // IV
        );

        out.writeObject(secureMessage);
        out.flush();

    }

    public static void main(String[] args) throws IOException {
        try {
            Socket socket = new Socket("localhost", 8888);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());

            SwingUtilities.invokeLater(() -> new Main(out));

        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Không thể kết nối đến server: " + e.getMessage());
        }
    }
}
