import type { Account } from '../auth/types'

/**
 * Whether the confirmed account may use the admin area. Mirrors the backend, which serves
 * `/admin/**` only to ADMIN accounts that are not suspended. This only decides what the UI
 * offers; the backend still checks every request.
 */
export function isActiveAdmin(account: Account | null): boolean {
  return account !== null && account.role === 'ADMIN' && account.status === 'ACTIVE'
}
