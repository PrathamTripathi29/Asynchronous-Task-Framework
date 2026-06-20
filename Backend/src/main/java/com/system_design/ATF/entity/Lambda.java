package com.system_design.ATF.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.w3c.dom.Text;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lambdas")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lambda {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(nullable = false, unique = true)
    String name;

    @Column(columnDefinition = "TEXT")
    String description;

    @Column(name="callback_url", nullable = false, length=500)
    String callbackUrl;

    @Column(name="owner_team")
    String ownerTeam;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    LambdaStatus status = LambdaStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name="gate_action")
    GateAction gateAction;

    @Builder.Default
    @Column(name="enqueue_timeout_seconds", nullable = false)
    int enqueueTimeoutSeconds = 300;

    @Builder.Default
    @Column(name="claim_timeout_seconds", nullable = false)
    int claimTimeoutSeconds = 60;

    @Builder.Default
    @Column(name = "heartbeat_interval_seconds", nullable = false)
    int heartbeatIntervalSeconds = 10;

    @Builder.Default
    @Column(name = "heartbeat_timeout_seconds", nullable = false)
    int heartbeatTimeoutSeconds = 60;

    @Builder.Default
    @Column(name="max_attempts", nullable = false)
    int maxAttempts = 3;

    @Builder.Default
    @Column(name = "max_delivery_count", nullable = false)
    int maxDeliveryCount = 5;

    @CreationTimestamp
    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", updatable = false)
    OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    OffsetDateTime updatedAt;
}
