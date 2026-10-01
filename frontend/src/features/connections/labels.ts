import { labelOf } from '../listings/labels'
import type { ConnectionStatus } from './types'

export const CONNECTION_STATUS_LABELS: Record<ConnectionStatus, string> = {
  PENDING: 'Pending',
  ACCEPTED: 'Accepted',
  REJECTED: 'Rejected',
  WITHDRAWN: 'Withdrawn',
}

export function statusLabel(status: string): string {
  return labelOf(CONNECTION_STATUS_LABELS, status)
}
