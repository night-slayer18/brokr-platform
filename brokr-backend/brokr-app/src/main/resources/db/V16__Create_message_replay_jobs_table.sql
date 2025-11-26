-- =====================================================
-- Message Replay Jobs Table
-- Stores replay job definitions, status, and progress
-- Includes scheduling and retry support
-- =====================================================
CREATE TABLE message_replay_jobs (
    id VARCHAR(255) PRIMARY KEY,
    cluster_id VARCHAR(255) NOT NULL,
    source_topic VARCHAR(255) NOT NULL,
    
    -- Target configuration
    target_topic VARCHAR(255),  -- NULL for offset reset only
    consumer_group_id VARCHAR(255),  -- NULL for reprocessing to topic
    
    -- Starting point (either offset or timestamp, not both)
    start_offset BIGINT,  -- NULL if using timestamp
    start_timestamp TIMESTAMP,  -- NULL if using offset
    
    -- Ending point (optional, for range replay)
    end_offset BIGINT,
    end_timestamp TIMESTAMP,
    
    -- Partition selection (JSON array of partition IDs, NULL = all partitions)
    partitions JSONB,
    
    -- Filter criteria (JSON object)
    filters JSONB,
    
    -- Transformation rules (JSON object, optional)
    transformation JSONB,
    
    -- Job status and progress
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',  -- PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    progress JSONB,  -- Current progress: {messagesProcessed, messagesTotal, throughput, estimatedTimeRemaining}
    
    -- Scheduling fields
    schedule_cron VARCHAR(100),  -- Cron expression for recurring jobs
    schedule_timezone VARCHAR(50) DEFAULT 'UTC',  -- Timezone for cron
    next_scheduled_run TIMESTAMP,  -- Next scheduled execution time
    is_recurring BOOLEAN DEFAULT false,  -- Whether this is a recurring job
    last_scheduled_run TIMESTAMP,  -- Last scheduled execution time
    
    -- Retry fields
    retry_count INT DEFAULT 0,  -- Current retry attempt
    max_retries INT DEFAULT 0,  -- Maximum retry attempts (0 = no retry)
    retry_delay_seconds INT DEFAULT 60,  -- Delay between retries in seconds
    
    -- Metadata
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    metadata JSONB,  -- Additional job metadata
    
    FOREIGN KEY (cluster_id) REFERENCES kafka_clusters(id) ON DELETE CASCADE
);

-- Indexes for efficient queries
CREATE INDEX idx_replay_jobs_cluster_status_created ON message_replay_jobs(cluster_id, status, created_at DESC);
CREATE INDEX idx_replay_jobs_created_by_created ON message_replay_jobs(created_by, created_at DESC);
CREATE INDEX idx_replay_jobs_source_topic_created ON message_replay_jobs(source_topic, created_at DESC);
CREATE INDEX idx_replay_jobs_status ON message_replay_jobs(status) WHERE status IN ('PENDING', 'RUNNING');

-- Index for scheduled jobs
CREATE INDEX idx_replay_jobs_scheduled ON message_replay_jobs(next_scheduled_run) 
    WHERE next_scheduled_run IS NOT NULL AND status = 'PENDING';

-- Index for failed jobs that need retry
CREATE INDEX idx_replay_jobs_retry ON message_replay_jobs(status, retry_count, max_retries, completed_at) 
    WHERE status = 'FAILED' AND retry_count < max_retries;

-- Index for finding jobs by source topic (for conflict detection)
CREATE INDEX IF NOT EXISTS idx_replay_source_topic ON message_replay_jobs(source_topic);

-- Index for finding jobs by target topic
CREATE INDEX IF NOT EXISTS idx_replay_target_topic ON message_replay_jobs(target_topic);

-- Partial index for running replay jobs only
CREATE INDEX IF NOT EXISTS idx_replay_running_only ON message_replay_jobs(started_at) 
    WHERE status = 'RUNNING';

-- Partial index for pending replay jobs (for scheduler)
CREATE INDEX IF NOT EXISTS idx_replay_pending_scheduled ON message_replay_jobs(next_scheduled_run) 
    WHERE status = 'PENDING' AND next_scheduled_run IS NOT NULL;

-- =====================================================
-- Replay Job History Table (Optional - for detailed audit trail)
-- Tracks progress snapshots for long-running replays
-- =====================================================
CREATE TABLE message_replay_job_history (
    id VARCHAR(255) PRIMARY KEY,
    replay_job_id VARCHAR(255) NOT NULL,
    
    -- Action type
    action VARCHAR(50) NOT NULL,  -- ACTION_STARTED, MESSAGE_PROCESSED, ACTION_COMPLETED, ACTION_FAILED
    
    -- Progress metrics at this point
    message_count BIGINT DEFAULT 0,
    throughput DECIMAL(10, 2),  -- Messages per second
    
    -- Timestamp
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Additional details (JSON object)
    details JSONB,
    
    FOREIGN KEY (replay_job_id) REFERENCES message_replay_jobs(id) ON DELETE CASCADE
);

-- Indexes for history queries
CREATE INDEX idx_replay_job_history_job_timestamp ON message_replay_job_history(replay_job_id, timestamp DESC);
CREATE INDEX idx_replay_job_history_timestamp ON message_replay_job_history(timestamp DESC);

