package com.conflux.report;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

	boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);

	Page<Report> findByStatus(ReportStatus status, Pageable pageable);

	@EntityGraph(attributePaths = { "reporter", "reviewedBy" })
	Optional<Report> findWithPeopleById(Long id);

}
