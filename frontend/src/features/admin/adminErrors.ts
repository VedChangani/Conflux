import { ApiError } from '../../services/apiClient'
import { RESOLUTION_NOTE_MAX_LENGTH, type AdminAction } from './types'

/**
 * How a failed admin request is treated. 401 is listed so callers can step aside: the API
 * client has already ended the session and the protected route sends the user to log in.
 */
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

/**
 * Shown whenever the backend refuses an admin request with 403: the account is no longer an
 * administrator, or it is suspended. This is about the account, never about the report.
 */
export const ADMIN_ACCESS_COPY = {
  title: 'Administrator access required',
  message: 'Your account doesn’t currently have administrator access. It may have lost its admin role or been suspended.',
} as const

export interface AdminLoadError {
  kind: AdminErrorKind
  title: string
  message: string
  /** Loading again could work (rate limit, server or network trouble). */
  retryable: boolean
}

const NETWORK = 'Unable to reach the server. Check your connection and try again.'
const RATE_LIMITED = 'Too many requests in a short time. Wait a moment, then try again.'

/**
 * What to show when the queue or a report could not be loaded. Written here rather than taken
 * from the response, so backend wording never reaches the page.
 */
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
  /** The same action could still succeed (rate limit, server or network trouble). */
  retryable: boolean
  /** The report or its target changed elsewhere: read it again to show the real state. */
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

/**
 * What to tell the administrator when a moderation action failed. Written here rather than
 * taken from the response, so backend wording never reaches the page.
 */
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

/** The backend's limit on the note, checked after trimming as the backend does. */
export function noteLengthError(note: string): string | undefined {
  const over = note.trim().length - RESOLUTION_NOTE_MAX_LENGTH
  return over > 0
    ? `The note can be at most ${numberFormat.format(RESOLUTION_NOTE_MAX_LENGTH)} characters (${numberFormat.format(over)} too many).`
    : undefined
}

/** The field-level message for a backend 400 about the note, in the panel's own words. */
export function serverNoteError(error: unknown, note: string): string | undefined {
  if (error instanceof ApiError && error.fieldErrors.some(({ field }) => field === 'resolutionNote')) {
    return noteLengthError(note) ?? 'This note wasn’t accepted. Shorten or rephrase it.'
  }
  return undefined
}
