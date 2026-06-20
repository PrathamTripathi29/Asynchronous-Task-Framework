package com.system_design.ATF.repository;

import com.system_design.ATF.entity.Lambda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LambdaRepository extends JpaRepository<Lambda, UUID> {
}
