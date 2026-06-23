package com.system_design.ATF.repository;

import com.system_design.ATF.entity.Lambda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LambdaRepository extends JpaRepository<Lambda, UUID> {
    Optional<Lambda> findByName(String name);
    boolean existsByName(String name);
}
