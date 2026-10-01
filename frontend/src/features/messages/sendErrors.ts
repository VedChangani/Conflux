import { ApiError } from '../../services/apiClient'
import { MESSAGE_MAX_LENGTH } from './types'

export interface SendError {
  message: string
  /** Sending again could work (rate limit, server or network trouble). */
  retryable: boolean
  /** Messaging is unavailable in this conversation; the composer is closed. */
  closed: boolean
}

const limit = MESSAGE_MAX_LENGTH.toLocaleString('en-US')

/**
 * What to tell the user when a message could not be sent. Written here rather than taken
 * from the response, so backend wording never reaches the page. 401 is not covered: the
 * session ends and the protected route sends the user to log in.
 */
export function sendErrorOf(error: unknown): SendError {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 0:
        return {
          message: 'Unable to reach the server. Your message wasn’t sent; check your connection and try again.',
          retryable: true,
          closed: false,
        }
      case 400:
        return {
          message: `This message can’t be sent. Messages need some text and can be at most ${limit} characters.`,
          retryable: false,
          closed: false,
        }
      case 403:
        return {
          message: 'Your account can’t send messages right now. It may be suspended.',
          retryable: false,
          closed: true,
        }
      case 404:
        return {
          message: 'This conversation doesn’t exist, or you no longer have access to it.',
          retryable: false,
          closed: true,
        }
      case 409:
        return {
          message: 'This conversation isn’t open for messages. Messaging is only available for accepted connections.',
          retryable: false,
          closed: true,
        }
      case 429:
        return {
          message: 'You’re sending messages too quickly. Wait a moment, then try again.',
          retryable: true,
          closed: false,
        }
    }
  }
  return { message: 'Your message wasn’t sent because something went wrong. Please try again.', retryable: true, closed: false }
}
