import { useEffect, useRef } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router'
import { paths } from '../../app/paths'
import { Button } from '../../components/Button'
import { EmptyState } from '../../components/EmptyState'
import { ErrorMessage } from '../../components/ErrorMessage'
import { SelectField } from '../../components/SelectField'
import { formatDateTime } from '../../lib/dates'
import type { AsyncState } from '../../lib/useAsync'
import type { PageResponse } from '../../types/api'
import { useAuth } from '../auth/useAuth'
import { Pagination } from '../listings/Pagination'
import { adminErrorKind, adminLoadError } from './adminErrors'
import { AdminStatusBadge } from './AdminStatusBadge'
import { REPORT_STATUS_LABELS, reasonLabel, reportStatusLabel, targetTypeLabel } from './labels'
import { readReportsQuery, withReportsQuery, type ReportsQueryChanges } from './reportQuery'
import { REPORT_PAGE_SIZES, REPORT_STATUSES, type ReportSummary } from './types'

const numberFormat = new Intl.NumberFormat('en-US')

const PAGE_SIZE_OPTIONS = REPORT_PAGE_SIZES.map((size) => ({ value: String(size), label: `${size} per page` }))

function countSummary(page: PageResponse<ReportSummary>): string {
  const total = page.totalElements
  const noun = total === 1 ? 'report' : 'reports'
  if (page.content.length === 0 || page.content.length === total) {
    return `${numberFormat.format(total)} ${noun}`
  }
  const from = page.page * page.size + 1
  const to = from + page.content.length - 1
  return `Showing ${numberFormat.format(from)}–${numberFormat.format(to)} of ${numberFormat.format(total)} ${noun}`
}

interface ReportQueueProps {
  result: AsyncState<PageResponse<ReportSummary>> & { retry: () => void }
  selectedId: number | null
}

