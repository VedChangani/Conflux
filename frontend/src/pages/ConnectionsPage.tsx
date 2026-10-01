import { NavLink, Outlet } from 'react-router'
import { paths } from '../app/paths'
import { usePendingRequests } from '../features/connections/usePendingRequests'

export function ConnectionsPage() {
  const { count } = usePendingRequests()

  return (
    <div className="connections-page">
      <header className="page-header">
        <p className="eyebrow">Your account</p>
        <h1 className="page-title">Connections</h1>
        <p className="lead">Requests from people interested in your listings, and the ones you’ve sent.</p>
      </header>

      <nav className="tabs" aria-label="Connection requests">
        <NavLink
          to={paths.connectionsReceived}
          className="tab"
          aria-label={count ? `Received, ${count} pending` : undefined}
        >
          Received
          {count ? (
            <span className="count-badge" aria-hidden="true">
              {count > 99 ? '99+' : count}
            </span>
          ) : null}
        </NavLink>
        <NavLink to={paths.connectionsSent} className="tab">
          Sent
        </NavLink>
      </nav>

      <Outlet />
    </div>
  )
}
