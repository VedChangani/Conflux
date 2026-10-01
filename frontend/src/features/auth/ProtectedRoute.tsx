import { Navigate, Outlet, useLocation } from 'react-router'
import { paths } from '../../app/paths'
import { Loading } from '../../components/Loading'
import { useAuth } from './useAuth'

/**
 * Layout route that renders its children only for a confirmed account. Anonymous
 * visitors are sent to the login page, with the requested location in `state.from`
 * so a successful login returns them there. After a deliberate logout there is
 * nothing to return to.
 */
export function ProtectedRoute() {
  const { status, loggedOut } = useAuth()
  const location = useLocation()

  if (status === 'authenticated') {
    return <Outlet />
  }
  if (status === 'unverified') {
    return <Loading label="Checking your session…" />
  }
  return <Navigate to={paths.login} replace state={loggedOut ? null : { from: location }} />
}
