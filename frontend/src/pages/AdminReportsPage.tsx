import { useCallback } from 'react'
import { Outlet, useParams, useSearchParams } from 'react-router'
import { adminApi } from '../features/admin/adminApi'
import type { AdminOutletContext } from '../features/admin/adminOutlet'
import { ReportQueue } from '../features/admin/ReportQueue'
import { readReportsQuery, toReportsApiSearch } from '../features/admin/reportQuery'
import { parseId } from '../lib/ids'
import { useAsync } from '../lib/useAsync'

export function AdminReportsPage() {
  const params = useParams()
  const [searchParams] = useSearchParams()
  const apiSearch = toReportsApiSearch(readReportsQuery(searchParams))
  const load = useCallback((signal: AbortSignal) => adminApi.reports(apiSearch, signal), [apiSearch])
  const result = useAsync(load)
  const selectedId = parseId(params.id)
  const showsReport = params.id !== undefined

  const outletContext: AdminOutletContext = { reloadQueue: result.retry, queueSearch: searchParams.toString() }

  return (
    <div className="admin-page">
      <title>Moderation · Conflux</title>
      <header className="admin-header">
        <p className="eyebrow admin-eyebrow">
          <span className="admin-eyebrow-mark" aria-hidden="true" />
          Admin · Trust &amp; safety
        </p>
        <h1 className="page-title">Moderation</h1>
        <p className="lead">Review reports from members, then resolve or dismiss them. Suspend or restore what they’re about.</p>
      </header>
      <div className="admin-workspace" data-pane={showsReport ? 'report' : 'queue'}>
        <ReportQueue result={result} selectedId={selectedId} />
        <div className="admin-detail">
          <Outlet context={outletContext} />
        </div>
      </div>
    </div>
  )
}

export function AdminReportsIndex() {
  return (
    <div className="report-panel report-placeholder">
      <span className="empty-state-mark" aria-hidden="true" />
      <p className="report-placeholder-title">Select a report</p>
      <p>Choose a report from the queue to see the full details and take action.</p>
    </div>
  )
}
