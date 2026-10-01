import { Navigate, Outlet, useLocation } from 'react-router'
import { paths } from '../../app/paths'
import { Loading } from '../../components/Loading'
import { useAuth } from './useAuth'

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
