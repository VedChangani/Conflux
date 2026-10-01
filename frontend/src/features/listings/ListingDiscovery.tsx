import { useCallback, useRef } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import { Button } from '../../components/Button'
import { EmptyState } from '../../components/EmptyState'
import { ErrorMessage } from '../../components/ErrorMessage'
import { LoadError } from '../../components/LoadError'
import { SelectField } from '../../components/SelectField'
import { useAsync } from '../../lib/useAsync'
import { ApiError } from '../../services/apiClient'
import type { PageResponse } from '../../types/api'
import { ActiveFilters } from './ActiveFilters'
import { DiscoveryControls } from './DiscoveryControls'
import {
  DEFAULT_PAGE_SIZE,
  hasActiveFilters,
  PAGE_SIZE_OPTIONS,
  readDiscoveryQuery,
  toApiSearch,
  withChanges,
  withoutFilters,
  type DiscoveryParam,
  type DiscoveryQuery,
} from './discoveryParams'
import { SORT_LABELS } from './labels'
import { ListingGrid, ListingGridSkeleton } from './ListingGrid'
import { listingsApi } from './listingsApi'
import { Pagination } from './Pagination'
import { SORTS, type ListingCard } from './types'

const SORT_OPTIONS = SORTS.map((sort) => ({ value: sort, label: SORT_LABELS[sort] }))
const SIZE_OPTIONS = PAGE_SIZE_OPTIONS.map((size) => ({ value: String(size), label: String(size) }))

const numberFormat = new Intl.NumberFormat('en-US')

export function ListingDiscovery() {
  const [params, setParams] = useSearchParams()
  const location = useLocation()
  const query = readDiscoveryQuery(params)
  const apiSearch = toApiSearch(query)

  const load = useCallback((signal: AbortSignal) => listingsApi.discover(apiSearch, signal), [apiSearch])
  const result = useAsync(load)
  const resultsHeadingRef = useRef<HTMLHeadingElement>(null)

  const update = (changes: Partial<Record<DiscoveryParam, string | null>>) => setParams(withChanges(params, changes))
  const clearFilters = () => setParams(withoutFilters(params))
  const hrefFor = (page: number) => {
    const search = withChanges(params, { page }).toString()
    return search ? `${location.pathname}?${search}` : location.pathname
  }
  const focusResults = () => resultsHeadingRef.current?.focus()

  const page = result.data ?? result.previousData
  const loading = result.status === 'loading'

  return (
    <div className="discovery">
      <DiscoveryControls query={query} onChange={update} />
      <ActiveFilters query={query} onRemove={(param) => update({ [param]: null })} onClearAll={clearFilters} />

      <section className="results" aria-labelledby="results-heading" aria-busy={loading}>
        <div className="results-toolbar">
          <div className="results-heading-group">
            <h2 id="results-heading" ref={resultsHeadingRef} tabIndex={-1} className="results-heading">
              {query.search ? <>Results for “{query.search}”</> : hasActiveFilters(query) ? 'Filtered listings' : 'All listings'}
            </h2>
            <p className="results-count" role="status">
              {loading ? 'Loading listings…' : result.status === 'success' ? resultSummary(result.data) : ''}
            </p>
          </div>
          <div className="results-options">
            <SelectField
              label="Sort by"
              name="sort"
              className="field-inline"
              value={query.sort ?? 'NEWEST'}
              options={SORT_OPTIONS}
              onChange={(sort) => update({ sort: sort === 'NEWEST' ? null : sort })}
            />
            <SelectField
              label="Per page"
              name="size"
              className="field-inline"
              value={query.size ?? String(DEFAULT_PAGE_SIZE)}
              options={SIZE_OPTIONS}
              onChange={(size) => update({ size: size === String(DEFAULT_PAGE_SIZE) ? null : size })}
            />
          </div>
        </div>

        {result.status === 'error' ? (
          <DiscoveryError error={result.error} onRetry={result.retry} resetHref={location.pathname} />
        ) : page === undefined ? (
          <ListingGridSkeleton />
        ) : (
          <Results
            page={page}
            query={query}
            loading={loading}
            linkState={{ from: location.search }}
            onClearFilters={clearFilters}
            hrefFor={hrefFor}
            onNavigate={focusResults}
          />
        )}
      </section>
    </div>
  )
}

function resultSummary(page: PageResponse<ListingCard>): string {
  const total = page.totalElements
  if (total === 0) {
    return 'No listings found'
  }
  const noun = total === 1 ? 'listing' : 'listings'
  if (page.content.length === 0) {
    return `${numberFormat.format(total)} ${noun}`
  }
  const from = page.page * page.size + 1
  const to = from + page.content.length - 1
  return `Showing ${numberFormat.format(from)}–${numberFormat.format(to)} of ${numberFormat.format(total)} ${noun}`
}

interface ResultsProps {
  page: PageResponse<ListingCard>
  query: DiscoveryQuery
  loading: boolean
  linkState: unknown
  onClearFilters: () => void
  hrefFor: (page: number) => string
  onNavigate: () => void
}

function Results({ page, query, loading, linkState, onClearFilters, hrefFor, onNavigate }: ResultsProps) {
  if (page.totalElements === 0) {
    return hasActiveFilters(query) ? (
      <EmptyState title="No listings match your search" action={<Button onClick={onClearFilters}>Clear search and filters</Button>}>
        Try different keywords, or remove a filter or two.
      </EmptyState>
    ) : (
      <EmptyState title="No listings yet">Published projects will appear here. Check back soon.</EmptyState>
    )
  }

  const pagination = <Pagination page={query.page} totalPages={page.totalPages} hrefFor={hrefFor} onNavigate={onNavigate} />

  if (page.content.length === 0) {
    const lastPage = page.totalPages
    return (
      <>
        <EmptyState
          title={`There is no page ${query.page}`}
          action={
            <Link to={hrefFor(lastPage)} className="button button-primary" onClick={onNavigate}>
              Go to page {lastPage}
            </Link>
          }
        >
          These results end on page {lastPage}.
        </EmptyState>
        {pagination}
      </>
    )
  }

  return (
    <>
      <div className={loading ? 'results-body is-stale' : 'results-body'}>
        <ListingGrid listings={page.content} linkState={linkState} />
      </div>
      {page.totalPages > 1 && pagination}
    </>
  )
}

function DiscoveryError({ error, onRetry, resetHref }: { error: unknown; onRetry: () => void; resetHref: string }) {
  if (error instanceof ApiError && error.status === 400) {
    return (
      <ErrorMessage
        title="Some search options aren't valid"
        message="This link has search options the marketplace doesn't recognise. Change them above, or reset the search."
      >
        <div className="button-row">
          <Link to={resetHref} className="button button-secondary">
            Reset search
          </Link>
        </div>
      </ErrorMessage>
    )
  }
  return <LoadError title="We couldn't load listings" error={error} onRetry={onRetry} />
}