export function ReportQueue({ result, selectedId }: ReportQueueProps) {
  const [params] = useSearchParams()
  const location = useLocation()
  const navigate = useNavigate()
  const { refreshAccount } = useAuth()
  const query = readReportsQuery(params)
  const headingRef = useRef<HTMLHeadingElement>(null)
  const knownStatus = (REPORT_STATUSES as readonly string[]).includes(query.status)
  const errorKind = result.status === 'error' ? adminErrorKind(result.error) : null

  useEffect(() => {
    if (errorKind === 'forbidden') {
      void refreshAccount()
    }
  }, [errorKind, refreshAccount])

  const hrefWith = (changes: ReportsQueryChanges, pathname = location.pathname) => {
    const search = withReportsQuery(params, changes)
    return search ? `${pathname}?${search}` : pathname
  }
  const focusResults = () => headingRef.current?.focus()

  const page = result.data ?? result.previousData
  const loading = result.status === 'loading'
  const search = params.toString()
  const showOpen = (
    <Link to={hrefWith({ status: 'OPEN' }, paths.adminReports)} className="button button-secondary">
      Show open reports
    </Link>
  )

  let body
  if (result.status === 'error') {
    const error = adminLoadError(result.error, 'queue')
    body = (
      <ErrorMessage title={error.title} message={error.message}>
        {(error.retryable || error.kind === 'invalid') && (
          <div className="button-row">
            {error.retryable ? (
              <Button variant="secondary" onClick={result.retry}>
                Try again
              </Button>
            ) : (
              <Link to={paths.adminReports} className="button button-secondary">
                Show open reports
              </Link>
            )}
          </div>
        )}
      </ErrorMessage>
    )
  } else if (page === undefined) {
    body = <ReportQueueSkeleton />
  } else if (page.totalElements === 0) {
    body =
      query.status === 'OPEN' ? (
        <EmptyState title="Queue clear">There are no open reports. New reports from members will appear here.</EmptyState>
      ) : (
        <EmptyState title={`No ${reportStatusLabel(query.status).toLowerCase()} reports`} action={showOpen}>
          No reports have this status yet.
        </EmptyState>
      )
  } else if (page.content.length === 0) {
    const lastPage = Math.max(page.totalPages, 1)
    body = (
      <EmptyState
        title={`There is no page ${query.page}`}
        action={
          <Link to={hrefWith({ page: lastPage })} className="button button-primary" onClick={focusResults}>
            Go to page {lastPage}
          </Link>
        }
      >
        These reports end on page {lastPage}.
      </EmptyState>
    )
  } else {
    body = (
      <>
        <ul className={loading ? 'report-queue-list results-body is-stale' : 'report-queue-list results-body'}>
          {page.content.map((report) => (
            <li key={report.id}>
              <ReportQueueItem report={report} selected={report.id === selectedId} search={search} />
            </li>
          ))}
        </ul>
        {page.totalPages > 1 && (
          <Pagination
            page={query.page}
            totalPages={page.totalPages}
            hrefFor={(target) => hrefWith({ page: target })}
            onNavigate={focusResults}
          />
        )}
      </>
    )
  }

  return (
    <section className="report-queue" aria-labelledby="report-queue-heading" aria-busy={loading}>
      <nav className="status-filters" aria-label="Filter reports by status">
        <ul>
          {REPORT_STATUSES.map((status) => (
            <li key={status}>
              <Link
                to={hrefWith({ status })}
                className="status-filter"
                data-status={status.toLowerCase()}
                aria-current={status === query.status ? 'true' : undefined}
              >
                {REPORT_STATUS_LABELS[status]}
              </Link>
            </li>
          ))}
        </ul>
      </nav>

      <div className="results-toolbar report-queue-toolbar">
        <div className="results-heading-group">
          <h2 id="report-queue-heading" ref={headingRef} tabIndex={-1} className="results-heading">
            {knownStatus ? `${reportStatusLabel(query.status)} reports` : 'Reports'}
          </h2>
          <p className="results-count" role="status">
            {loading ? 'Loading reports…' : result.status === 'success' ? `${countSummary(result.data)} · Newest first` : ''}
          </p>
        </div>
        <SelectField
          label="Page size"
          className="field-inline report-page-size"
          value={String(query.size)}
          options={PAGE_SIZE_OPTIONS}
          onChange={(value) => void navigate(hrefWith({ size: Number(value) }))}
        />
      </div>

      {body}
    </section>
  )
}

function ReportQueueItem({ report, selected, search }: { report: ReportSummary; selected: boolean; search: string }) {
  const target = `${targetTypeLabel(report.targetType)} #${report.targetId}`
  return (
    <Link
      to={{ pathname: paths.adminReport(report.id), search }}
      state={{ focusDetail: true }}
      className="report-row"
      data-status={report.status.toLowerCase()}
      aria-current={selected ? 'true' : undefined}
      aria-label={`Report #${report.id}, ${reasonLabel(report.reason)}, ${target}, ${reportStatusLabel(report.status)}`}
    >
      <span className="report-row-top">
        <span className="report-row-id">#{report.id}</span>
        <AdminStatusBadge kind="report" status={report.status} />
      </span>
      <span className="report-row-reason">{reasonLabel(report.reason)}</span>
      <span className="report-row-target">
        <span className="report-row-type">{targetTypeLabel(report.targetType)}</span>
        <span className="report-row-target-id">#{report.targetId}</span>
      </span>
      <span className="report-row-dates">
        Reported <time dateTime={report.createdAt}>{formatDateTime(report.createdAt)}</time>
        {report.reviewedAt && (
          <>
            {' · Reviewed '}
            <time dateTime={report.reviewedAt}>{formatDateTime(report.reviewedAt)}</time>
          </>
        )}
      </span>
    </Link>
  )
}

function ReportQueueSkeleton() {
  return (
    <div className="report-queue-list" aria-hidden="true">
      {Array.from({ length: 4 }, (_, index) => (
        <div key={index} className="report-row report-row-skeleton">
          <span className="skeleton skeleton-tag" />
          <span className="skeleton skeleton-title" />
          <span className="skeleton skeleton-line skeleton-line-short" />
        </div>
      ))}
    </div>
  )
}
