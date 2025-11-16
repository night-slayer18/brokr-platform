package io.brokr.security.service;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Slf4j
@Service
public class TotpService {

    private static final int SECRET_LENGTH = 32;

    private final SecretGenerator secretGenerator;
    private final CodeGenerator codeGenerator;
    private final CodeVerifier codeVerifier;
    private final QrGenerator qrGenerator;
    private final TimeProvider timeProvider;

    @Value("${mfa.totp.issuer:Brokr Platform}")
    private String issuer;

    public TotpService() {
        this.secretGenerator = new DefaultSecretGenerator(SECRET_LENGTH);
        this.codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1);
        this.timeProvider = new SystemTimeProvider();
        this.codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        this.qrGenerator = new ZxingPngQrGenerator();
    }

    /**
     * Generate a new TOTP secret key
     */
    public String generateSecretKey() {
        return secretGenerator.generate();
    }

    /**
     * Generate QR code data URL for TOTP setup
     */
    public String generateQrCodeDataUrl(String email, String secretKey) {
        try {
            QrData qrData = new QrData.Builder()
                    .label(email)
                    .secret(secretKey)
                    .issuer(issuer)
                    .algorithm(HashingAlgorithm.SHA1)
                    .digits(6)
                    .period(30)
                    .build();

            byte[] qrCodeImage = qrGenerator.generate(qrData);
            String base64Image = Base64.getEncoder().encodeToString(qrCodeImage);
            return "data:image/png;base64," + base64Image;
        } catch (QrGenerationException e) {
            log.error("Failed to generate QR code", e);
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    /**
     * Generate QR code URI (otpauth:// format)
     */
    public String generateQrCodeUri(String email, String secretKey) {
        String label = email.replace("@", "%40");
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
                issuer, label, secretKey, issuer);
    }

    /**
     * Verify a TOTP code
     */
    public boolean verifyCode(String secretKey, String code) {
        try {
            return codeVerifier.isValidCode(secretKey, code);
        } catch (Exception e) {
            return false;
        }
    }


    /**
     * Generate current TOTP code for a secret (for testing)
     */
    public String generateCurrentCode(String secretKey) {
        try {
            return codeGenerator.generate(secretKey, System.currentTimeMillis() / 1000 / 30);
        } catch (CodeGenerationException e) {
            log.error("Failed to generate TOTP code", e);
            throw new RuntimeException("Failed to generate TOTP code", e);
        }
    }
}

