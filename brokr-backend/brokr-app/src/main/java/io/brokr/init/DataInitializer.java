package io.brokr.init;

import io.brokr.core.model.Environment;
import io.brokr.core.model.Organization;
import io.brokr.core.model.Role;
import io.brokr.core.model.User;
import io.brokr.storage.entity.EnvironmentEntity;
import io.brokr.storage.entity.EnvironmentType;
import io.brokr.storage.entity.OrganizationEntity;
import io.brokr.storage.entity.UserEntity;
import io.brokr.storage.repository.EnvironmentRepository;
import io.brokr.storage.repository.OrganizationRepository;
import io.brokr.storage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {
    
    // Security: Fail fast if this runs in production
    @org.springframework.beans.factory.annotation.Value("${spring.profiles.active:}")
    private String activeProfiles;

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final EnvironmentRepository environmentRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Security: Fail fast if misconfigured to run in production
        if (activeProfiles != null && (activeProfiles.contains("prod") || 
            (!activeProfiles.contains("dev") && !activeProfiles.contains("test")))) {
            throw new IllegalStateException(
                "DataInitializer should NEVER run in production! " +
                "Active profiles: " + activeProfiles + ". " +
                "This component seeds hardcoded credentials and should only run in dev/test environments."
            );
        }
        
        initializeSuperAdmin();
        initializeSampleOrganization();
    }

    private void initializeSuperAdmin() {
        if (userRepository.existsByUsername("admin")) {
            log.info("Super admin already exists");
            return;
        }

        User admin = User.builder()
                .id(UUID.randomUUID().toString())
                .username("admin")
                .email("admin@brokr.com")
                .password(passwordEncoder.encode("admin123"))
                .firstName("Super")
                .lastName("Admin")
                .role(Role.SUPER_ADMIN)
                .isActive(true)
                .build();

        userRepository.save(UserEntity.fromDomain(admin));
        log.info("Created super admin: admin/admin123");
    }

    private void initializeSampleOrganization() {
        if (organizationRepository.existsByName("Sample Organization")) {
            log.info("Sample organization already exists");
            return;
        }

        // Create organization
        Organization organization = Organization.builder()
                .id(UUID.randomUUID().toString())
                .name("Sample Organization")
                .description("A sample organization for demonstration")
                .isActive(true)
                .build();

        OrganizationEntity orgEntity = organizationRepository.save(OrganizationEntity.fromDomain(organization));

        // Create environments
        List<Environment> environments = Arrays.asList(
                Environment.builder()
                        .id(UUID.randomUUID().toString())
                        .name("Hotfix")
                        .type(EnvironmentType.NON_PROD_HOTFIX.name())
                        .description("Non-production hotfix environment")
                        .isActive(true)
                        .build(),
                Environment.builder()
                        .id(UUID.randomUUID().toString())
                        .name("Minor")
                        .type(EnvironmentType.NON_PROD_MINOR.name())
                        .description("Non-production minor environment")
                        .isActive(true)
                        .build(),
                Environment.builder()
                        .id(UUID.randomUUID().toString())
                        .name("Major")
                        .type(EnvironmentType.NON_PROD_MAJOR.name())
                        .description("Non-production major environment")
                        .isActive(true)
                        .build(),
                Environment.builder()
                        .id(UUID.randomUUID().toString())
                        .name("Production")
                        .type(EnvironmentType.PROD.name())
                        .description("Production environment")
                        .isActive(true)
                        .build()
        );

        for (Environment env : environments) {
            EnvironmentEntity envEntity = EnvironmentEntity.fromDomain(env);
            envEntity.setOrganization(orgEntity);
            environmentRepository.save(envEntity);
        }

        // Create org admin
        User orgAdmin = User.builder()
                .id(UUID.randomUUID().toString())
                .username("orgadmin")
                .email("orgadmin@sample.com")
                .password(passwordEncoder.encode("orgadmin123"))
                .firstName("Org")
                .lastName("Admin")
                .role(Role.ADMIN)
                .organizationId(orgEntity.getId())
                .accessibleEnvironmentIds(environments.stream().map(Environment::getId).toList())
                .isActive(true)
                .build();

        userRepository.save(UserEntity.fromDomain(orgAdmin));

        // Create developer user
        User developer = User.builder()
                .id(UUID.randomUUID().toString())
                .username("developer")
                .email("developer@sample.com")
                .password(passwordEncoder.encode("developer123"))
                .firstName("Dev")
                .lastName("User")
                .role(Role.VIEWER)
                .organizationId(orgEntity.getId())
                .accessibleEnvironmentIds(environments.stream()
                        .filter(env -> !env.getType().equals(EnvironmentType.PROD.name()))
                        .map(Environment::getId)
                        .toList())
                .isActive(true)
                .build();

        userRepository.save(UserEntity.fromDomain(developer));

        log.info("Created sample organization with environments and users");
    }
}