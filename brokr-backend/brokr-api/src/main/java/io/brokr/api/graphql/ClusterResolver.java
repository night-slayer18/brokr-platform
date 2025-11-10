package io.brokr.api.graphql;

import io.brokr.api.input.KafkaClusterInput;
import io.brokr.api.service.*;
import io.brokr.core.model.*;
import io.brokr.kafka.service.KafkaAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ClusterResolver {

    private final ClusterApiService clusterApiService;
    private final OrganizationApiService organizationApiService;
    private final EnvironmentApiService environmentApiService;
    private final SchemaRegistryApiService schemaRegistryApiService;
    private final KafkaConnectApiService kafkaConnectApiService;
    private final KafkaStreamsApiService kafkaStreamsApiService;
    private final KafkaAdminService kafkaAdminService;

    @QueryMapping
    @PreAuthorize("#organizationId == null or @authorizationService.hasAccessToOrganization(#organizationId)")
    public List<KafkaCluster> clusters(@Argument String organizationId, @Argument String environmentId) {
        return clusterApiService.listAuthorizedClusters(organizationId, environmentId);
    }

    @QueryMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public KafkaCluster cluster(@Argument String id) {
        return clusterApiService.getClusterById(id);
    }

    @BatchMapping(typeName = "KafkaCluster", field = "organization")
    public Map<KafkaCluster, Organization> getOrganization(List<KafkaCluster> clusters) {
        List<String> organizationIds = clusters.stream().map(KafkaCluster::getOrganizationId).distinct().toList();
        Map<String, Organization> orgsById = organizationApiService.getOrganizationsByIds(organizationIds);
        return clusters.stream().collect(Collectors.toMap(Function.identity(), c -> orgsById.get(c.getOrganizationId())));
    }

    @BatchMapping(typeName = "KafkaCluster", field = "environment")
    public Map<KafkaCluster, Environment> getEnvironment(List<KafkaCluster> clusters) {
        List<String> environmentIds = clusters.stream().map(KafkaCluster::getEnvironmentId).distinct().toList();
        Map<String, Environment> envsById = environmentApiService.getEnvironmentsByIds(environmentIds);
        return clusters.stream().collect(Collectors.toMap(Function.identity(), c -> envsById.get(c.getEnvironmentId())));
    }

    @BatchMapping(typeName = "KafkaCluster", field = "schemaRegistries")
    public Map<KafkaCluster, List<SchemaRegistry>> getSchemaRegistries(List<KafkaCluster> clusters) {
        List<String> clusterIds = clusters.stream().map(KafkaCluster::getId).toList();
        Map<String, List<SchemaRegistry>> schemasByClusterId = schemaRegistryApiService.getSchemaRegistriesForClusters(clusterIds);
        return clusters.stream().collect(Collectors.toMap(Function.identity(), c -> schemasByClusterId.getOrDefault(c.getId(), List.of())));
    }

    @BatchMapping(typeName = "KafkaCluster", field = "kafkaConnects")
    public Map<KafkaCluster, List<KafkaConnect>> getKafkaConnects(List<KafkaCluster> clusters) {
        List<String> clusterIds = clusters.stream().map(KafkaCluster::getId).toList();
        Map<String, List<KafkaConnect>> connectsByClusterId = kafkaConnectApiService.getKafkaConnectsForClusters(clusterIds);
        return clusters.stream().collect(Collectors.toMap(Function.identity(), c -> connectsByClusterId.getOrDefault(c.getId(), List.of())));
    }

    @BatchMapping(typeName = "KafkaCluster", field = "kafkaStreamsApplications")
    public Map<KafkaCluster, List<KafkaStreamsApplication>> getKafkaStreamsApplications(List<KafkaCluster> clusters) {
        List<String> clusterIds = clusters.stream().map(KafkaCluster::getId).toList();
        Map<String, List<KafkaStreamsApplication>> streamsByClusterId = kafkaStreamsApiService.getKafkaStreamsApplicationsForClusters(clusterIds);
        return clusters.stream().collect(Collectors.toMap(Function.identity(), c -> streamsByClusterId.getOrDefault(c.getId(), List.of())));
    }

    @BatchMapping(typeName = "KafkaCluster", field = "brokers")
    public Map<KafkaCluster, List<BrokerNode>> getBrokers(List<KafkaCluster> clusters) {
        return clusters.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        kafkaAdminService::getClusterNodes
                ));
    }

    @BatchMapping(typeName = "KafkaCluster", field = "topics")
    public Map<KafkaCluster, List<Topic>> getTopics(List<KafkaCluster> clusters) {
        return clusters.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        kafkaAdminService::listTopics
                ));
    }

    @BatchMapping(typeName = "KafkaCluster", field = "consumerGroups")
    public Map<KafkaCluster, List<ConsumerGroup>> getConsumerGroups(List<KafkaCluster> clusters) {
        return clusters.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        kafkaAdminService::listConsumerGroups
                ));
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToEnvironment(#input.environmentId)")
    public KafkaCluster createCluster(@Argument KafkaClusterInput input) {
        return clusterApiService.createCluster(input);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public KafkaCluster updateCluster(@Argument String id, @Argument KafkaClusterInput input) {
        return clusterApiService.updateCluster(id, input);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public boolean deleteCluster(@Argument String id) {
        return clusterApiService.deleteCluster(id);
    }

    @MutationMapping
    @PreAuthorize("@authorizationService.hasAccessToCluster(#id)")
    public boolean testClusterConnection(@Argument String id) {
        return clusterApiService.testClusterConnection(id);
    }
}