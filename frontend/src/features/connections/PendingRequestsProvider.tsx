import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useAuth } from '../auth/useAuth'
import { connectionsApi } from './connectionsApi'
import { PendingRequestsContext, type PendingRequestsContextValue } from './pendingContext'

/**
 * The number of received requests awaiting an answer, for the navigation. Read once when
 * a session starts and again after the user answers a request; there is no polling.
 */
export function PendingRequestsProvider({ children }: { children: ReactNode }) {
  const { status, account } = useAuth()
  const accountId = status === 'authenticated' && account ? account.id : null
  const [result, setResult] = useState<{ accountId: number; count: number } | null>(null)
  const [generation, setGeneration] = useState(0)

  useEffect(() => {
    if (accountId === null) {
      return
    }
    const controller = new AbortController()
    connectionsApi.pendingReceivedCount(controller.signal).then(
      (count) => {
        if (!controller.signal.aborted) {
          setResult({ accountId, count })
        }
      },
      () => {
        // Only a hint: without it the navigation simply shows no count.
      },
    )
    return () => controller.abort()
  }, [accountId, generation])

  const refresh = useCallback(() => setGeneration((current) => current + 1), [])
  // A count read for another (or no) account is never shown.
  const count = result !== null && result.accountId === accountId ? result.count : null

  const value = useMemo<PendingRequestsContextValue>(() => ({ count, refresh }), [count, refresh])
  return <PendingRequestsContext value={value}>{children}</PendingRequestsContext>
}
