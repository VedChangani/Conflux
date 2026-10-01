import { ApiError } from '../../services/apiClient'
import { RESOLUTION_NOTE_MAX_LENGTH, type AdminAction } from './types'

export type AdminErrorKind =
  | 'unauthorized'
  | 'forbidden'
  | 'invalid'
  | 'notFound'
  | 'conflict'
  | 'rateLimited'
  | 'network'
  | 'server'

export function adminErrorKind(error: unknown): AdminErrorKind {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 0:
        return 'network'
      case 400:
        return 'invalid'
      case 401:
        return 'unauthorized'
      case 403:
        return 'forbidden'
      case 404:
        return 'notFound'
      case 409:
        return 'conflict'
      case 429:
        return 'rateLimited'
    }
  }
  return 'server'
}

export const ADMIN_ACCESS_COPY = {
  title: 'Administrator access required',
  message: 'Your account doesn’t currently have administrator access. It may have lost its admin role or been suspended.',
} as const

export interface AdminLoadError {
  kind: AdminErrorKind
  title: string
  message: string
  retryable: boolean
}

const NETWORK = 'Unable to reach the server. Check your connection and try again.'
const RATE_LIMITED = 'Too many requests in a short time. Wait a moment, then try again.'

export function adminLoadError(error: unknown, subject: 'queue' | 'report'): AdminLoadError {
  const kind = adminErrorKind(error)
  const title = subject === 'queue' ? 'We couldn’t load the report queue' : 'We couldn’t load this report'
  switch (kind) {
    case 'forbidden':
      return { kind, ...ADMIN_ACCESS_COPY, retryable: false }
    case 'invalid':
      return subject === 'queue'
        ? { kind, title: 'These filters aren’t valid', message: 'Choose a status above, or go back to the open reports.', retryable: false }
        : { kind, title: 'Report not found', message: 'This report doesn’t exist.', retryable: false }
    case 'notFound':
      return { kind, title: 'Report not found', message: 'This report doesn’t exist, or it has been removed.', retryable: false }
    case 'rateLimited':
      return { kind, title, message: RATE_LIMITED, retryable: true }
    case 'network':
      return { kind, title, message: NETWORK, retryable: true }
    default:
      return { kind, title, message: 'Something went wrong on our side. Please try again.', retryable: true }
  }
}

export interface AdminActionError {
  kind: AdminErrorKind
  message: string
  retryable: boolean
  refresh: boolean
}

const SUBJECT: Record<AdminAction, string> = {
  resolve: 'report',
  dismiss: 'report',
  suspendUser: 'account',
  restoreUser: 'account',
  suspendListing: 'listing',
  restoreListing: 'listing',
}

function conflictMessage(action: AdminAction): string {
  switch (action) {
    case 'resolve':
    case 'dismiss':
      return 'This report has already been reviewed, so it can’t be changed. Its current status is shown.'
    case 'suspendUser':
      return 'You can’t suspend your own account.'
    case 'restoreUser':
      return 'This account changed in the meantime. Its current status is shown.'
    case 'suspendListing':
    case 'restoreListing':
      return 'Only published listings can be suspended, and only suspended listings restored. The listing’s current status is shown.'
  }
}

export function adminActionError(error: unknown, action: AdminAction): AdminActionError {
  const kind = adminErrorKind(error)
  const subject = SUBJECT[action]
  switch (kind) {
    case 'forbidden':
      return {
        kind,
        message: `Nothing was changed. ${ADMIN_ACCESS_COPY.message}`,
        retryable: false,
        refresh: false,
      }
    case 'invalid':
      return {
        kind,
        message:
          subject === 'report'
            ? 'This decision wasn’t accepted. Check the note and try again.'
            : `This ${subject} can’t be moderated.`,
        retryable: subject === 'report',
        refresh: false,
      }
    case 'notFound':
      return { kind, message: `This ${subject} no longer exists.`, retryable: false, refresh: true }
    case 'conflict':
      return { kind, message: conflictMessage(action), retryable: false, refresh: action !== 'suspendUser' }
    case 'rateLimited':
      return {
        kind,
        message: 'You’ve made a lot of moderation changes in a short time. Wait a moment, then try again.',
        retryable: true,
        refresh: false,
      }
    case 'network':
      return {
        kind,
        message: 'Unable to reach the server. Nothing may have changed; check your connection and try again.',
        retryable: true,
        refresh: false,
      }
    default:
      return { kind, message: 'Something went wrong, so this may not have been saved. Please try again.', retryable: true, refresh: false }
  }
}

const numberFormat = new Intl.NumberFormat('en-US')

export function noteLengthError(note: string): string | undefined {
  const over = note.trim().length - RESOLUTION_NOTE_MAX_LENGTH
  return over > 0
    ? `The note can be at most ${numberFormat.format(RESOLUTION_NOTE_MAX_LENGTH)} characters (${numberFormat.format(over)} too many).`
    : undefined
}

export function serverNoteError(error: unknown, note: string): string | undefined {
  if (error instanceof ApiError && error.fieldErrors.some(({ field }) => field === 'resolutionNote')) {
    return noteLengthError(note) ?? 'This note wasn’t accepted. Shorten or rephrase it.'
  }
  return undefined
}
