package com.bakr.queryforge.repo;

import com.bakr.queryforge.entity.ExperimentRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExperimentRunRepository extends JpaRepository<ExperimentRunEntity, String> {
    List<ExperimentRunEntity> findByTemplateId(String templateId);
    List<ExperimentRunEntity> findByState(String state);
}
