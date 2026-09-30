package com.conflux.report;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@link Report}.
 */
public interface ReportRepository extends JpaRepository<Report, Long> {

	boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);

	/**
	 * The admin queue for one status (paged in the database; no associations needed).
	 */
	Page<Report> findByStatus(ReportStatus status, Pageable pageable);

	/**
	 * A report with its reporter and reviewer (for the admin detail view).
	 */
	@EntityGraph(attributePaths = { "reporter", "reviewedBy" })
	Optional<Report> findWithPeopleById(Long id);

}
