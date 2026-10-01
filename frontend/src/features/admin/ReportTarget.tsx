import { Link } from 'react-router'
import { paths } from '../../app/paths'
import { formatDateTime } from '../../lib/dates'
import { initialOf } from '../listings/format'
import { UserLink } from '../profile/UserLink'
import { isHttpUrl } from '../profile/validation'
import { AdminStatusBadge } from './AdminStatusBadge'
import type { ListingTarget, MessageTarget, ReportPerson, UserTarget } from './types'

/** `https://github.com/ada/` → `github.com/ada`. */
function displayUrl(url: string): string {
  return url.replace(/^https?:\/\//i, '').replace(/\/$/, '')
}

/** A reporter, reviewer, owner or sender: name plus a link to their public profile. */
export function PersonSummary({ person, isYou = false }: { person: ReportPerson | null; isYou?: boolean }) {
  if (person === null) {
    return <span className="admin-muted">Account no longer exists</span>
  }
  return (
    <span className="admin-person">
      <span className="avatar avatar-small" aria-hidden="true">
        {initialOf(person.displayName)}
      </span>
      <span className="admin-person-text">
        <span className="admin-person-name">
          {person.displayName}
          {isYou && <span className="you-tag">You</span>}
        </span>
        <UserLink username={person.username} className="owner-username">
          @{person.username}
        </UserLink>
      </span>
    </span>
  )
}

/** The reported account, with exactly the fields of `UserTarget`. */
export function UserTargetView({ target, isYou }: { target: UserTarget; isYou: boolean }) {
  const links = [
    { label: 'Website', url: target.websiteUrl },
    { label: 'GitHub', url: target.githubUrl },
    { label: 'LinkedIn', url: target.linkedinUrl },
  ].filter((link): link is { label: string; url: string } => Boolean(link.url))

  return (
    <div className="target-card" data-target="user">
      <div className="target-card-head">
        <span className="avatar" aria-hidden="true">
          {initialOf(target.displayName)}
        </span>
        <div className="target-card-identity">
          <p className="target-card-title">
            {target.displayName}
            {isYou && <span className="you-tag">You</span>}
          </p>
          <p className="owner-username">@{target.username}</p>
        </div>
        <AdminStatusBadge kind="user" status={target.status} />
      </div>
      {target.bio ? <p className="prose target-card-body">{target.bio}</p> : <p className="admin-muted">No bio.</p>}
      {(target.location || links.length > 0) && (
        <dl className="admin-facts">
          {target.location && (
            <div>
              <dt>Location</dt>
              <dd>{target.location}</dd>
            </div>
          )}
          {links.map(({ label, url }) => (
            <div key={label}>
              <dt>{label}</dt>
              <dd>
                {isHttpUrl(url) ? (
                  <a href={url} target="_blank" rel="noopener noreferrer nofollow ugc">
                    {displayUrl(url)}
                    <span className="visually-hidden"> (opens in a new tab)</span>
                  </a>
                ) : (
                  displayUrl(url)
                )}
              </dd>
            </div>
          ))}
        </dl>
      )}
      {target.status === 'ACTIVE' && (
        <p>
          <UserLink username={target.username} className="section-link">
            View public profile <span aria-hidden="true">→</span>
          </UserLink>
        </p>
      )}
    </div>
  )
}

/** The reported listing, with exactly the fields of `ListingTarget`. */
export function ListingTargetView({ target, accountId }: { target: ListingTarget; accountId: number | null }) {
  return (
    <div className="target-card" data-target="listing">
      <div className="target-card-head">
        <div className="target-card-identity">
          <p className="target-card-title">{target.title}</p>
          <p className="owner-username">/{target.slug}</p>
        </div>
        <AdminStatusBadge kind="listing" status={target.status} />
      </div>
      <p className="target-card-pitch">{target.shortPitch}</p>
      <details className="target-card-more">
        <summary>Full description</summary>
        <p className="prose">{target.description}</p>
      </details>
      <dl className="admin-facts">
        <div>
          <dt>Owner</dt>
          <dd>
            <PersonSummary person={target.owner} isYou={target.owner !== null && target.owner.id === accountId} />
          </dd>
        </div>
      </dl>
      {target.status === 'PUBLISHED' && (
        <p>
          <Link to={paths.listing(target.slug)} className="section-link">
            View on the marketplace <span aria-hidden="true">→</span>
          </Link>
        </p>
      )}
    </div>
  )
}

/** The reported message, with exactly the fields of `MessageTarget`. */
export function MessageTargetView({ target, accountId }: { target: MessageTarget; accountId: number | null }) {
  return (
    <div className="target-card" data-target="message">
      <blockquote className="target-message">
        <p className="prose">{target.content}</p>
      </blockquote>
      <dl className="admin-facts">
        <div>
          <dt>Sender</dt>
          <dd>
            <PersonSummary person={target.sender} isYou={target.sender !== null && target.sender.id === accountId} />
          </dd>
        </div>
        <div>
          <dt>Sent</dt>
          <dd>
            <time dateTime={target.createdAt}>{formatDateTime(target.createdAt)}</time>
          </dd>
        </div>
        <div>
          <dt>Conversation</dt>
          <dd className="admin-mono">#{target.conversationId}</dd>
        </div>
        <div>
          <dt>About listing</dt>
          <dd>
            {target.listing.title} <span className="admin-mono admin-muted">#{target.listing.id}</span>
          </dd>
        </div>
      </dl>
    </div>
  )
}
