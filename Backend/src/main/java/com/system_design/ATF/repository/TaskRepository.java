package com.system_design.ATF.repository;

import com.system_design.ATF.entity.Task;
import com.system_design.ATF.entity.TaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    Page<Task> findByLambdaName(String lambdaName, Pageable pageable);
    Page<Task> findByLambdaNameAndStatus(String lambdaName, TaskStatus status, Pageable pageable);

    @Query(nativeQuery = true, value = """
        SELECT * FROM tasks 
        WHERE status NOT IN ('SUCCESS', 'FATAL_FAILURE')
            AND next_trigger_at <= :now
        ORDER BY next_trigger_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED 
        """)
    List<Task> findDueTasks(@Param("now")OffsetDateTime now, @Param("batchSize") int batchSize);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Task t WHERE t.id = :id")
    Optional<Task> findByIdForUpdate(@Param("id") UUID id);

}
