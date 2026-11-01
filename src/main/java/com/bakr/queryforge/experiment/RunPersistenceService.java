package com.bakr.queryforge.experiment;

import com.bakr.queryforge.entity.ExperimentRunEntity;
import com.bakr.queryforge.repo.ExperimentRunRepository;
import com.bakr.queryforge.service.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class RunPersistenceService {

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "id", "id",
            "templateId", "templateId",
            "state", "state",
            "startedAt", "startedAt",
            "durationMs", "durationMs");

    private final ExperimentRunRepository repo;

    public RunPersistenceService(ExperimentRunRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void save(ExperimentRun run) {
        ExperimentRunEntity entity = repo.findById(run.id())
                .orElseGet(() -> new ExperimentRunEntity(run.id()));
        repo.save(ExperimentRunMapper.updateEntity(run, entity));
    }

    @Transactional
    public long importLegacyRuns(List<ExperimentRun> runs) {
        long imported = 0;
        for (ExperimentRun run : runs) {
            if (repo.existsById(run.id())) {
                continue;
            }
            repo.save(ExperimentRunMapper.toEntity(run));
            imported++;
        }
        return imported;
    }

    @Transactional(readOnly = true)
    public PageResponse<ExperimentRun> findPage(
            String state, String templateId, Instant from, Instant toExclusive,
            int page, int size, String sortBy, String sortDirection) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String sortColumn = SORT_COLUMNS.get(sortBy);
        if (sortColumn == null) {
            throw new IllegalArgumentException("unsupported run sort column: " + sortBy);
        }
        Sort.Direction direction = Sort.Direction.fromString(sortDirection);

        Specification<ExperimentRunEntity> filters = (root, query, builder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (state != null && !state.isBlank()) {
                predicates.add(builder.equal(
                        builder.upper(root.get("state")),
                        state.strip().toUpperCase(Locale.ROOT)));
            }
            if (templateId != null && !templateId.isBlank()) {
                predicates.add(builder.equal(root.get("templateId"), templateId.strip()));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("startedAt"), from));
            }
            if (toExclusive != null) {
                predicates.add(builder.lessThan(root.get("startedAt"), toExclusive));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };

        var result = repo.findAll(filters, PageRequest.of(
                safePage, safeSize, Sort.by(direction, sortColumn)));
        List<ExperimentRun> content = result.getContent().stream()
                .map(ExperimentRunMapper::toRecord)
                .toList();
        return PageResponse.of(content, safePage, safeSize, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Optional<ExperimentRun> findById(String id) {
        return repo.findById(id).map(ExperimentRunMapper::toRecord);
    }

    @Transactional(readOnly = true)
    public boolean hasAny() {
        return repo.count() > 0;
    }
}
