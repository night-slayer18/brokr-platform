package io.brokr.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class BackupCodeService {

    private static final String CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // Exclude confusing characters (0, O, I, 1)

    @Value("${mfa.backup-codes.count:10}")
    private int codeCount;

    @Value("${mfa.backup-codes.length:8}")
    private int codeLength;

    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom;

    public BackupCodeService() {
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.secureRandom = new SecureRandom();
    }

    /**
     * Generate a list of backup codes
     */
    public List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < codeCount; i++) {
            codes.add(generateRandomCode());
        }
        return codes;
    }

    /**
     * Generate a single random backup code
     */
    private String generateRandomCode() {
        StringBuilder code = new StringBuilder(getCodeLength());
        for (int i = 0; i < getCodeLength(); i++) {
            int index = secureRandom.nextInt(CHARACTERS.length());
            code.append(CHARACTERS.charAt(index));
        }
        return code.toString();
    }

    /**
     * Hash a backup code using BCrypt
     */
    public String hashCode(String code) {
        return passwordEncoder.encode(code);
    }

    /**
     * Verify a backup code against its hash
     */
    public boolean verifyCode(String code, String hash) {
        try {
            return passwordEncoder.matches(code, hash);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the number of codes to generate
     */
    public int getCodeCount() {
        return codeCount;
    }

    /**
     * Get the length of each code
     */
    public int getCodeLength() {
        return codeLength;
    }
}

