import { Link } from 'react-router'
import { paths } from '../../app/paths'
import { formatDate, initialOf } from '../listings/format'
import { ConnectionActions } from './ConnectionActions'
import { roleOf, statusSummary } from './perspective'
import { StatusBadge } from './StatusBadge'
import type { Connection, ConnectionBox } from './types'

interface ConnectionItemProps {
  connection: Connection
  box: ConnectionBox
  accountId: number | null
  linkState?: unknown
  onUpdated: (connection: Connection) => void
}

export function ConnectionItem({ connection, box, accountId, linkState, onUpdated }: ConnectionItemProps) {
  const role = roleOf(connection, accountId)
  const received = box === 'received'
  const other = received ? connection.requester : connection.owner
  const title = connection.listing.title
  const updated = connection.updatedAt !== connection.createdAt

  return (
    <article className="connection-item" data-status={connection.status.toLowerCase()}>
      <div className="connection-item-main">
        <div className="connection-item-meta">
          <StatusBadge status={connection.status} />
          <span className="connection-item-dates">
            {received ? 'Received' : 'Sent'}{' '}
            <time dateTime={connection.createdAt}>{formatDate(connection.createdAt)}</time>
            {updated && (
              <>
                {' · Updated '}
                <time dateTime={connection.updatedAt}>{formatDate(connection.updatedAt)}</time>
              </>
            )}
          </span>
        </div>
        <h3 className="connection-item-title">
          <Link
            to={paths.connection(connection.id)}
            state={linkState}
            className="connection-item-link"
            aria-label={`${title}, request ${received ? 'from' : 'to'} ${other.displayName}`}
          >
            {title}
          </Link>
        </h3>
        <p className="connection-item-party">
          <span className="avatar avatar-small" aria-hidden="true">
            {initialOf(other.displayName)}
          </span>
          <span className="connection-item-party-text">
            <span className="connection-item-direction">{received ? 'From' : 'To'}</span>{' '}
            <span className="connection-item-name">{other.displayName}</span>{' '}
            <span className="connection-item-username">@{other.username}</span>
          </span>
        </p>
        <p className="connection-item-summary">
          {statusSummary(connection, role)}
          {connection.listing.status !== 'PUBLISHED' && ' The listing is no longer on the marketplace.'}
        </p>
      </div>
      <ConnectionActions
        connection={connection}
        role={role}
        onUpdated={onUpdated}
        compact
        labelFor={(action) =>
          action === 'withdraw'
            ? `Withdraw request for ${title}`
            : `${action === 'accept' ? 'Accept' : 'Reject'} request from ${other.displayName} for ${title}`
        }
      />
    </article>
  )
}
