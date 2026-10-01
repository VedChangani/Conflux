import { ApiError } from '../services/apiClient'

/**
 * A user-facing message for data that failed to load. Only the client's own network
 * message is shown as-is; backend wording never reaches the page.
 */
export function loadErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 0) {
    return error.message
  }
  return 'Something went wrong. Please try again.'
}
