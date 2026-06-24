package com.system_design.ATF.repository;

import com.system_design.ATF.entity.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollectionRepository extends JpaRepository<Collection, UUID> {
    //lambda name, collection name
    Optional<Collection> findByLambdaNameAndName(String lambdaName, String name);
}
