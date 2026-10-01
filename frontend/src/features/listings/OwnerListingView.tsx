import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { paths } from '../../app/paths'
import { formatDateTime } from '../../lib/dates'
import { priceSummary } from './format'
import { ASSET_TYPE_LABELS, CATEGORY_LABELS, labelOf, MARKETPLACE_MODE_LABELS, STAGE_LABELS } from './labels'
import { DetailSection } from './ListingDetailView'
import { ownerStatusSummary } from './listingManagement'
import { ListingStatusBadge } from './ListingStatusBadge'
import type { ListingDetail } from './types'

interface OwnerListingViewProps {
  listing: ListingDetail
  notice?: ReactNode
  actions: ReactNode
}

export function OwnerListingView({ listing, notice, actions }: OwnerListingViewProps) {
  const price = priceSummary(listing)
  const mode = labelOf(MARKETPLACE_MODE_LABELS, listing.marketplaceMode)
  const published = listing.status === 'PUBLISHED'

  return (
    <article
      className="listing-detail owner-listing-detail"
      data-mode={listing.marketplaceMode.toLowerCase()}
      data-status={listing.status.toLowerCase()}
    >
      <title>{`${listing.title} · My listings · Conflux`}</title>

      <header className="listing-detail-header">
        <ul className="tag-row" aria-label="Listing tags">
          <li className="tag tag-strong">{labelOf(ASSET_TYPE_LABELS, listing.assetType)}</li>
          <li className="tag">
            <span className="mode-dot" aria-hidden="true" />
            {mode}
          </li>
          <li className="tag">{labelOf(CATEGORY_LABELS, listing.category)}</li>
          <li className="tag">{labelOf(STAGE_LABELS, listing.stage)}</li>
        </ul>
        <h1 className="listing-detail-title" tabIndex={-1}>
          {listing.title}
        </h1>
        <p className="listing-detail-pitch">{listing.shortPitch}</p>
      </header>

      {notice}

      <div className="listing-detail-layout">
        <div className="listing-detail-main">
          <DetailSection title="About this listing" text={listing.description} />
          {listing.problem && <DetailSection title="The problem" text={listing.problem} />}
          {listing.solution && <DetailSection title="The solution" text={listing.solution} />}
          {listing.collaborationDetails && (
            <DetailSection title="Collaboration details" text={listing.collaborationDetails} />
          )}
        </div>

        <aside className="listing-detail-aside" aria-label="Listing status">
          <div className="deal-panel owner-status-panel">
            <p className="eyebrow">Status</p>
            <ListingStatusBadge status={listing.status} />
            <p className="owner-status-summary">{ownerStatusSummary(listing.status)}</p>
            {actions}
            {published && (
              <Link to={paths.listing(listing.slug)} className="section-link">
                View on the marketplace <span aria-hidden="true">→</span>
              </Link>
            )}
          </div>

          <div className="owner-panel">
            <p className="eyebrow">{price.priced ? 'Asking price' : 'Price'}</p>
            <p className={price.priced ? 'deal-price' : 'deal-price deal-price-muted'}>{price.value}</p>
            {price.negotiable && <p className="price-note">Negotiable</p>}
            <dl className="deal-facts">
              <div>
                <dt>Opportunity</dt>
                <dd>{mode}</dd>
              </div>
              <div>
                <dt>Created</dt>
                <dd>
                  <time dateTime={listing.createdAt}>{formatDateTime(listing.createdAt)}</time>
                </dd>
              </div>
              <div>
                <dt>Last updated</dt>
                <dd>
                  <time dateTime={listing.updatedAt}>{formatDateTime(listing.updatedAt)}</time>
                </dd>
              </div>
              <div>
                <dt>Published</dt>
                <dd>
                  {listing.publishedAt ? (
                    <time dateTime={listing.publishedAt}>{formatDateTime(listing.publishedAt)}</time>
                  ) : (
                    'Not yet'
                  )}
                </dd>
              </div>
              <div>
                <dt>Public address</dt>
                <dd className="owner-listing-slug">/listings/{listing.slug}</dd>
              </div>
            </dl>
          </div>
        </aside>
      </div>
    </article>
  )
}
