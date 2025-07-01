import java.net.*;
import java.io.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.sql.*;
import java.util.*;

public class Server {
    private static Connection dbConnection;
    private static Map<String, ClientInfo> clientDatabase = new HashMap<>();

    public static void main(String[] args) {
        try {
            initializeDatabase();

            ServerSocket serverSocket = new ServerSocket(8888);
            System.out.println("Server đang chạy trên port 8888...");

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Client kết nối từ: " + socket.getInetAddress());

                new Thread(() -> handleClient(socket)).start();
            }

        } catch (IOException | SQLException e) {
            e.printStackTrace();
        }
    }

    private static void initializeDatabase() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String url = "jdbc:mysql://localhost:3306/mydatabase";
            String user = "root";
            String password = "";

            dbConnection = DriverManager.getConnection(url, user, password);

            String createTable = """
            CREATE TABLE IF NOT EXISTS client_keys (
                client_id VARCHAR(255) PRIMARY KEY,
                public_key TEXT NOT NULL,
                aes_key TEXT NOT NULL,
                iv TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

            Statement stmt = dbConnection.createStatement();
            stmt.execute(createTable);
            stmt.close();

            System.out.println("Database đã được khởi tạo bằng MySQL");

        } catch (ClassNotFoundException e) {
            System.out.println("Không tìm thấy MySQL driver, kiểm tra thư viện JDBC.");
        }
    }

    private static void handleClient(Socket socket) {
        try {
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            String clientId = socket.getInetAddress().toString() + ":" + socket.getPort();

            while (true) {
                try {
                    SecureMessage secureMessage = (SecureMessage) in.readObject();

                    boolean isValid = processSecureMessage(clientId, secureMessage, out);

                    if (!isValid) {
                        out.writeObject("VERIFICATION_FAILED");
                        out.flush();
                    }

                } catch (ClassNotFoundException e) {
                    System.out.println("Lỗi đọc dữ liệu từ client: " + e.getMessage());
                    break;
                }
            }

        } catch (IOException e) {
            System.out.println("Client ngắt kết nối: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean processSecureMessage(String clientId, SecureMessage secureMessage, ObjectOutputStream out) {
        try {
            byte[] aesKeyBytes = Base64.getDecoder().decode(secureMessage.getAesKey());
            byte[] ivBytes = Base64.getDecoder().decode(secureMessage.getIv());

            SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");
            IvParameterSpec iv = new IvParameterSpec(ivBytes);

            Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            aesCipher.init(Cipher.DECRYPT_MODE, aesKey, iv);

            byte[] encryptedPublicKeyBytes = Base64.getDecoder().decode(secureMessage.getEncryptedPublicKey());
            byte[] publicKeyBytes = aesCipher.doFinal(encryptedPublicKeyBytes);

            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            saveClientInfo(clientId, publicKey, aesKey, iv);

            boolean isSignatureValid = verifySignature(
                    secureMessage.getRawMessage(),
                    secureMessage.getBase64Signature(),
                    publicKey
            );

            if (isSignatureValid) {
                System.out.println("✓ Chữ ký số hợp lệ cho client: " + clientId);

                String scanResult = scanSubdomains("huflit.edu.vn", "C:\\Users\\Admin\\OneDrive - Ho Chi Minh City University of Foreign Languages and Information Technology - HUFLIT\\Documents\\laptrinhmang\\doanlaptrinhmang\\subdomains-top1million-110000.txt");
                out.writeObject(scanResult);
                out.flush();

                return true;
            } else {
                System.out.println("✗ Chữ ký số không hợp lệ cho client: " + clientId);
                return false;
            }

        } catch (Exception e) {
            System.out.println("Lỗi xử lý tin nhắn bảo mật: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static boolean verifySignature(String message, String base64Signature, PublicKey publicKey) {
        try {
            byte[] signatureBytes = Base64.getDecoder().decode(base64Signature);

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(message.getBytes("UTF-8"));

            return signature.verify(signatureBytes);

        } catch (Exception e) {
            System.out.println("Lỗi xác thực chữ ký: " + e.getMessage());
            return false;
        }
    }

    private static void saveClientInfo(String clientId, PublicKey publicKey, SecretKey aesKey, IvParameterSpec iv) {
        try {
            if (dbConnection != null) {
                String sql = """
                    INSERT INTO client_keys (client_id, public_key, aes_key, iv)
                    VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        public_key = VALUES(public_key),
                        aes_key = VALUES(aes_key),
                        iv = VALUES(iv)
                    """;

                PreparedStatement pstmt = dbConnection.prepareStatement(sql);
                pstmt.setString(1, clientId);
                pstmt.setString(2, Base64.getEncoder().encodeToString(publicKey.getEncoded()));
                pstmt.setString(3, Base64.getEncoder().encodeToString(aesKey.getEncoded()));
                pstmt.setString(4, Base64.getEncoder().encodeToString(iv.getIV()));

                pstmt.executeUpdate();
                pstmt.close();

                System.out.println("✓ Đã lưu thông tin client vào database: " + clientId);

            } else {
                ClientInfo clientInfo = new ClientInfo(publicKey, aesKey, iv);
                clientDatabase.put(clientId, clientInfo);

                System.out.println("✓ Đã lưu thông tin client vào memory: " + clientId);
            }

        } catch (SQLException e) {
            System.out.println("Lỗi lưu thông tin client: " + e.getMessage());

            ClientInfo clientInfo = new ClientInfo(publicKey, aesKey, iv);
            clientDatabase.put(clientId, clientInfo);
        }
    }

    private static String scanSubdomains(String domain, String wordlistPath) {
        StringBuilder found = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new FileReader(wordlistPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String subdomain = line.trim() + "." + domain;
                try {
                    InetAddress.getByName(subdomain);
                    found.append(subdomain).append("\n");
                    System.out.println("✓ Found: " + subdomain);
                } catch (UnknownHostException ignored) {}
            }
        } catch (IOException e) {
            return "ERROR_READING_WORDLIST";
        }

        return found.toString().isEmpty() ? "NO_SUBDOMAINS_FOUND" : found.toString();
    }

    static class ClientInfo {
        private PublicKey publicKey;
        private SecretKey aesKey;
        private IvParameterSpec iv;
        private long timestamp;

        public ClientInfo(PublicKey publicKey, SecretKey aesKey, IvParameterSpec iv) {
            this.publicKey = publicKey;
            this.aesKey = aesKey;
            this.iv = iv;
            this.timestamp = System.currentTimeMillis();
        }

        public PublicKey getPublicKey() { return publicKey; }
        public SecretKey getAesKey() { return aesKey; }
        public IvParameterSpec getIv() { return iv; }
        public long getTimestamp() { return timestamp; }
    }
}
