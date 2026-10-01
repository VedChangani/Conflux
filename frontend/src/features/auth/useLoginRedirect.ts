import { useCallback } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { paths } from '../../app/paths'

export function useLoginRedirect(): () => void {
  const navigate = useNavigate()
  const location = useLocation()

  return useCallback(() => {
    void navigate(paths.login, { state: { from: location } })
  }, [navigate, location])
}
