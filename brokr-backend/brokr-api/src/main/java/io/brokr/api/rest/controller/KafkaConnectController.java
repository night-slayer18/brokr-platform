package io.brokr.api.rest.controller;

import io.brokr.api.input.KafkaConnectInput;
import io.brokr.api.service.KafkaConnectApiService;
import io.brokr.core.dto.KafkaConnectDto;
import io.brokr.core.model.KafkaConnect;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class KafkaConnectController {

    private final KafkaConnectApiService kafkaConnectApiService;

    @GetMapping("/clusters/{clusterId}/kafka-connects")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#clusterId)")
    public List<KafkaConnectDto> getKafkaConnects(@PathVariable String clusterId) {
        return kafkaConnectApiService.listKafkaConnects(clusterId).stream()
                .map(KafkaConnectDto::fromDomain)
                .collect(Collectors.toList());
    }

    @GetMapping("/kafka-connects/{id}")
    @PreAuthorize("@authorizationService.hasAccessToKafkaConnect(#id)")
    public KafkaConnectDto getKafkaConnect(@PathVariable String id) {
        return KafkaConnectDto.fromDomain(kafkaConnectApiService.getKafkaConnectById(id));
    }

    @PostMapping("/kafka-connects")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#input.clusterId)")
    public ResponseEntity<KafkaConnectDto> createKafkaConnect(@RequestBody KafkaConnectInput input) {
        KafkaConnect saved = kafkaConnectApiService.createKafkaConnect(input);
        return new ResponseEntity<>(KafkaConnectDto.fromDomain(saved), HttpStatus.CREATED);
    }

    @PutMapping("/kafka-connects/{id}")
    @PreAuthorize("@authorizationService.hasAccessToKafkaConnect(#id)")
    public KafkaConnectDto updateKafkaConnect(@PathVariable String id, @RequestBody KafkaConnectInput input) {
        KafkaConnect updated = kafkaConnectApiService.updateKafkaConnect(id, input);
        return KafkaConnectDto.fromDomain(updated);
    }

    @DeleteMapping("/kafka-connects/{id}")
    @PreAuthorize("@authorizationService.hasAccessToKafkaConnect(#id)")
    public ResponseEntity<Void> deleteKafkaConnect(@PathVariable String id) {
        kafkaConnectApiService.deleteKafkaConnect(id);
        return ResponseEntity.noContent().build();
    }
}