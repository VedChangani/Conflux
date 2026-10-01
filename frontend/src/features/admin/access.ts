import type { Account } from '../auth/types'

export function isActiveAdmin(account: Account | null): boolean {
  return account !== null && account.role === 'ADMIN' && account.status === 'ACTIVE'
}
