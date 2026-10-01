import { useRef, type MouseEvent } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router'
import { paths } from '../app/paths'
import { isActiveAdmin } from '../features/admin/access'
import { useAuth } from '../features/auth/useAuth'
import { PendingRequestsProvider } from '../features/connections/PendingRequestsProvider'
import { usePendingRequests } from '../features/connections/usePendingRequests'
import { SavedListingsProvider } from '../features/saved/SavedListingsProvider'
import { Button } from './Button'
import { ErrorMessage } from './ErrorMessage'
import { Loading } from './Loading'

export function AppShell() {
  const { status, verificationFailed, retryVerification, logout } = useAuth()
  const mainRef = useRef<HTMLElement>(null)

  function skipToContent(event: MouseEvent<HTMLAnchorElement>) {
    event.preventDefault()
    mainRef.current?.focus()
  }

  return (
    <PendingRequestsProvider>
      <div className="app-shell">
        <a href="#main-content" className="skip-link" onClick={skipToContent}>
          Skip to main content
        </a>
        <header className="app-header">
          <nav className="app-nav" aria-label="Main" data-session={status}>
            <Link to={paths.home} className="app-brand">
              <span className="app-brand-mark" aria-hidden="true" />
              <span className="app-brand-name">Conflux</span>
            </Link>
            <SectionNav />
            <AccountNav />
          </nav>
        </header>
        <main ref={mainRef} id="main-content" className="app-main" tabIndex={-1}>
          {status !== 'unverified' ? (
            <SavedListingsProvider>
              <Outlet />
            </SavedListingsProvider>
          ) : verificationFailed ? (
            <div className="session-check">
              <ErrorMessage
                title="We couldn't confirm your session"
                message="The server could not be reached. Check your connection and try again."
              >
                <div className="button-row">
                  <Button onClick={retryVerification}>Try again</Button>
                  <Button variant="secondary" onClick={logout}>
                    Log out
                  </Button>
                </div>
              </ErrorMessage>
            </div>
          ) : (
            <Loading label="Checking your session…" />
          )}
        </main>
      </div>
    </PendingRequestsProvider>
  )
}

function SectionNav() {
  const { status, account } = useAuth()
  const { count } = usePendingRequests()

  return (
    <div className="app-nav-sections">
      <NavLink to={paths.listings} className="app-nav-link">
        Browse
      </NavLink>
      {status === 'authenticated' && (
        <>
          <NavLink to={paths.myListings} className="app-nav-link">
            My listings
          </NavLink>
          <NavLink to={paths.saved} className="app-nav-link">
            Saved
          </NavLink>
          <NavLink
            to={paths.connections}
            className="app-nav-link"
            aria-label={count ? `Connections, ${count} pending` : undefined}
          >
            Connections
            {count ? (
              <span className="count-badge" aria-hidden="true">
                {count > 99 ? '99+' : count}
              </span>
            ) : null}
          </NavLink>
          <NavLink to={paths.messages} className="app-nav-link">
            Messages
          </NavLink>
          {isActiveAdmin(account) && (
            <NavLink to={paths.admin} className="app-nav-link app-nav-admin">
              <span className="app-nav-admin-mark" aria-hidden="true" />
              Moderation
            </NavLink>
          )}
        </>
      )}
    </div>
  )
}

function AccountNav() {
  const { status, account, logout } = useAuth()
  const navigate = useNavigate()

  if (status === 'unverified') {
    return null
  }

  if (status === 'authenticated' && account) {
    const handleLogout = () => {
      logout()
      void navigate(paths.login, { replace: true })
    }
    return (
      <div className="app-nav-links">
        <NavLink
          to={paths.profile}
          className="app-nav-user"
          title={`@${account.username}`}
          aria-label={`Profile (${account.displayName})`}
        >
          <span className="avatar" aria-hidden="true">
            {account.displayName.trim().charAt(0).toUpperCase() || '?'}
          </span>
          <span className="app-nav-user-name">{account.displayName}</span>
        </NavLink>
        <Button variant="secondary" className="button-small" onClick={handleLogout}>
          Log out
        </Button>
      </div>
    )
  }

  return (
    <div className="app-nav-links">
      <NavLink to={paths.login} className="app-nav-link">
        Log in
      </NavLink>
      <Link to={paths.register} className="button button-primary button-small">
        Register
      </Link>
    </div>
  )
}
