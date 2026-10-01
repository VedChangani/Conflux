import { useCallback, useEffect, useId, useRef, useState, type MouseEvent, type ReactNode } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { paths } from '../../app/paths'
import { Button } from '../../components/Button'
import { ErrorMessage } from '../../components/ErrorMessage'
import { formatDateTime } from '../../lib/dates'
import { parseId } from '../../lib/ids'
import { useAsync } from '../../lib/useAsync'
import { useAuth } from '../auth/useAuth'
import { adminApi } from './adminApi'
import { adminErrorKind, adminLoadError } from './adminErrors'
import { useAdminOutlet } from './adminOutlet'
import { AdminStatusBadge } from './AdminStatusBadge'
import { reasonLabel, targetTypeLabel } from './labels'
import { ModerationDialog } from './ModerationDialog'
import { ListingTargetView, MessageTargetView, PersonSummary, UserTargetView } from './ReportTarget'
import type { AdminAction, ReportDecision, ReportDetail, TargetAction } from './types'

const DONE: Record<AdminAction, string> = {
  resolve: 'Report resolved.',
  dismiss: 'Report dismissed.',
  suspendUser: 'Account suspended.',
  restoreUser: 'Account restored.',
  suspendListing: 'Listing suspended.',
  restoreListing: 'Listing restored.',
}

const TARGET_NOUN: Record<ReportDetail['targetType'], string> = { USER: 'user', LISTING: 'listing', MESSAGE: 'message' }

/** `/admin/reports/:id`: one report, keyed so switching reports starts afresh. */
export function ReportDetailRoute() {
  const { id: rawId } = useParams()
  const id = parseId(rawId)
  if (id === null) {
    return <ReportNotFound />
  }
  return <ReportDetailPanel key={id} id={id} />
}

/**
 * One report with everything `ReportDetailResponse` holds: the report, its reporter and
 * reviewer, and the current state of the reported user, listing or message. An OPEN report can
 * be resolved or dismissed; a user or listing can be suspended or restored. After an action the
 * panel shows what the backend returned (decisions) or reads the report again (suspend and
 * restore answer 204), and the queue reloads; the rest of the app is untouched.
 */
