import { useRef, useState } from 'react'
import { ApiError } from '../../services/apiClient'
import { actionErrorMessage, isRetryable, type ConnectionAction } from './connectionErrors'
import { connectionsApi } from './connectionsApi'
import type { Connection } from './types'
import { usePendingRequests } from './usePendingRequests'

export interface ActionError {
  message: string
  retryable: boolean
}

const DONE: Record<ConnectionAction, string> = {
  accept: 'Request accepted.',
  reject: 'Request rejected.',
  withdraw: 'Request withdrawn.',
}

export function useConnectionAction(connection: Connection, onUpdated: (connection: Connection) => void) {
  const { refresh: refreshPendingCount } = usePendingRequests()
  const [running, setRunning] = useState<ConnectionAction | null>(null)
  const runningRef = useRef(false)
  const [error, setError] = useState<ActionError | null>(null)
  const [announcement, setAnnouncement] = useState('')

  async function run(action: ConnectionAction) {
    if (runningRef.current) {
      return
    }
    runningRef.current = true
    setRunning(action)
    setError(null)
    setAnnouncement('')
    const { id } = connection
    try {
      let updated: Connection
      if (action === 'withdraw') {
        await connectionsApi.withdraw(id)
        updated = await connectionsApi.detail(id).catch(() => ({ ...connection, status: 'WITHDRAWN' as const }))
      } else {
        updated = await (action === 'accept' ? connectionsApi.accept(id) : connectionsApi.reject(id))
        refreshPendingCount()
      }
      onUpdated(updated)
      setAnnouncement(DONE[action])
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 401) {
        return
      }
      setError({ message: actionErrorMessage(caught, action), retryable: isRetryable(caught) })
      if (caught instanceof ApiError && caught.status === 409) {
        try {
          onUpdated(await connectionsApi.detail(id))
        } catch {
        }
        refreshPendingCount()
      }
    } finally {
      runningRef.current = false
      setRunning(null)
    }
  }

  return { run, running, error, announcement }
}
