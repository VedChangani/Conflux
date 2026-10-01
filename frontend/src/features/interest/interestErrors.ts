import { ApiError } from '../../services/apiClient'

export interface InterestError {
  title: string
  message: string
  /** Whether sending again could succeed; otherwise the action is withdrawn. */
  retryable: boolean
}

/**
 * What to tell the user when expressing interest fails. Written here rather than taken from
 * the response, so backend wording never reaches the page. 401 is not covered: callers send
 * the visitor to log in instead.
 */
export function interestErrorOf(error: unknown): InterestError {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 0:
        return {
          title: 'Interest not sent',
          message: 'Unable to reach the server. Check your connection and try again.',
          retryable: true,
        }
      case 403:
        return {
          title: 'Interest not sent',
          message: 'Your account is suspended, so you can’t express interest in listings.',
          retryable: false,
        }
      case 404:
        return {
          title: 'Listing unavailable',
          message: 'This listing is no longer available on the marketplace.',
          retryable: false,
        }
      case 409:
        return {
          title: 'Interest can’t be sent',
          message:
            'You can’t express interest in this listing: an earlier request was declined or withdrawn, or the listing is your own.',
          retryable: false,
        }
      case 429:
        return {
          title: 'Interest not sent',
          message: 'You’ve expressed interest in a lot of listings recently. Please try again later.',
          retryable: true,
        }
    }
  }
  return { title: 'Interest not sent', message: 'Something went wrong. Please try again.', retryable: true }
}
