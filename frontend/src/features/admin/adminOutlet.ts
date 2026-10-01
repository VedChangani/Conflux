import { useOutletContext } from 'react-router'

export interface AdminOutletContext {
  reloadQueue: () => void
  queueSearch: string
}

export function useAdminOutlet(): AdminOutletContext {
  return useOutletContext<AdminOutletContext>()
}
