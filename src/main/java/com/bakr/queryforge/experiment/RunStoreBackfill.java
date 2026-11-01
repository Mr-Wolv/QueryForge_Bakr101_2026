package com.bakr.queryforge.experiment;

import com.bakr.queryforge.artifact.ManifestStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class RunStoreBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RunStoreBackfill.class);

    private final ManifestStore manifests;
    private final RunPersistenceService persistence;

    public RunStoreBackfill(ManifestStore manifests, RunPersistenceService persistence) {
        this.manifests = manifests;
        this.persistence = persistence;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        var records = manifests.readAllStrict().stream()
                .map(ExperimentRunMapper::fromManifest)
                .toList();
        long imported = persistence.importLegacyRuns(records);
        if (imported > 0) {
            log.info("Imported {} legacy run manifests into PostgreSQL", imported);
        }
    }
}
