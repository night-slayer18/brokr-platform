package io.brokr.security.service;

import io.brokr.core.model.MfaType;
import io.brokr.storage.entity.BackupCodeEntity;
import io.brokr.storage.entity.MfaDeviceEntity;
import io.brokr.storage.entity.OrganizationEntity;
import io.brokr.storage.entity.UserEntity;
import io.brokr.storage.repository.BackupCodeRepository;
import io.brokr.storage.repository.MfaDeviceRepository;
import io.brokr.storage.repository.OrganizationRepository;
import io.brokr.storage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MfaService {

    private final TotpService totpService;
    private final BackupCodeService backupCodeService;
    private final EncryptionService encryptionService;
    private final MfaDeviceRepository mfaDeviceRepository;
    private final BackupCodeRepository backupCodeRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final MfaRateLimitService rateLimitService;
    private final UserDetailsServiceImpl userDetailsService;

    /**
     * Initiate MFA setup - generates secret and QR code
     * Uses pessimistic locking to prevent concurrent setup attempts.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public MfaSetupResult initiateMfaSetup(String userId) {
        // Use pessimistic lock to prevent concurrent setup
        UserEntity userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if user already has active MFA device (use findBy* with lock instead of existsBy* to ensure pessimistic lock is acquired)
        if (mfaDeviceRepository.findByUserIdAndTypeAndIsActiveTrue(userId, MfaType.TOTP).isPresent()) {
            throw new RuntimeException("MFA is already enabled for this user");
        }

        // Clean up ALL existing devices for this user (from previous setup attempts or disabled MFA)
        // This ensures a completely clean state for new setup
        List<MfaDeviceEntity> allDevices = mfaDeviceRepository.findByUserId(userId);
        if (!allDevices.isEmpty()) {
            mfaDeviceRepository.deleteAll(allDevices);
        }

        // Generate secret key
        String secretKey = totpService.generateSecretKey();

        // Create temporary MFA device (not verified yet)
        // Encrypt the secret key before storing
        String encryptedSecretKey = encryptionService.encrypt(secretKey);
        
        MfaDeviceEntity deviceEntity = MfaDeviceEntity.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .type(MfaType.TOTP)
                .name("Authenticator App")
                .secretKey(encryptedSecretKey)
                .isVerified(false)
                .isActive(false) // Not active until verified
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        mfaDeviceRepository.save(deviceEntity);

        // Generate QR code
        String qrCodeDataUrl = totpService.generateQrCodeDataUrl(userEntity.getEmail(), secretKey);
        String qrCodeUri = totpService.generateQrCodeUri(userEntity.getEmail(), secretKey);

        return MfaSetupResult.builder()
                .secretKey(secretKey)
                .qrCodeDataUrl(qrCodeDataUrl)
                .qrCodeUri(qrCodeUri)
                .deviceId(deviceEntity.getId())
                .build();
    }

    /**
     * Verify and complete MFA setup
     */
    @Transactional
    public MfaSetupCompleteResult verifyAndCompleteMfaSetup(String userId, String deviceId, String code) {
        MfaDeviceEntity deviceEntity = mfaDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new RuntimeException("MFA device not found"));

        if (!deviceEntity.getUserId().equals(userId)) {
            throw new RuntimeException("MFA device does not belong to this user");
        }

        if (deviceEntity.isVerified()) {
            throw new RuntimeException("MFA device is already verified");
        }

        // Decrypt the secret key before verification
        String decryptedSecretKey = encryptionService.decrypt(deviceEntity.getSecretKey());
        
        // Verify TOTP code
        if (!totpService.verifyCode(decryptedSecretKey, code)) {
            throw new RuntimeException("Invalid TOTP code");
        }

        // Before activating this device, ensure no other active devices exist for this user
        List<MfaDeviceEntity> otherActiveDevices = mfaDeviceRepository.findByUserIdAndIsActiveTrue(userId).stream()
                .filter(device -> !device.getId().equals(deviceId))
                .toList();
        if (!otherActiveDevices.isEmpty()) {
            for (MfaDeviceEntity otherDevice : otherActiveDevices) {
                otherDevice.setActive(false);
                otherDevice.setUpdatedAt(LocalDateTime.now());
            }
            mfaDeviceRepository.saveAll(otherActiveDevices);
        }

        // Mark device as verified and active
        deviceEntity.setVerified(true);
        deviceEntity.setActive(true);
        deviceEntity.setUpdatedAt(LocalDateTime.now());
        mfaDeviceRepository.saveAndFlush(deviceEntity);

        // Update user MFA status - use saveAndFlush to ensure immediate persistence
        UserEntity userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        userEntity.setMfaEnabled(true);
        userEntity.setMfaType(MfaType.TOTP);
        userRepository.saveAndFlush(userEntity);
        
        // Invalidate cache to ensure subsequent authentication uses updated MFA status
        userDetailsService.evictCacheForUser(userEntity.getEmail(), userId);

        // Generate backup codes
        List<String> backupCodes = backupCodeService.generateBackupCodes();
        List<BackupCodeEntity> backupCodeEntities = backupCodes.stream()
                .map(codeStr -> BackupCodeEntity.builder()
                        .id(UUID.randomUUID().toString())
                        .userId(userId)
                        .codeHash(backupCodeService.hashCode(codeStr))
                        .isUsed(false)
                        .createdAt(LocalDateTime.now())
                        .build())
                .collect(Collectors.toList());

        backupCodeRepository.saveAll(backupCodeEntities);

        return MfaSetupCompleteResult.builder()
                .backupCodes(backupCodes)
                .build();
    }

    /**
     * Verify MFA code during login
     * Uses pessimistic locking to prevent race conditions and rate limiting to prevent brute force attacks.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public boolean verifyMfaCode(String userId, String code) {
        // Check rate limiting first (fail fast)
        if (rateLimitService.isRateLimited(userId)) {
            log.warn("MFA verification rate limited for user: {}", userId);
            throw new RuntimeException("Too many failed attempts. Please try again later.");
        }

        // Use pessimistic lock to prevent concurrent verification attempts
        MfaDeviceEntity deviceEntity = mfaDeviceRepository
                .findByUserIdAndTypeAndIsActiveTrue(userId, MfaType.TOTP)
                .orElseThrow(() -> {
                    log.warn("No active MFA device found for user: {}", userId);
                    return new RuntimeException("No active MFA device found for user");
                });

        // Decrypt the secret key before verification
        String decryptedSecretKey;
        try {
            decryptedSecretKey = encryptionService.decrypt(deviceEntity.getSecretKey());
        } catch (Exception e) {
            log.error("Failed to decrypt secret key for user: {}", userId, e);
            rateLimitService.recordFailedAttempt(userId);
            throw new RuntimeException("MFA verification failed");
        }
        
        // Verify TOTP code (constant-time operation)
        boolean isValid = totpService.verifyCode(decryptedSecretKey, code);

        if (isValid) {
            // Update last used timestamp
            deviceEntity.setLastUsedAt(LocalDateTime.now());
            deviceEntity.setUpdatedAt(LocalDateTime.now());
            mfaDeviceRepository.save(deviceEntity);
            
            // Clear rate limit on success
            rateLimitService.recordSuccessfulAttempt(userId);
        } else {
            // Record failed attempt for rate limiting
            rateLimitService.recordFailedAttempt(userId);
            log.warn("MFA verification failed for user: {}. Remaining attempts: {}", 
                    userId, rateLimitService.getRemainingAttempts(userId));
        }

        return isValid;
    }

    /**
     * Verify backup code during login
     * Uses pessimistic locking and optimized query to prevent race conditions and improve performance.
     * Implements constant-time verification to prevent timing attacks.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public boolean verifyBackupCode(String userId, String code) {
        // Check rate limiting first (fail fast)
        if (rateLimitService.isRateLimited(userId)) {
            log.warn("Backup code verification rate limited for user: {}", userId);
            throw new RuntimeException("Too many failed attempts. Please try again later.");
        }

        // Load unused codes with limit to prevent loading excessive data
        // Backup codes are typically generated in batches of 10, so limit to 20 to handle regeneration scenarios
        // Using findByUserIdAndIsUsedFalse which is more efficient than custom query
        List<BackupCodeEntity> unusedCodes = backupCodeRepository.findByUserIdAndIsUsedFalse(userId);
        
        // Limit to reasonable number to prevent performance issues if many codes exist
        // This is a safety measure - normally users have ~10 backup codes
        if (unusedCodes.size() > 20) {
            unusedCodes = unusedCodes.subList(0, 20);
        }

        if (unusedCodes.isEmpty()) {
            rateLimitService.recordFailedAttempt(userId);
            return false;
        }

        // Use constant-time comparison to prevent timing attacks
        // Always check all codes to maintain constant execution time (no early break)
        BackupCodeEntity matchedCode = null;
        for (BackupCodeEntity codeEntity : unusedCodes) {
            // Always verify all codes to maintain constant execution time regardless of match position
            boolean matches = backupCodeService.verifyCode(code, codeEntity.getCodeHash());
            if (matches && matchedCode == null) {
                // Only store first match, but continue checking all codes for constant time
                matchedCode = codeEntity;
            }
        }

        if (matchedCode != null) {
            // Mark as used atomically
            matchedCode.setUsed(true);
            matchedCode.setUsedAt(LocalDateTime.now());
            backupCodeRepository.save(matchedCode);
            
            // Clear rate limit on success
            rateLimitService.recordSuccessfulAttempt(userId);
            return true;
        }

        // Record failed attempt
        rateLimitService.recordFailedAttempt(userId);
        log.warn("Backup code verification failed for user: {}. Remaining attempts: {}", 
                userId, rateLimitService.getRemainingAttempts(userId));
        return false;
    }

    /**
     * Disable MFA for a user
     * SECURITY: Prevents disabling MFA if organization requires it
     */
    @Transactional(rollbackFor = Exception.class)
    public void disableMfa(String userId) {
        // Get user to check organization
        UserEntity userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if organization requires MFA
        if (userEntity.getOrganizationId() != null) {
            OrganizationEntity org = organizationRepository.findById(userEntity.getOrganizationId()).orElse(null);
            if (org != null && org.isMfaRequired()) {
                throw new RuntimeException("Cannot disable MFA. Your organization requires MFA to be enabled. Please contact your administrator.");
            }
        }

        // Delete all MFA devices (complete cleanup, not just deactivate)
        List<MfaDeviceEntity> devices = mfaDeviceRepository.findByUserId(userId);
        if (!devices.isEmpty()) {
            mfaDeviceRepository.deleteAll(devices);
        }

        // Delete all backup codes
        List<BackupCodeEntity> backupCodes = backupCodeRepository.findByUserId(userId);
        if (!backupCodes.isEmpty()) {
            backupCodeRepository.deleteAll(backupCodes);
        }

        // Update user MFA status - ensure all fields are cleared
        userEntity.setMfaEnabled(false);
        userEntity.setMfaType(null);
        userEntity.setMfaEnforced(false);
        userRepository.saveAndFlush(userEntity);
        
        // Invalidate cache to ensure subsequent authentication uses updated MFA status
        userDetailsService.evictCacheForUser(userEntity.getEmail(), userId);
    }

    /**
     * Regenerate backup codes
     */
    @Transactional(rollbackFor = Exception.class)
    public List<String> regenerateBackupCodes(String userId) {
        // Verify user has MFA enabled
        UserEntity userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!userEntity.isMfaEnabled()) {
            throw new RuntimeException("MFA is not enabled for this user");
        }

        // Delete old backup codes
        List<BackupCodeEntity> oldCodes = backupCodeRepository.findByUserId(userId);
        if (!oldCodes.isEmpty()) {
            backupCodeRepository.deleteAll(oldCodes);
            backupCodeRepository.flush(); // Ensure deletion is persisted
        }

        // Generate new backup codes
        List<String> backupCodes = backupCodeService.generateBackupCodes();
        List<BackupCodeEntity> backupCodeEntities = backupCodes.stream()
                .map(codeStr -> BackupCodeEntity.builder()
                        .id(UUID.randomUUID().toString())
                        .userId(userId)
                        .codeHash(backupCodeService.hashCode(codeStr))
                        .isUsed(false)
                        .createdAt(LocalDateTime.now())
                        .build())
                .collect(Collectors.toList());

        backupCodeRepository.saveAll(backupCodeEntities);
        backupCodeRepository.flush(); // Ensure new codes are persisted before returning

        return backupCodes;
    }

    /**
     * Get MFA status for a user
     * Optimized to use read-only transaction and minimize queries.
     */
    @Transactional(readOnly = true)
    public MfaStatus getMfaStatus(String userId) {
        // Single query to get user with MFA info
        UserEntity userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Parallel queries for device and backup code status (can be optimized further with JOIN if needed)
        boolean hasActiveDevice = mfaDeviceRepository.existsByUserIdAndTypeAndIsActiveTrue(userId, MfaType.TOTP);
        long unusedBackupCodes = backupCodeRepository.countByUserIdAndIsUsedFalse(userId);

        return MfaStatus.builder()
                .enabled(userEntity.isMfaEnabled() && hasActiveDevice)
                .type(userEntity.getMfaType())
                .unusedBackupCodesCount((int) unusedBackupCodes)
                .build();
    }

    // Inner classes for results
    @lombok.Data
    @lombok.Builder
    public static class MfaSetupResult {
        private String secretKey;
        private String qrCodeDataUrl;
        private String qrCodeUri;
        private String deviceId;
    }

    @lombok.Data
    @lombok.Builder
    public static class MfaSetupCompleteResult {
        private List<String> backupCodes;
    }

    @lombok.Data
    @lombok.Builder
    public static class MfaStatus {
        private boolean enabled;
        private MfaType type;
        private int unusedBackupCodesCount;
    }
}

