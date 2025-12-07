package io.brokr.api.rest.controller;

import io.brokr.api.input.KafkaStreamApplicationInput;
import io.brokr.api.service.KafkaStreamsApiService;
import io.brokr.core.dto.KafkaStreamsApplicationDto;
import io.brokr.core.exception.ResourceNotFoundException;
import io.brokr.core.model.KafkaStreamsApplication;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/brokr")
@RequiredArgsConstructor
public class KafkaStreamsController {

    private final KafkaStreamsApiService kafkaStreamsApiService;

    @GetMapping("/clusters/{clusterId}/kafka-streams")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<KafkaStreamsApplicationDto> getKafkaStreamsApplications(@PathVariable String clusterId) {
        return kafkaStreamsApiService.listKafkaStreamsApplications(clusterId).stream()
                .map(KafkaStreamsApplicationDto::fromDomain)
                .collect(Collectors.toList());
    }

    @GetMapping("/kafka-streams/{id}")
    @PreAuthorize("@authorizationService.hasAccessToKafkaStreamsApp(#id)")
    public KafkaStreamsApplicationDto getKafkaStreamsApplication(@PathVariable String id) {
        return KafkaStreamsApplicationDto.fromDomain(kafkaStreamsApiService.getKafkaStreamsApplication(id));
    }

    @PostMapping("/kafka-streams")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#input.clusterId)")
    public ResponseEntity<KafkaStreamsApplicationDto> createKafkaStreamsApplication(@RequestBody KafkaStreamApplicationInput input) {
        KafkaStreamsApplication saved = kafkaStreamsApiService.createKafkaStreamsApplication(input);
        return new ResponseEntity<>(KafkaStreamsApplicationDto.fromDomain(saved), HttpStatus.CREATED);
    }

    @PutMapping("/kafka-streams/{id}")
    @PreAuthorize("@authorizationService.hasAccessToKafkaStreamsApp(#id)")
    public KafkaStreamsApplicationDto updateKafkaStreamsApplication(@PathVariable String id, @RequestBody KafkaStreamApplicationInput input) {
        KafkaStreamsApplication updated = kafkaStreamsApiService.updateKafkaStreamsApplication(id, input);
        return KafkaStreamsApplicationDto.fromDomain(updated);
    }

    @DeleteMapping("/kafka-streams/{id}")
    @PreAuthorize("@authorizationService.hasAccessToKafkaStreamsApp(#id)")
    public ResponseEntity<Void> deleteKafkaStreamsApplication(@PathVariable String id) {
        if (!kafkaStreamsApiService.deleteKafkaStreamsApplication(id)) {
            throw new ResourceNotFoundException("Kafka Streams App not found with id: " + id);
        }
        return ResponseEntity.noContent().build();
    }
}