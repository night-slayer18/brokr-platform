package io.brokr.security.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for encrypting and decrypting sensitive data like TOTP secret keys.
 * Uses AES-256-GCM for authenticated encryption.
 */
@Slf4j
@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 16; // 128 bits
    private static final int AES_KEY_SIZE = 256;

    @Value("${mfa.encryption.key:}")
    private String encryptionKeyBase64;

    private volatile SecretKey secretKey;
    private final Object keyLock = new Object();
    
    /**
     * Validates MFA encryption key configuration on application startup.
     * CRITICAL SECURITY: Ensures MFA secrets can be properly encrypted/decrypted.
     * Always requires a properly configured key - no temporary keys allowed.
     */
    @PostConstruct
    public void validateAndInitializeKey() {
        if (encryptionKeyBase64 == null || encryptionKeyBase64.trim().isEmpty()) {
            throw new IllegalStateException(
                "CRITICAL SECURITY ERROR: MFA encryption key (mfa.encryption.key) is not configured. " +
                "MFA secrets cannot be encrypted without a persistent key. " +
                "This key MUST be set via environment variable or application.yml. " +
                "Generate a key using: openssl rand -base64 32"
            );
        }
        
        // Validate the provided key
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encryptionKeyBase64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "CRITICAL SECURITY ERROR: MFA encryption key is not valid Base64. " +
                "The key must be Base64-encoded. " +
                "Generate a valid key using: openssl rand -base64 32", e
            );
        }
        
        if (keyBytes.length != AES_KEY_SIZE / 8) {
            throw new IllegalStateException(
                "CRITICAL SECURITY ERROR: MFA encryption key is " + keyBytes.length + " bytes. " +
                "AES-256 requires exactly 32 bytes (256 bits). " +
                "Current key provides only " + (keyBytes.length * 8) + " bits. " +
                "Generate a correct key using: openssl rand -base64 32"
            );
        }
        
        secretKey = new SecretKeySpec(keyBytes, "AES");
        log.info("MFA encryption key validated successfully: 256-bit AES-GCM");
    }

    /**
     * Get the secret key for encryption/decryption.
     * Thread-safe access using double-checked locking.
     */
    private SecretKey getSecretKey() {
        if (secretKey != null) {
            return secretKey;
        }
        
        synchronized (keyLock) {
            if (secretKey != null) {
                return secretKey;
            }
            
            // This should never happen if @PostConstruct ran successfully
            throw new IllegalStateException(
                "Encryption key not initialized. This indicates a critical initialization failure."
            );
        }
    }

    /**
     * Encrypt a plaintext string.
     * 
     * @param plaintext The text to encrypt
     * @return Base64-encoded encrypted string (includes IV)
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }

        try {
            SecretKey key = getSecretKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);
            
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
            
            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = cipher.doFinal(plaintextBytes);
            
            // Prepend IV to ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);
            
            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("Failed to encrypt data", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypt an encrypted string.
     * 
     * @param encrypted The Base64-encoded encrypted string (includes IV)
     * @return The decrypted plaintext
     * @throws RuntimeException if decryption fails
     */
    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) {
            throw new RuntimeException("Cannot decrypt null or empty value");
        }

        try {
            SecretKey key = getSecretKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            
            byte[] encryptedBytes = Base64.getDecoder().decode(encrypted);
            
            // Check if this looks like encrypted data (should have IV + ciphertext, minimum length)
            if (encryptedBytes.length < GCM_IV_LENGTH + GCM_TAG_LENGTH) {
                throw new RuntimeException("Invalid encrypted data format: too short");
            }
            
            // Extract IV from the beginning
            ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedBytes);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);
            
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);
            
            byte[] plaintextBytes = cipher.doFinal(ciphertext);
            return new String(plaintextBytes, StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            // GCM authentication failure - indicates tampering or invalid key
            log.error("GCM authentication failed - encrypted data may have been tampered with or key is incorrect", e);
            throw new RuntimeException("Decryption failed: authentication error", e);
        } catch (IllegalArgumentException e) {
            log.error("Invalid encrypted data format", e);
            throw new RuntimeException("Invalid encrypted data format", e);
        } catch (Exception e) {
            log.error("Failed to decrypt data", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }
}

