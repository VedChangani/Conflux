import { useId } from 'react'
import { Link } from 'react-router'
import { paths } from '../../app/paths'
import { formatDateTime } from '../../lib/dates'
import { useAuth } from '../auth/useAuth'
import { initialOf } from '../listings/format'
import { UserLink } from '../profile/UserLink'
import { ConnectionActions } from './ConnectionActions'
import { roleOf, statusSummary } from './perspective'
import { StatusBadge } from './StatusBadge'
import type { Connection, ConnectionParticipant } from './types'

interface ConnectionDetailViewProps {
  connection: Connection
  onUpdated: (connection: Connection) => void
}

export function ConnectionDetailView({ connection, onUpdated }: ConnectionDetailViewProps) {
  const { account } = useAuth()
  const role = roleOf(connection, account?.id ?? null)
  const listingId = useId()
  const peopleId = useId()
  const timelineId = useId()
  const { listing } = connection

  return (
    <article className="connection-detail" data-status={connection.status.toLowerCase()}>
      <title>{`Request for ${listing.title} · Conflux`}</title>

      <header className="connection-detail-header">
        <p className="eyebrow">
          {role === 'owner' ? 'Received request' : role === 'requester' ? 'Sent request' : 'Connection request'}
        </p>
        <h1 className="connection-detail-title">{listing.title}</h1>
        <StatusBadge status={connection.status} />
      </header>

      <div className="listing-detail-layout">
        <div className="listing-detail-main">
          <section className="detail-section" aria-labelledby={listingId}>
            <h2 id={listingId} className="detail-section-title">
              Listing
            </h2>
            <p className="connection-detail-pitch">{listing.shortPitch}</p>
            {listing.status === 'PUBLISHED' ? (
              <p>
                <Link to={paths.listing(listing.slug)} className="section-link">
                  View listing <span aria-hidden="true">→</span>
                </Link>
              </p>
            ) : (
              <p className="action-note">This listing is no longer on the marketplace.</p>
            )}
          </section>

          <section className="detail-section" aria-labelledby={peopleId}>
            <h2 id={peopleId} className="detail-section-title">
              People
            </h2>
            <div className="participants">
              <Participant label="Requester" person={connection.requester} isYou={role === 'requester'} />
              <Participant label="Listing owner" person={connection.owner} isYou={role === 'owner'} />
            </div>
          </section>

          <section className="detail-section" aria-labelledby={timelineId}>
            <h2 id={timelineId} className="detail-section-title">
              Timeline
            </h2>
            <dl className="deal-facts connection-timeline">
              <div>
                <dt>Sent</dt>
                <dd>
                  <time dateTime={connection.createdAt}>{formatDateTime(connection.createdAt)}</time>
                </dd>
              </div>
              <div>
                <dt>Last updated</dt>
                <dd>
                  <time dateTime={connection.updatedAt}>{formatDateTime(connection.updatedAt)}</time>
                </dd>
              </div>
            </dl>
          </section>
        </div>

        <aside className="listing-detail-aside" aria-label="Request status">
          <div className="deal-panel connection-status-panel">
            <p className="eyebrow">Status</p>
            <p className="connection-status-summary">{statusSummary(connection, role)}</p>
            <ConnectionActions connection={connection} role={role} onUpdated={onUpdated} />
          </div>
        </aside>
      </div>
    </article>
  )
}

function Participant({ label, person, isYou }: { label: string; person: ConnectionParticipant; isYou: boolean }) {
  return (
    <div className="participant">
      <p className="eyebrow">{label}</p>
      <p className="owner">
        <span className="avatar" aria-hidden="true">
          {initialOf(person.displayName)}
        </span>
        <span className="owner-text">
          <span className="owner-name">
            {person.displayName}
            {isYou && <span className="you-tag">You</span>}
          </span>
          <UserLink username={person.username} className="owner-username">
            @{person.username}
          </UserLink>
        </span>
      </p>
    </div>
  )
}
