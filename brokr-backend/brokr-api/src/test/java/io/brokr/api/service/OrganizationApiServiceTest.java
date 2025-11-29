package io.brokr.api.service;

import io.brokr.api.input.EnvironmentInput;
import io.brokr.api.input.OrganizationInput;
import io.brokr.core.model.Organization;
import io.brokr.storage.entity.EnvironmentEntity;
import io.brokr.storage.entity.EnvironmentType;
import io.brokr.storage.entity.OrganizationEntity;
import io.brokr.storage.repository.ApiKeyRepository;
import io.brokr.storage.repository.EnvironmentRepository;
import io.brokr.storage.repository.KafkaClusterRepository;
import io.brokr.storage.repository.OrganizationRepository;
import io.brokr.storage.repository.UserRepository;
import io.brokr.security.service.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationApiServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private EnvironmentRepository environmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApiKeyRepository apiKeyRepository;
    @Mock
    private KafkaClusterRepository kafkaClusterRepository;
    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private OrganizationApiService organizationApiService;

    @Test
    void createOrganization_WithInitialEnvironments_ShouldCreateEnvironments() {
        // Arrange
        OrganizationInput input = new OrganizationInput();
        input.setName("Test Org");
        input.setDescription("Test Description");
        input.setIsActive(true);
        
        EnvironmentInput envInput = new EnvironmentInput();
        envInput.setName("Dev");
        envInput.setType(EnvironmentType.NON_PROD_MINOR);
        envInput.setDescription("Dev Env");
        
        input.setInitialEnvironments(List.of(envInput));

        OrganizationEntity savedOrgEntity = OrganizationEntity.builder()
                .id(UUID.randomUUID().toString())
                .name("Test Org")
                .description("Test Description")
                .isActive(true)
                .build();

        when(organizationRepository.existsByName("Test Org")).thenReturn(false);
        when(organizationRepository.save(any(OrganizationEntity.class))).thenReturn(savedOrgEntity);

        // Act
        Organization result = organizationApiService.createOrganization(input);

        // Assert
        assertNotNull(result);
        assertEquals("Test Org", result.getName());
        
        // Verify Organization save
        verify(organizationRepository).save(any(OrganizationEntity.class));
        
        // Verify Environment save
        ArgumentCaptor<List<EnvironmentEntity>> envCaptor = ArgumentCaptor.forClass(List.class);
        verify(environmentRepository).saveAll(envCaptor.capture());
        
        List<EnvironmentEntity> savedEnvs = envCaptor.getValue();
        assertEquals(1, savedEnvs.size());
        assertEquals("Dev", savedEnvs.get(0).getName());
        assertEquals(EnvironmentType.NON_PROD_MINOR, savedEnvs.get(0).getType());
        assertEquals(savedOrgEntity, savedEnvs.get(0).getOrganization());
        
        // Verify result contains environments
        assertNotNull(result.getEnvironments());
        assertEquals(1, result.getEnvironments().size());
        assertEquals("Dev", result.getEnvironments().get(0).getName());
    }
}
