package com.bakr.queryforge.repo;

import com.bakr.queryforge.entity.ExperimentRunEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ExperimentRunRepository extends JpaRepository<ExperimentRunEntity, String>,
        JpaSpecificationExecutor<ExperimentRunEntity> {
}
