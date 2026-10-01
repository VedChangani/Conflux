import { createContext } from 'react'

export interface PendingRequestsContextValue {
  count: number | null
  refresh: () => void
}

export const PendingRequestsContext = createContext<PendingRequestsContextValue | null>(null)
