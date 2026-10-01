import { Link, Outlet } from 'react-router'
import { paths } from '../../app/paths'
import { StatusPanel } from '../../components/StatusPanel'
import { useAuth } from '../auth/useAuth'
import { isActiveAdmin } from './access'

export function AdminRoute() {
  const { account } = useAuth()

  if (isActiveAdmin(account)) {
    return <Outlet />
  }
  return <AdminForbidden suspended={account?.role === 'ADMIN'} />
}

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
