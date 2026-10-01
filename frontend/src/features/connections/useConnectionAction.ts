import { useRef, useState } from 'react'
import { ApiError } from '../../services/apiClient'
import { actionErrorMessage, isRetryable, type ConnectionAction } from './connectionErrors'
import { connectionsApi } from './connectionsApi'
import type { Connection } from './types'
import { usePendingRequests } from './usePendingRequests'

export interface ActionError {
  message: string
  /** False when the same action cannot succeed (403, 404, 409); its controls are withdrawn. */
  retryable: boolean
}

const DONE: Record<ConnectionAction, string> = {
  accept: 'Request accepted.',
  reject: 'Request rejected.',
  withdraw: 'Request withdrawn.',
}

/**
 * Runs accept, reject or withdraw for one connection. One request at a time; the connection
 * is only updated (through `onUpdated`) with what the backend returned:
 * - accept and reject answer with the updated connection;
 * - withdraw answers 204 without a body, so the connection is read again (if that fails,
 *   the 204 itself confirms the WITHDRAWN status);
 * - a 409 means it changed elsewhere, so it is read again to show its real status.
 * A 401 ends the session; the protected route then sends the user to log in.
 */
export function useConnectionAction(connection: Connection, onUpdated: (connection: Connection) => void) {
  const { refresh: refreshPendingCount } = usePendingRequests()
  const [running, setRunning] = useState<ConnectionAction | null>(null)
  // A ref as well as state: a second click can arrive before the disabled buttons render.
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
          // The message already explains; the old status stays until the next load.
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
