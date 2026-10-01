import { useCallback } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { paths } from '../app/paths'
import { LoadError } from '../components/LoadError'
import { Loading } from '../components/Loading'
import { StatusPanel } from '../components/StatusPanel'
import { useAuth } from '../features/auth/useAuth'
import { ExpressInterest } from '../features/interest/ExpressInterest'
import { ListingDetailView } from '../features/listings/ListingDetailView'
import { listingsApi } from '../features/listings/listingsApi'
import { ReportButton } from '../features/reports/ReportButton'
import { SaveButton } from '../features/saved/SaveButton'
import { useAsync } from '../lib/useAsync'
import { ApiError } from '../services/apiClient'

function backTarget(state: unknown): { href: string; label: string } {
  const { from, savedFrom } = (state ?? {}) as { from?: unknown; savedFrom?: unknown }
  if (typeof from === 'string' && from.startsWith('?') && from.length > 1) {
    return { href: `${paths.listings}${from}`, label: 'Back to results' }
  }
  if (typeof savedFrom === 'string' && (savedFrom === '' || savedFrom.startsWith('?'))) {
    return { href: `${paths.saved}${savedFrom}`, label: 'Back to saved listings' }
  }
  return { href: paths.listings, label: 'Back to marketplace' }
}

export function ListingDetailPage() {
  const { slug = '' } = useParams()
  const location = useLocation()
  const { account } = useAuth()
  const back = backTarget(location.state)

  const load = useCallback((signal: AbortSignal) => listingsApi.bySlug(slug, signal), [slug])
  const result = useAsync(load)

  return (
    <div className="listing-page">
      <nav aria-label="Breadcrumb" className="back-nav">
        <Link to={back.href} className="back-link">
          <span aria-hidden="true">←</span> {back.label}
        </Link>
      </nav>

      {result.status === 'success' ? (
        <ListingDetailView
          key={result.data.id}
          listing={result.data}
          actions={
            <>
              <ExpressInterest listing={result.data} />
              <SaveButton listingId={result.data.id} block />
              {account?.id !== result.data.owner.id && (
                <ReportButton
                  target={{ type: 'LISTING', id: result.data.id, description: `Listing “${result.data.title}”` }}
                  label="Report listing"
                  className="report-button-quiet"
                />
              )}
            </>
          }
        />
      ) : result.status === 'loading' ? (
        <div className="listing-detail-loading">
          <title>Loading listing… · Conflux</title>
          <Loading label="Loading listing…" />
          <div className="listing-detail-skeleton" aria-hidden="true">
            <span className="skeleton skeleton-tag" />
            <span className="skeleton skeleton-heading" />
            <span className="skeleton skeleton-line" />
            <span className="skeleton skeleton-line skeleton-line-short" />
          </div>
        </div>
      ) : result.error instanceof ApiError && result.error.status === 404 ? (
        <StatusPanel
          code="404"
          title="Listing not found"
          documentTitle="Listing not found"
          actions={
            <Link to={paths.listings} className="button button-primary">
              Browse the marketplace
            </Link>
          }
        >
          This listing doesn't exist, or it is no longer available on the marketplace.
        </StatusPanel>
      ) : (
        <div className="listing-error">
          <title>Listing unavailable · Conflux</title>
          <h1 className="visually-hidden">Listing unavailable</h1>
          <LoadError title="We couldn't load this listing" error={result.error} onRetry={result.retry} />
        </div>
      )}
    </div>
  )
}
