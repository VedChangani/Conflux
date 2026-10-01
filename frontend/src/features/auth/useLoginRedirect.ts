import { useCallback } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { paths } from '../../app/paths'

/**
 * Sends the visitor to the login page for an action that needs an account, remembering
 * the current page in `state.from` (as {@link ProtectedRoute} does) so a successful login
 * returns them to it.
 */
export function useLoginRedirect(): () => void {
  const navigate = useNavigate()
  const location = useLocation()

  return useCallback(() => {
    void navigate(paths.login, { state: { from: location } })
  }, [navigate, location])
}
