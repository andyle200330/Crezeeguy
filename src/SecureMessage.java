import java.io.Serializable;

class SecureMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String rawMessage;
    private String base64Signature;
    private String encryptedPublicKey;
    private String aesKey;
    private String iv;

    public SecureMessage(String rawMessage, String base64Signature,
                         String encryptedPublicKey, String aesKey, String iv) {
        this.rawMessage = rawMessage;
        this.base64Signature = base64Signature;
        this.encryptedPublicKey = encryptedPublicKey;
        this.aesKey = aesKey;
        this.iv = iv;
    }

    // Getters
    public String getRawMessage() { return rawMessage; }
    public String getBase64Signature() { return base64Signature; }
    public String getEncryptedPublicKey() { return encryptedPublicKey; }
    public String getAesKey() { return aesKey; }
    public String getIv() { return iv; }
}