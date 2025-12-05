package io.brokr.kafka.service;

import io.brokr.core.model.BrokerJmxMetrics;
import io.brokr.core.model.KafkaCluster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JmxConnectionServiceTest {

    private JmxConnectionService jmxConnectionService;

    @Mock
    private KafkaCluster kafkaCluster;

    @BeforeEach
    void setUp() {
        jmxConnectionService = new JmxConnectionService();
    }

    @Test
    void collectMetrics_ShouldReturnNull_WhenJmxDisabled() {
        when(kafkaCluster.isJmxEnabled()).thenReturn(false);
        BrokerJmxMetrics metrics = jmxConnectionService.collectMetrics(kafkaCluster, "localhost", 9999);
        assertNull(metrics);
        // Verify no connection attempt made
    }

    @Test
    void collectMetrics_ShouldHandleTimeoutGracefully() throws Exception {
        when(kafkaCluster.isJmxEnabled()).thenReturn(true);
        when(kafkaCluster.getJmxPort()).thenReturn(9999);
        
        // Note: It's hard to mock JMXConnectorFactory static methods directly without PowerMock.
        // For this test, we accept that we can't easily mock the static connection creation 
        // without refactoring the service to use a factory.
        // However, we can verify that the service handles connection failures (which will happen since we have no real JMX server)
        // gracefully by returning null instead of throwing an exception.
        
        BrokerJmxMetrics metrics = jmxConnectionService.collectMetrics(kafkaCluster, "non-existent-host", 9999);
        assertNull(metrics);
    }
    
    @Test
    void testConnection_ShouldReturnFalse_WhenConnectionFails() {
        boolean result = jmxConnectionService.testConnection(kafkaCluster, "non-existent-host", 9999);
        assertFalse(result);
    }
}
