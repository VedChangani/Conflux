import { useCallback, useRef } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import { paths } from '../../app/paths'
import { EmptyState } from '../../components/EmptyState'
import { LoadError } from '../../components/LoadError'
import { readPageParam } from '../../lib/pageParam'
import { useAsync } from '../../lib/useAsync'
import { withChanges } from './discoveryParams'
import { formatDate, priceSummary } from './format'
import { ASSET_TYPE_LABELS, CATEGORY_LABELS, labelOf, MARKETPLACE_MODE_LABELS, STAGE_LABELS } from './labels'
import { MY_LISTINGS_PAGE_SIZE } from './listingManagement'
import { listingsApi } from './listingsApi'
import { ListingStatusBadge } from './ListingStatusBadge'
import { Pagination } from './Pagination'
import type { MyListingSummary } from './types'

const numberFormat = new Intl.NumberFormat('en-US')

function countSummary(total: number, from: number, shown: number): string {
  const noun = total === 1 ? 'listing' : 'listings'
  if (shown === 0 || shown === total) {
    return `${numberFormat.format(total)} ${noun}`
  }
  const to = from + shown - 1
  return `Showing ${numberFormat.format(from)}–${numberFormat.format(to)} of ${numberFormat.format(total)} ${noun}`
}

export function MyListings() {
  const [params] = useSearchParams()
  const location = useLocation()
  const page = readPageParam(params)

  const load = useCallback((signal: AbortSignal) => listingsApi.mine(page - 1, MY_LISTINGS_PAGE_SIZE, signal), [page])
  const result = useAsync(load)
  const headingRef = useRef<HTMLHeadingElement>(null)

  const hrefFor = (target: number) => {
    const search = withChanges(params, { page: target }).toString()
    return search ? `${location.pathname}?${search}` : location.pathname
  }
  const focusResults = () => headingRef.current?.focus()

  const data = result.data ?? result.previousData
  const loading = result.status === 'loading'

  let body
  if (result.status === 'error') {
    body = <LoadError title="We couldn’t load your listings" error={result.error} onRetry={result.retry} />
  } else if (data === undefined) {
    body = <MyListingsSkeleton />
  } else if (data.totalElements === 0) {
    body = (
      <EmptyState
        title="No listings yet"
        action={
          <Link to={paths.newListing} className="button button-primary">
            Create your first listing
          </Link>
        }
      >
        List an idea, project, MVP or startup. It starts as a private draft until you publish it.
      </EmptyState>
    )
  } else if (data.content.length === 0) {
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
        Your listings end on page {lastPage}.
      </EmptyState>
    )
  } else {
    body = (
      <>
        <ul className={loading ? 'owner-listing-list results-body is-stale' : 'owner-listing-list results-body'}>
          {data.content.map((listing) => (
            <li key={listing.id}>
              <MyListingItem listing={listing} />
            </li>
          ))}
        </ul>
        {data.totalPages > 1 && (
          <Pagination page={page} totalPages={data.totalPages} hrefFor={hrefFor} onNavigate={focusResults} />
        )}
      </>
    )
  }

  return (
    <section className="results" aria-labelledby="my-listings-heading" aria-busy={loading}>
      <div className="results-toolbar">
        <div className="results-heading-group">
          <h2 id="my-listings-heading" ref={headingRef} tabIndex={-1} className="results-heading">
            Your listings
          </h2>
          <p className="results-count" role="status">
            {result.status === 'error'
              ? ''
              : data === undefined
                ? 'Loading your listings…'
                : countSummary(data.totalElements, (page - 1) * MY_LISTINGS_PAGE_SIZE + 1, data.content.length)}
          </p>
        </div>
      </div>
      {body}
    </section>
  )
}

function MyListingItem({ listing }: { listing: MyListingSummary }) {
  const price = priceSummary(listing)
  const mode = labelOf(MARKETPLACE_MODE_LABELS, listing.marketplaceMode)

  return (
    <article
      className="owner-listing"
      data-status={listing.status.toLowerCase()}
      data-mode={listing.marketplaceMode.toLowerCase()}
    >
      <div className="owner-listing-main">
        <div className="owner-listing-meta">
          <ListingStatusBadge status={listing.status} />
          <span className="owner-listing-dates">
            Created <time dateTime={listing.createdAt}>{formatDate(listing.createdAt)}</time>
            {' · Updated '}
            <time dateTime={listing.updatedAt}>{formatDate(listing.updatedAt)}</time>
            {listing.publishedAt && (
              <>
                {' · Published '}
                <time dateTime={listing.publishedAt}>{formatDate(listing.publishedAt)}</time>
              </>
            )}
          </span>
        </div>
        <h3 className="owner-listing-title">
          <Link to={paths.myListing(listing.id)} className="owner-listing-link">
            {listing.title}
          </Link>
        </h3>
        <p className="owner-listing-pitch">{listing.shortPitch}</p>
        <ul className="tag-row" aria-label="Listing tags">
          <li className="tag tag-strong">{labelOf(ASSET_TYPE_LABELS, listing.assetType)}</li>
          <li className="tag">
            <span className="mode-dot" aria-hidden="true" />
            {mode}
          </li>
          <li className="tag">{labelOf(CATEGORY_LABELS, listing.category)}</li>
          <li className="tag">{labelOf(STAGE_LABELS, listing.stage)}</li>
        </ul>
      </div>
      <div className="owner-listing-price">
        <p className="eyebrow">{price.priced ? 'Asking price' : 'Price'}</p>
        <p className={price.priced ? 'owner-listing-amount' : 'owner-listing-amount owner-listing-amount-muted'}>
          {price.value}
        </p>
        {price.negotiable && <p className="price-note">Negotiable</p>}
      </div>
    </article>
  )
}

function MyListingsSkeleton() {
  return (
    <div className="owner-listing-list" aria-hidden="true">
      {Array.from({ length: 3 }, (_, index) => (
        <div key={index} className="owner-listing owner-listing-skeleton">
          <div className="owner-listing-main">
            <span className="skeleton skeleton-tag" />
            <span className="skeleton skeleton-title" />
            <span className="skeleton skeleton-line skeleton-line-short" />
          </div>
        </div>
      ))}
    </div>
  )
}
