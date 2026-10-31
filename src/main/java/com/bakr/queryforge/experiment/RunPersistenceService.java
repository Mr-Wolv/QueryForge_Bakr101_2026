package com.bakr.queryforge.experiment;

import com.bakr.queryforge.repo.ExperimentRunRepository;
import com.bakr.queryforge.service.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RunPersistenceService {

    private final ExperimentRunRepository repo;

    public RunPersistenceService(ExperimentRunRepository repo) {
        this.repo = repo;
    }

    public void save(ExperimentRun run) {
        repo.save(ExperimentRunMapper.toEntity(run));
    }

    public PageResponse<ExperimentRun> findPage(String state, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        long total;
        List<com.bakr.queryforge.entity.ExperimentRunEntity> slice;
        if (state == null || state.isBlank()) {
            var p = repo.findAll(PageRequest.of(safePage, safeSize,
                    Sort.by(Sort.Direction.DESC, "startedAt")));
            total = p.getTotalElements();
            slice = p.getContent();
        } else {
            var all = repo.findByState(state);
            total = all.size();
            int from = Math.min(safePage * safeSize, all.size());
            int to = Math.min(from + safeSize, all.size());
            slice = all.subList(from, to);
        }
        List<ExperimentRun> content = slice.stream().map(ExperimentRunMapper::toRecord).toList();
        return PageResponse.of(content, safePage, safeSize, total);
    }

    public boolean hasAny() {
        try {
            return repo.count() > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
