package com.system_design.ATF.repository;

import com.system_design.ATF.entity.Task;
import com.system_design.ATF.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    Page<Task> findByLambdaName(String lambdaName, Pageable pageable);
    Page<Task> findByLambdaNameAndStatus(String lambdaName, TaskStatus status, Pageable pageable);
}
