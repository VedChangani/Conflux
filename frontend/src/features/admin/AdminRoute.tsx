import { Link, Outlet } from 'react-router'
import { paths } from '../../app/paths'
import { StatusPanel } from '../../components/StatusPanel'
import { useAuth } from '../auth/useAuth'
import { isActiveAdmin } from './access'

/**
 * Layout route for the admin area, nested inside {@link ProtectedRoute} so anonymous visitors
 * have already been sent to log in. Signed-in accounts that aren't active administrators get
 * a 403 page instead; nothing of the admin area is requested for them.
 */
export function AdminRoute() {
  const { account } = useAuth()

  if (isActiveAdmin(account)) {
    return <Outlet />
  }
  return <AdminForbidden suspended={account?.role === 'ADMIN'} />
}

/** The admin area's 403 page. Also shown when the backend stops accepting this admin. */
export function AdminForbidden({ suspended = false }: { suspended?: boolean }) {
  return (
    <StatusPanel
      code="403 · Restricted"
      title="You don’t have access to this page"
      documentTitle="Access denied"
      className="admin-forbidden"
      actions={
        <Link to={paths.home} className="button button-primary">
          Go to the home page
        </Link>
      }
    >
      {suspended
        ? 'Your account is suspended, so the moderation tools aren’t available.'
        : 'The moderation area is only available to Conflux administrators.'}
    </StatusPanel>
  )
}
