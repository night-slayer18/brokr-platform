package io.brokr.api.rest.controller;

import io.brokr.api.input.MessageReplayInput;
import io.brokr.api.service.MessageReplayApiService;
import io.brokr.core.model.MessageReplayJob;
import io.brokr.core.model.ReplayJobStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for message replay/reprocessing operations.
 * Thin wrapper around MessageReplayApiService - no service changes needed.
 */
@RestController
@RequestMapping("/api/v1/replay")
@RequiredArgsConstructor
public class ReplayController {
    
    private final MessageReplayApiService replayApiService;
    
    @GetMapping("/jobs")
    @PreAuthorize("#clusterId == null or @authorizationService.hasAccessToCluster(#clusterId)")
    public List<MessageReplayJob> getReplayJobs(
            @RequestParam(required = false) String clusterId,
            @RequestParam(required = false) ReplayJobStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return replayApiService.listReplayJobs(clusterId, status, page, size);
    }
    
    @GetMapping("/jobs/{id}")
    public MessageReplayJob getReplayJob(@PathVariable String id) {
        // Authorization is checked in service layer
        return replayApiService.getReplayJob(id);
    }
    
    @GetMapping("/jobs/{id}/history")
    public List<io.brokr.storage.entity.MessageReplayJobHistoryEntity> getReplayHistory(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Authorization is checked in service layer
        return replayApiService.getReplayHistory(id, page, size);
    }
    
    @PostMapping("/jobs")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#input.clusterId)")
    public ResponseEntity<MessageReplayJob> replayMessages(@RequestBody MessageReplayInput input) {
        MessageReplayJob job = replayApiService.replayMessages(input);
        return new ResponseEntity<>(job, HttpStatus.CREATED);
    }
    
    @PostMapping("/jobs/schedule")
    @PreAuthorize("@authorizationService.hasAccessToCluster(#input.clusterId)")
    public ResponseEntity<MessageReplayJob> scheduleReplay(@RequestBody MessageReplayInput input) {
        MessageReplayJob job = replayApiService.scheduleReplay(input);
        return new ResponseEntity<>(job, HttpStatus.CREATED);
    }
    
    @PostMapping("/jobs/{id}/cancel")
    public ResponseEntity<Void> cancelReplay(@PathVariable String id) {
        // Authorization is checked in service layer
        replayApiService.cancelReplay(id);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/jobs/{id}/retry")
    public ResponseEntity<Void> retryReplay(@PathVariable String id) {
        // Authorization is checked in service layer
        replayApiService.retryReplay(id);
        return ResponseEntity.noContent().build();
    }
    
    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<Void> deleteReplay(@PathVariable String id) {
        // Authorization is checked in service layer
        replayApiService.deleteReplay(id);
        return ResponseEntity.noContent().build();
    }
}

