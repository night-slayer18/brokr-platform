package io.brokr.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
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
     * Initialize the encryption key from configuration or generate a new one.
     * In production, the key should be provided via environment variable or secure vault.
     * Thread-safe initialization using double-checked locking pattern.
     */
    private SecretKey getSecretKey() {
        if (secretKey != null) {
            return secretKey;
        }
        
        synchronized (keyLock) {
            if (secretKey != null) {
                return secretKey;
            }

        if (encryptionKeyBase64 == null || encryptionKeyBase64.isEmpty()) {
            log.warn("MFA encryption key not configured. Generating a temporary key. " +
                    "This should be set via mfa.encryption.key property in production!");
            // Generate a temporary key (not recommended for production)
            try {
                KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
                keyGenerator.init(AES_KEY_SIZE);
                secretKey = keyGenerator.generateKey();
                log.warn("Generated temporary encryption key. MFA secrets will be lost on restart!");
                return secretKey;
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate encryption key", e);
            }
        }

        try {
            byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
            if (keyBytes.length != AES_KEY_SIZE / 8) {
                throw new IllegalArgumentException("Encryption key must be " + (AES_KEY_SIZE / 8) + " bytes (256 bits)");
            }
            secretKey = new SecretKeySpec(keyBytes, "AES");
            return secretKey;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize encryption key", e);
        }
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
        } catch (IllegalArgumentException e) {
            log.error("Invalid encrypted data format", e);
            throw new RuntimeException("Invalid encrypted data format", e);
        } catch (Exception e) {
            log.error("Failed to decrypt data", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }
}

