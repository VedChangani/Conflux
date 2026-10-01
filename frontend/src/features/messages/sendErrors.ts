import { ApiError } from '../../services/apiClient'
import { MESSAGE_MAX_LENGTH } from './types'

export interface SendError {
  message: string
  retryable: boolean
  closed: boolean
}

const limit = MESSAGE_MAX_LENGTH.toLocaleString('en-US')

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
