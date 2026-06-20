package com.system_design.ATF.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "lambda_name", nullable = false)
    String lambdaName;

    @Column(name = "collection_name")
    String collectionName;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    Priority priority = Priority.MEDIUM;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    JsonNode payload;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    TaskStatus status = TaskStatus.NEW;

    @Column(name = "next_trigger_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false)
    OffsetDateTime nextTriggerAt;

    @Column(name = "scheduled_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false)
    OffsetDateTime scheduledAt;

    @Column(name = "attempt_count", nullable = false)
    int attemptCount;

    @Column(name = "claimed_by")
    String claimedBy;

    @Column(name = "last_heart_beat", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    OffsetDateTime lastHeartBeat;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    JsonNode result;

    @Column(name = "error_message", columnDefinition = "TEXT")
    String errorMessage;

    @Column(name = "http_status_code")
    Integer httpStatusCode;

    @CreationTimestamp
    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", updatable = false)
    OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    OffsetDateTime updatedAt;
}
