import { ApiError } from '../services/apiClient'

const GENERIC = 'Something went wrong. Please try again.'

/**
 * A user-facing message for data that failed to load. Only the client's own network
 * message is shown as-is; backend wording never reaches the page. A 401 never gets here in
 * practice: the session ends and the protected route sends the user to log in.
 */
export function loadErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 0:
        return error.message
      case 403:
        return 'Your account doesn’t have access to this right now. It may be suspended.'
      case 429:
        return 'Too many requests in a short time. Wait a moment, then try again.'
    }
  }
  return GENERIC
}
