import { useCallback, useRef, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import { paths } from '../../app/paths'
import { EmptyState } from '../../components/EmptyState'
import { LoadError } from '../../components/LoadError'
import { readPageParam } from '../../lib/pageParam'
import { useAsync } from '../../lib/useAsync'
import { withChanges } from '../listings/discoveryParams'
import { ListingGrid, ListingGridSkeleton } from '../listings/ListingGrid'
import { Pagination } from '../listings/Pagination'
import type { ListingCardSummary } from '../listings/types'
import { savedApi } from './savedApi'

export const SAVED_PAGE_SIZE = 12

const numberFormat = new Intl.NumberFormat('en-US')

function countSummary(total: number, from: number, shown: number): string {
  const noun = total === 1 ? 'saved listing' : 'saved listings'
  if (shown === 0 || shown === total) {
    return `${numberFormat.format(total)} ${noun}`
  }
  const to = from + shown - 1
  return `Showing ${numberFormat.format(from)}–${numberFormat.format(to)} of ${numberFormat.format(total)} ${noun}`
}

/**
 * The signed-in user's saved listings, newest save first, paged through `?page=` (1-based
 * in the URL, as on the marketplace). Unsaving removes a card at once; the page is then
 * reloaded in the background so counts and paging stay accurate.
 */
export function SavedListings() {
  const [params] = useSearchParams()
  const location = useLocation()
  const page = readPageParam(params)

  const load = useCallback((signal: AbortSignal) => savedApi.list(page - 1, SAVED_PAGE_SIZE, signal), [page])
  const result = useAsync(load)
  // Listings unsaved on this page. Hidden straight away, before the refreshed page arrives.
  const [removed, setRemoved] = useState<ReadonlySet<number>>(() => new Set())
  const [announcement, setAnnouncement] = useState('')
  const headingRef = useRef<HTMLHeadingElement>(null)

  const { retry } = result
  const handleSavedChange = useCallback(
    (listing: ListingCardSummary, saved: boolean) => {
      if (saved) {
        return
      }
      setRemoved((current) => new Set(current).add(listing.id))
      setAnnouncement(`Removed “${listing.title}” from your saved listings.`)
      // The card (and the focused button) is gone: keep keyboard focus in the list.
      headingRef.current?.focus()
      retry()
    },
    [retry],
  )

  const hrefFor = (target: number) => {
    const search = withChanges(params, { page: target }).toString()
    return search ? `${location.pathname}?${search}` : location.pathname
  }
  const focusResults = () => headingRef.current?.focus()

  const data = result.data ?? result.previousData
  const loading = result.status === 'loading'
  const visible = data ? data.content.filter((listing) => !removed.has(listing.id)) : []
  const total = data ? data.totalElements - (data.content.length - visible.length) : 0

  let body
  if (result.status === 'error') {
    body = <LoadError title="We couldn't load your saved listings" error={result.error} onRetry={retry} />
  } else if (data === undefined || (visible.length === 0 && total > 0 && loading)) {
    body = <ListingGridSkeleton count={3} />
  } else if (total === 0) {
    body = (
      <EmptyState
        title="Nothing saved yet"
        action={
          <Link to={paths.listings} className="button button-primary">
            Browse listings
          </Link>
        }
      >
        Save listings you want to come back to, and they’ll be collected here.
      </EmptyState>
    )
  } else if (visible.length === 0) {
    const lastPage = Math.max(data.totalPages, 1)
    body = (
      <EmptyState
        title={`There is no page ${page}`}
        action={
          <Link to={hrefFor(lastPage)} className="button button-primary" onClick={focusResults}>
            Go to page {lastPage}
          </Link>
        }
      >
        Your saved listings end on page {lastPage}.
      </EmptyState>
    )
  } else {
    body = (
      <>
        <div className={loading ? 'results-body is-stale' : 'results-body'}>
          <ListingGrid
            listings={visible}
            linkState={{ savedFrom: location.search }}
            knownSaved
            onSavedChange={handleSavedChange}
          />
        </div>
        {data.totalPages > 1 && (
          <Pagination page={page} totalPages={data.totalPages} hrefFor={hrefFor} onNavigate={focusResults} />
        )}
      </>
    )
  }

  return (
    <section className="results" aria-labelledby="saved-heading" aria-busy={loading}>
      <div className="results-toolbar">
        <div className="results-heading-group">
          <h2 id="saved-heading" ref={headingRef} tabIndex={-1} className="results-heading">
            Your list
          </h2>
          <p className="results-count" role="status">
            {result.status === 'error'
              ? ''
              : data === undefined
                ? 'Loading saved listings…'
                : countSummary(total, (page - 1) * SAVED_PAGE_SIZE + 1, visible.length)}
          </p>
        </div>
      </div>
      <p className="visually-hidden" aria-live="polite">
        {announcement}
      </p>
      {body}
    </section>
  )
}
