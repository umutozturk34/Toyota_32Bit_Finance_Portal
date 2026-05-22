package com.finance.app.analytics.repository;

import com.finance.app.analytics.model.BeaterSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface BeaterSnapshotRepository extends JpaRepository<BeaterSnapshot, Long> {

    Optional<BeaterSnapshot> findBySnapshotDateAndPeriodAndBenchmarkCode(
            LocalDate snapshotDate, String period, String benchmarkCode);

    void deleteBySnapshotDateBefore(LocalDate cutoff);
}
