package io.brokr.kafka.service;

import io.brokr.core.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.common.ConsumerGroupState;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaStreamsService {

    private final KafkaConnectionService connectionService;

    /**
     * Fetches the live state of a Kafka Streams application by describing its
     * internal consumer group.
     */
    public StreamsState getState(KafkaStreamsApplication app, KafkaCluster cluster) {
        try {
            AdminClient adminClient = connectionService.getOrCreateAdminClient(cluster);
            ConsumerGroupDescription groupDesc = getGroupDescription(adminClient, app.getApplicationId());
            if (groupDesc == null) {
                return StreamsState.NOT_RUNNING;
            }
            return mapKafkaStateToStreamsState(groupDesc.state());
        } catch (Exception e) {
            log.warn("Could not get state for streams app [{}]: {}", app.getApplicationId(), e.getMessage());
            connectionService.removeAdminClient(cluster.getId());
            // Check if the specific cause is GroupIdNotFound
            if (e.getCause() instanceof GroupIdNotFoundException || e instanceof GroupIdNotFoundException) {
                return StreamsState.NOT_RUNNING;
            }
            // For other errors (e.g., connection timeout), mark as ERROR
            return StreamsState.ERROR;
        }
    }

    /**
     * Fetches live thread/task metadata by mapping consumer group members
     * to ThreadMetadata.
     * <p>
     * Note: This provides a simplified view based on consumer group assignments.
     * True Kafka Streams TaskMetadata is typically only available via JMX or a
     * custom REST endpoint on the streams application itself.
     */
    public List<ThreadMetadata> getThreads(KafkaStreamsApplication app, KafkaCluster cluster) {
        try {
            AdminClient adminClient = connectionService.getOrCreateAdminClient(cluster);
            ConsumerGroupDescription groupDesc = getGroupDescription(adminClient, app.getApplicationId());
            if (groupDesc == null) {
                return Collections.emptyList();
            }

            // Get the overall state to apply to threads and tasks
            String groupStateName = mapKafkaStateToStreamsState(groupDesc.state()).name();

            // Map each consumer group member (a "thread") to our ThreadMetadata model
            return groupDesc.members().stream()
                    .map(member -> {
                        // Map each partition assignment to our TaskMetadata model
                        List<TaskMetadata> tasks = member.assignment().topicPartitions().stream()
                                .map(tp -> TaskMetadata.builder()
                                        .taskId(tp.partition())
                                        .taskIdString(tp.topic() + "-" + tp.partition()) // e.g., "my-topic-1"
                                        .topicPartitions(List.of(tp.topic() + ":" + tp.partition()))
                                        .taskState(groupStateName) // Simplified: Task state = thread state
                                        .build())
                                .collect(Collectors.toList());

                        return ThreadMetadata.builder()
                                .threadName(member.consumerId()) // e.g., "my-app-id-StreamThread-1-consumer"
                                .threadState(groupStateName)
                                .consumerClientId(List.of(member.clientId()))
                                .tasks(tasks)
                                .build();
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("Could not get threads for streams app [{}]: {}", app.getApplicationId(), e.getMessage());
            connectionService.removeAdminClient(cluster.getId());
            return Collections.emptyList();
        }
    }

    /**
     * Helper to fetch the ConsumerGroupDescription for a given group ID.
     */
    private ConsumerGroupDescription getGroupDescription(AdminClient client, String groupId) throws ExecutionException, InterruptedException {
        Map<String, ConsumerGroupDescription> descriptions = client.describeConsumerGroups(Collections.singleton(groupId))
                .all().get();
        return descriptions.get(groupId);
    }

    /**
     * Maps the internal Kafka consumer group state to our application's
     * StreamsState enum.
     */
    private StreamsState mapKafkaStateToStreamsState(ConsumerGroupState state) {
        if (state == null) {
            return StreamsState.ERROR;
        }
        switch (state) {
            case STABLE:
                return StreamsState.RUNNING;
            case PREPARING_REBALANCE:
            case COMPLETING_REBALANCE:
                return StreamsState.REBALANCING;
            case EMPTY:
            case DEAD:
                return StreamsState.NOT_RUNNING;
            default:
                return StreamsState.ERROR;
        }
    }
}