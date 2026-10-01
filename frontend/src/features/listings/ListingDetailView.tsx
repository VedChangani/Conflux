import { useId, type ReactNode } from 'react'
import { UserLink } from '../profile/UserLink'
import { formatDate, initialOf, priceSummary } from './format'
import { ASSET_TYPE_LABELS, CATEGORY_LABELS, labelOf, MARKETPLACE_MODE_LABELS, STAGE_LABELS } from './labels'
import type { ListingDetail } from './types'

/**
 * The public view of a published listing. Internal ids and status are not shown.
 * `actions` (e.g. express interest, save) sit in the summary panel under the price.
 */
export function ListingDetailView({ listing, actions }: { listing: ListingDetail; actions?: ReactNode }) {
  const price = priceSummary(listing)
  const mode = labelOf(MARKETPLACE_MODE_LABELS, listing.marketplaceMode)
  const assetType = labelOf(ASSET_TYPE_LABELS, listing.assetType)
  const category = labelOf(CATEGORY_LABELS, listing.category)
  const stage = labelOf(STAGE_LABELS, listing.stage)

  return (
    <article className="listing-detail" data-mode={listing.marketplaceMode.toLowerCase()}>
      <title>{`${listing.title} · Conflux`}</title>

      <header className="listing-detail-header">
        <ul className="tag-row" aria-label="Listing tags">
          <li className="tag tag-strong">{assetType}</li>
          <li className="tag">
            <span className="mode-dot" aria-hidden="true" />
            {mode}
          </li>
          <li className="tag">{category}</li>
          <li className="tag">{stage}</li>
        </ul>
        <h1 className="listing-detail-title">{listing.title}</h1>
        <p className="listing-detail-pitch">{listing.shortPitch}</p>
      </header>

      <div className="listing-detail-layout">
        <div className="listing-detail-main">
          <DetailSection title="About this listing" text={listing.description} />
          {listing.problem && <DetailSection title="The problem" text={listing.problem} />}
          {listing.solution && <DetailSection title="The solution" text={listing.solution} />}
          {listing.collaborationDetails && (
            <DetailSection title="Collaboration details" text={listing.collaborationDetails} />
          )}
        </div>

        <aside className="listing-detail-aside" aria-label="Listing summary">
          <div className="deal-panel">
            <p className="eyebrow">{price.priced ? 'Asking price' : 'Price'}</p>
            <p className={price.priced ? 'deal-price' : 'deal-price deal-price-muted'}>{price.value}</p>
            {price.negotiable && <p className="price-note">Negotiable</p>}

            {actions && <div className="deal-actions">{actions}</div>}

            <dl className="deal-facts">
              <div>
                <dt>Opportunity</dt>
                <dd>{mode}</dd>
              </div>
              <div>
                <dt>Type</dt>
                <dd>{assetType}</dd>
              </div>
              <div>
                <dt>Category</dt>
                <dd>{category}</dd>
              </div>
              <div>
                <dt>Stage</dt>
                <dd>{stage}</dd>
              </div>
              {listing.publishedAt && (
                <div>
                  <dt>Published</dt>
                  <dd>
                    <time dateTime={listing.publishedAt}>{formatDate(listing.publishedAt)}</time>
                  </dd>
                </div>
              )}
              {listing.updatedAt && (
                <div>
                  <dt>Last updated</dt>
                  <dd>
                    <time dateTime={listing.updatedAt}>{formatDate(listing.updatedAt)}</time>
                  </dd>
                </div>
              )}
            </dl>
          </div>

          <div className="owner-panel">
            <p className="eyebrow">Listed by</p>
            <p className="owner">
              <span className="avatar" aria-hidden="true">
                {initialOf(listing.owner.displayName)}
              </span>
              <span className="owner-text">
                <UserLink username={listing.owner.username} className="owner-name">
                  {listing.owner.displayName}
                </UserLink>
                <span className="owner-username">@{listing.owner.username}</span>
              </span>
            </p>
          </div>
        </aside>
      </div>
    </article>
  )
}

function DetailSection({ title, text }: { title: string; text: string }) {
  const headingId = useId()
  const paragraphs = text
    .split(/\n\s*\n/)
    .map((paragraph) => paragraph.trim())
    .filter(Boolean)

  return (
    <section className="detail-section" aria-labelledby={headingId}>
      <h2 id={headingId} className="detail-section-title">
        {title}
      </h2>
      <div className="prose">
        {paragraphs.map((paragraph, index) => (
          <p key={index}>{paragraph}</p>
        ))}
      </div>
    </section>
  )
}
