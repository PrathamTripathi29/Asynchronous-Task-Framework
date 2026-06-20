package com.system_design.ATF.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "collections", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"lambda_name", "name"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Collection {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "lambda_name", nullable = false)
    String lambdaName;

    @Column(nullable = false)
    String name;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    LambdaStatus status = LambdaStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "gate_action")
    GateAction gateAction;
}
