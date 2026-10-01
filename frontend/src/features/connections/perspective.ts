import type { Connection } from './types'

/** The signed-in user's side of a connection, if any. */
export type ConnectionRole = 'owner' | 'requester' | null

/**
 * Derived only from server data: the account confirmed by `/auth/me` and the participant
 * ids in the connection response. Never from the URL or the list being viewed. It decides
 * which actions are offered; the backend still checks every one.
 */
export function roleOf(connection: Connection, accountId: number | null): ConnectionRole {
  if (accountId === null) {
    return null
  }
  if (connection.owner.id === accountId) {
    return 'owner'
  }
  return connection.requester.id === accountId ? 'requester' : null
}

export interface AvailableActions {
  /** Accept or reject: the listing owner, while pending. */
  decide: boolean
  /** Withdraw: the requester, while pending. */
  withdraw: boolean
}

export function availableActions(connection: Connection, role: ConnectionRole): AvailableActions {
  const pending = connection.status === 'PENDING'
  return { decide: pending && role === 'owner', withdraw: pending && role === 'requester' }
}

/** Whether the connection can no longer change. */
export function isFinal(connection: Connection): boolean {
  return connection.status !== 'PENDING'
}

/** One sentence on where the request stands, from the user's side. */
export function statusSummary(connection: Connection, role: ConnectionRole): string {
  switch (connection.status) {
    case 'PENDING':
      return role === 'owner' ? 'Waiting for your answer.' : 'Waiting for the owner’s answer.'
    case 'ACCEPTED':
      return role === 'owner' ? 'You accepted this request. You’re connected.' : 'The owner accepted this request. You’re connected.'
    case 'REJECTED':
      return role === 'owner' ? 'You rejected this request.' : 'The owner rejected this request.'
    case 'WITHDRAWN':
      return role === 'requester' ? 'You withdrew this request.' : 'The requester withdrew this request.'
  }
  return 'This request can no longer change.'
}
