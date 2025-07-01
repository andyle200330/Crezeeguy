import java.net.*;
import java.io.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class Server {
    private static Connection dbConnection;
    private static Map<String, ClientInfo> clientDatabase = new HashMap<>();

    public static void main(String[] args) {
        try {
            // Khởi tạo database
            initializeDatabase();

            ServerSocket serverSocket = new ServerSocket(8888);
            System.out.println("Server đang chạy trên port 8888...");

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Client kết nối từ: " + socket.getInetAddress());

                // Xử lý mỗi client trong thread riêng
                new Thread(() -> handleClient(socket)).start();
            }

        } catch (IOException | SQLException e) {
            e.printStackTrace();
        }
    }

    private static void initializeDatabase() throws SQLException {
        try {
            // Load MySQL driver
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Thay đổi thông tin kết nối tương ứng
            String url = "jdbc:mysql://localhost:3306/mydatabase";
            String user = "root";
            String password = "";

            dbConnection = DriverManager.getConnection(url, user, password);

            // Tạo bảng nếu chưa có
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
            String clientId = socket.getInetAddress().toString() + ":" + socket.getPort();

            while (true) {
                try {
                    // Nhận SecureMessage từ client
                    SecureMessage secureMessage = (SecureMessage) in.readObject();


                    // Xử lý tin nhắn bảo mật
                    boolean isValid = processSecureMessage(clientId, secureMessage);

                    if (isValid) {
                        System.out.println("✓ Tin nhắn hợp lệ từ " + clientId + ": " + secureMessage.getRawMessage());
                    } else {
                        System.out.println("✗ Tin nhắn không hợp lệ từ " + clientId);
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

    private static boolean processSecureMessage(String clientId, SecureMessage secureMessage) {
        try {
            // 1. Giải mã AES key và IV từ Base64
            byte[] aesKeyBytes = Base64.getDecoder().decode(secureMessage.getAesKey());
            byte[] ivBytes = Base64.getDecoder().decode(secureMessage.getIv());

            SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");
            IvParameterSpec iv = new IvParameterSpec(ivBytes);

            // 2. Giải mã public key bằng AES CBC
            Cipher aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            aesCipher.init(Cipher.DECRYPT_MODE, aesKey, iv);

            byte[] encryptedPublicKeyBytes = Base64.getDecoder().decode(secureMessage.getEncryptedPublicKey());
            byte[] publicKeyBytes = aesCipher.doFinal(encryptedPublicKeyBytes);

            // 3. Tạo PublicKey object
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            // 4. Lưu thông tin vào database
            saveClientInfo(clientId, publicKey, aesKey, iv);

            // 5. Xác thực chữ ký số
            boolean isSignatureValid = verifySignature(
                    secureMessage.getRawMessage(),
                    secureMessage.getBase64Signature(),
                    publicKey
            );

            if (isSignatureValid) {
                System.out.println("✓ Chữ ký số hợp lệ cho client: " + clientId);
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
            // Giải mã chữ ký từ Base64
            byte[] signatureBytes = Base64.getDecoder().decode(base64Signature);

            // Xác thực chữ ký
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
                // Lưu vào SQLite database
                String sql = """
                    INSERT OR REPLACE INTO client_keys (client_id, public_key, aes_key, iv)
                    VALUES (?, ?, ?, ?)
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
                // Lưu vào HashMap (memory)
                ClientInfo clientInfo = new ClientInfo(publicKey, aesKey, iv);
                clientDatabase.put(clientId, clientInfo);

                System.out.println("✓ Đã lưu thông tin client vào memory: " + clientId);
            }

        } catch (SQLException e) {
            System.out.println("Lỗi lưu thông tin client: " + e.getMessage());

            // Fallback to memory storage
            ClientInfo clientInfo = new ClientInfo(publicKey, aesKey, iv);
            clientDatabase.put(clientId, clientInfo);
        }
    }

    // Class để lưu thông tin client trong memory
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

        // Getters
        public PublicKey getPublicKey() { return publicKey; }
        public SecretKey getAesKey() { return aesKey; }
        public IvParameterSpec getIv() { return iv; }
        public long getTimestamp() { return timestamp; }
    }
}