function ReportDetailPanel({ id }: { id: number }) {
  const { reloadQueue, queueSearch } = useAdminOutlet()
  const { account, refreshAccount } = useAuth()
  const location = useLocation()
  const load = useCallback((signal: AbortSignal) => adminApi.report(id, signal), [id])
  const result = useAsync(load)

  // The report as last known from the backend: a load, or the response to a decision.
  const [report, setReport] = useState<ReportDetail | null>(null)
  const [loaded, setLoaded] = useState<ReportDetail | undefined>(undefined)
  if (result.data !== loaded) {
    setLoaded(result.data)
    if (result.data !== undefined) {
      setReport(result.data)
    }
  }

  const [pending, setPending] = useState<AdminAction | null>(null)
  const [announcement, setAnnouncement] = useState('')
  const triggerRef = useRef<HTMLElement | null>(null)
  const headingRef = useRef<HTMLHeadingElement>(null)
  const outcomeRef = useRef<HTMLParagraphElement>(null)
  const focusOutcome = useRef(false)
  // Opened from the queue: move focus here once loaded (on small screens the queue is hidden).
  const focusOnLoad = useRef(Boolean((location.state as { focusDetail?: unknown } | null)?.focusDetail))

  const errorKind = result.status === 'error' ? adminErrorKind(result.error) : null

  useEffect(() => {
    if (report && focusOnLoad.current) {
      focusOnLoad.current = false
      headingRef.current?.focus()
    }
  }, [report])

  useEffect(() => {
    if (pending === null && focusOutcome.current) {
      focusOutcome.current = false
      outcomeRef.current?.focus()
    }
  }, [pending])

  // A 403 means this account is no longer an active admin: re-read it so the admin area closes.
  useEffect(() => {
    if (errorKind === 'forbidden') {
      void refreshAccount()
    }
  }, [errorKind, refreshAccount])

  const back = queueSearch ? `${paths.adminReports}?${queueSearch}` : paths.adminReports

  if (errorKind === 'notFound' || errorKind === 'invalid') {
    return <ReportNotFound back={back} />
  }

  if (report === null) {
    if (result.status === 'error') {
      const error = adminLoadError(result.error, 'report')
      return (
        <div className="report-panel">
          <BackLink to={back} />
          <ErrorMessage title={error.title} message={error.message}>
            {error.retryable && (
              <div className="button-row">
                <Button variant="secondary" onClick={result.retry}>
                  Try again
                </Button>
              </div>
            )}
          </ErrorMessage>
        </div>
      )
    }
    return <ReportDetailSkeleton back={back} />
  }

  function open(action: AdminAction) {
    return (event: MouseEvent<HTMLButtonElement>) => {
      triggerRef.current = event.currentTarget
      setAnnouncement('')
      setPending(action)
    }
  }

  function decide(decision: ReportDecision) {
    return async (note: string) => {
      const request = { resolutionNote: note.trim() || null }
      const updated = await (decision === 'resolve' ? adminApi.resolve(id, request) : adminApi.dismiss(id, request))
      setReport(updated)
      reloadQueue()
    }
  }

  function moderate(action: TargetAction, targetId: number) {
    return async () => {
      await adminApi.moderate(action, targetId)
      // 204 either way (also when nothing changed): read the report again for the real state.
      result.retry()
    }
  }

  function handleDone() {
    if (pending) {
      setAnnouncement(DONE[pending])
    }
    focusOutcome.current = true
    setPending(null)
  }

  function handleStale() {
    result.retry()
    reloadQueue()
  }

  const refreshing = result.status === 'loading'
  const refreshError = result.status === 'error' ? adminLoadError(result.error, 'report') : null

  return (
    <article
      className="report-panel report-detail"
      data-status={report.status.toLowerCase()}
      aria-labelledby={`report-${report.id}-heading`}
      aria-busy={refreshing}
    >
      <title>{`Report #${report.id} · Moderation · Conflux`}</title>
      <BackLink to={back} />

      <header className="report-detail-header">
        <p className="eyebrow">
          {targetTypeLabel(report.targetType)} report · <span className="admin-mono">#{report.id}</span>
        </p>
        <h2 id={`report-${report.id}-heading`} ref={headingRef} tabIndex={-1} className="report-detail-title">
          {reasonLabel(report.reason)}
        </h2>
        <div className="report-detail-meta">
          <AdminStatusBadge kind="report" status={report.status} />
          <span className="admin-muted">
            Reported <time dateTime={report.createdAt}>{formatDateTime(report.createdAt)}</time>
          </span>
        </div>
      </header>

      <p ref={outcomeRef} tabIndex={-1} className="report-outcome" role="status">
        {announcement || (refreshing ? 'Refreshing…' : '')}
      </p>
      {refreshError && (
        <ErrorMessage title="We couldn’t refresh this report" message={refreshError.message}>
          {refreshError.retryable && (
            <div className="button-row">
              <Button variant="secondary" className="button-small" onClick={result.retry}>
                Try again
              </Button>
            </div>
          )}
        </ErrorMessage>
      )}

      <Section title="Report">
        <dl className="admin-facts">
          <div>
            <dt>Reason</dt>
            <dd>{reasonLabel(report.reason)}</dd>
          </div>
          <div>
            <dt>Target</dt>
            <dd>
              {targetTypeLabel(report.targetType)} <span className="admin-mono">#{report.targetId}</span>
            </dd>
          </div>
          <div>
            <dt>Reporter</dt>
            <dd>
              <PersonSummary person={report.reporter} />
            </dd>
          </div>
        </dl>
        <div className="report-details">
          <p className="eyebrow">Reporter’s details</p>
          {report.details ? <p className="prose">{report.details}</p> : <p className="admin-muted">No details given.</p>}
        </div>
      </Section>

      <Section title={`Reported ${TARGET_NOUN[report.targetType]}`}>
        <TargetView report={report} accountId={account?.id ?? null} />
        <TargetActions report={report} busy={pending !== null} open={open} />
      </Section>

      <Section title="Review">
        {report.status === 'OPEN' ? (
          <div className="review-decision">
            <p className="admin-muted">
              Resolve when the report was valid and has been dealt with; dismiss when no action is needed. Both are
              final.
            </p>
            <div className="button-row">
              <Button className="button-small" disabled={pending !== null} aria-haspopup="dialog" onClick={open('resolve')}>
                Resolve
              </Button>
              <Button
                variant="secondary"
                className="button-small"
                disabled={pending !== null}
                aria-haspopup="dialog"
                onClick={open('dismiss')}
              >
                Dismiss
              </Button>
            </div>
          </div>
        ) : (
          <dl className="admin-facts">
            <div>
              <dt>Outcome</dt>
              <dd>
                <AdminStatusBadge kind="report" status={report.status} />
              </dd>
            </div>
            <div>
              <dt>Reviewed by</dt>
              <dd>
                <PersonSummary
                  person={report.reviewer}
                  isYou={report.reviewer !== null && report.reviewer.id === account?.id}
                />
              </dd>
            </div>
            {report.reviewedAt && (
              <div>
                <dt>Reviewed</dt>
                <dd>
                  <time dateTime={report.reviewedAt}>{formatDateTime(report.reviewedAt)}</time>
                </dd>
              </div>
            )}
            <div>
              <dt>Note</dt>
              <dd>{report.resolutionNote ? <span className="prose">{report.resolutionNote}</span> : <span className="admin-muted">No note.</span>}</dd>
            </div>
          </dl>
        )}
      </Section>

      {pending && (
        <ModerationDialog
          action={pending}
          {...dialogCopy(pending, report)}
          onConfirm={
            pending === 'resolve' || pending === 'dismiss' ? decide(pending) : moderate(pending, report.targetId)
          }
          onDone={handleDone}
          onStale={handleStale}
          onClose={() => setPending(null)}
          returnFocusRef={triggerRef}
        />
      )}
    </article>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  const id = useId()
  return (
    <section className="report-section" aria-labelledby={id}>
      <h3 id={id} className="report-section-title">
        {title}
      </h3>
      {children}
    </section>
  )
}

function TargetView({ report, accountId }: { report: ReportDetail; accountId: number | null }) {
  if (report.target === null) {
    return (
      <p className="action-note">
        This {TARGET_NOUN[report.targetType]} no longer exists. The report is kept for the record.
      </p>
    )
  }
  switch (report.targetType) {
    case 'USER':
      return <UserTargetView target={report.target} isYou={report.target.id === accountId} />
    case 'LISTING':
      return <ListingTargetView target={report.target} accountId={accountId} />
    case 'MESSAGE':
      return <MessageTargetView target={report.target} accountId={accountId} />
  }
}

interface TargetActionsProps {
  report: ReportDetail
  busy: boolean
  open: (action: AdminAction) => (event: MouseEvent<HTMLButtonElement>) => void
}

/** Suspend or restore, offered only where the backend's lifecycle allows it. */
function TargetActions({ report, busy, open }: TargetActionsProps) {
  if (report.target === null || report.targetType === 'MESSAGE') {
    return null
  }
  let action: TargetAction | null = null
  let label = ''
  if (report.targetType === 'USER') {
    action = report.target.status === 'ACTIVE' ? 'suspendUser' : report.target.status === 'SUSPENDED' ? 'restoreUser' : null
    label = action === 'suspendUser' ? 'Suspend account' : 'Restore account'
  } else {
    action =
      report.target.status === 'PUBLISHED'
        ? 'suspendListing'
        : report.target.status === 'SUSPENDED'
          ? 'restoreListing'
          : null
    label = action === 'suspendListing' ? 'Suspend listing' : 'Restore listing'
    if (action === null) {
      return (
        <p className="action-note">
          Draft and archived listings aren’t on the marketplace, so they can’t be suspended or restored.
        </p>
      )
    }
  }
  if (action === null) {
    return null
  }
  const suspending = action === 'suspendUser' || action === 'suspendListing'
  return (
    <div className="target-actions">
      <Button
        variant={suspending ? 'secondary' : 'primary'}
        className={suspending ? 'button-small button-caution' : 'button-small'}
        disabled={busy}
        aria-haspopup="dialog"
        onClick={open(action)}
      >
        {label}
      </Button>
    </div>
  )
}

interface DialogCopy {
  title: string
  children: ReactNode
  confirmLabel: string
  runningLabel: string
  danger?: boolean
  withNote?: boolean
}

function dialogCopy(action: AdminAction, report: ReportDetail): DialogCopy {
  const target = report.target
  switch (action) {
    case 'resolve':
      return {
        title: `Resolve report #${report.id}?`,
        children: (
          <p>
            The report is marked as valid and dealt with. This is final. Resolving doesn’t suspend anything by itself; use
            the actions on the reported {TARGET_NOUN[report.targetType]} for that.
          </p>
        ),
        confirmLabel: 'Resolve report',
        runningLabel: 'Resolving…',
        withNote: true,
      }
    case 'dismiss':
      return {
        title: `Dismiss report #${report.id}?`,
        children: <p>The report is closed without action. This is final.</p>,
        confirmLabel: 'Dismiss report',
        runningLabel: 'Dismissing…',
        withNote: true,
      }
    case 'suspendUser':
    case 'restoreUser': {
      const name = target && 'username' in target ? `@${target.username}` : 'this account'
      return action === 'suspendUser'
        ? {
            title: `Suspend ${name}?`,
            children: (
              <p>
                The account is suspended: it can no longer be used, and what it shows publicly is hidden. Nothing is
                deleted, and you can restore it at any time.
              </p>
            ),
            confirmLabel: 'Suspend account',
            runningLabel: 'Suspending…',
            danger: true,
          }
        : {
            title: `Restore ${name}?`,
            children: <p>The account becomes active again, and what the suspension hid becomes visible again.</p>,
            confirmLabel: 'Restore account',
            runningLabel: 'Restoring…',
          }
    }
    case 'suspendListing':
    case 'restoreListing': {
      const name = target && 'title' in target ? `“${target.title}”` : 'this listing'
      return action === 'suspendListing'
        ? {
            title: `Suspend ${name}?`,
            children: (
              <p>The listing is taken off the marketplace. It isn’t deleted, and you can restore it at any time.</p>
            ),
            confirmLabel: 'Suspend listing',
            runningLabel: 'Suspending…',
            danger: true,
          }
        : {
            title: `Restore ${name}?`,
            children: <p>The listing is published on the marketplace again.</p>,
            confirmLabel: 'Restore listing',
            runningLabel: 'Restoring…',
          }
    }
  }
}

function BackLink({ to }: { to: string }) {
  return (
    <nav aria-label="Breadcrumb" className="back-nav report-back">
      <Link to={to} className="back-link">
        <span aria-hidden="true">←</span> Back to queue
      </Link>
    </nav>
  )
}

function ReportNotFound({ back = paths.adminReports }: { back?: string }) {
  return (
    <section className="not-found-panel report-panel" aria-labelledby="report-not-found">
      <p className="eyebrow">404</p>
      <h2 id="report-not-found" className="report-detail-title">
        Report not found
      </h2>
      <p className="lead">This report doesn’t exist, or it has been removed.</p>
      <div className="button-row">
        <Link to={back} className="button button-primary">
          Back to queue
        </Link>
      </div>
    </section>
  )
}

function ReportDetailSkeleton({ back }: { back: string }) {
  return (
    <div className="report-panel">
      <BackLink to={back} />
      <p className="loading" role="status">
        Loading report…
      </p>
      <div className="listing-detail-skeleton" aria-hidden="true">
        <span className="skeleton skeleton-tag" />
        <span className="skeleton skeleton-title" />
        <span className="skeleton skeleton-line" />
        <span className="skeleton skeleton-line skeleton-line-short" />
      </div>
    </div>
  )
}
