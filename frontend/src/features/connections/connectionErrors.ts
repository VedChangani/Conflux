import { ApiError } from '../../services/apiClient'

export type ConnectionAction = 'accept' | 'reject' | 'withdraw'

/**
 * A user-facing message for a failed accept, reject or withdraw. Written here rather than
 * taken from the response, so backend wording never reaches the page. 401 is not covered:
 * the session ends and the protected route sends the user to log in.
 */
export function actionErrorMessage(error: unknown, action: ConnectionAction): string {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 0:
        return 'Unable to reach the server. Check your connection and try again.'
      case 403:
        return action === 'withdraw'
          ? 'You can’t withdraw this request. Only the person who sent it can, and suspended accounts can’t make changes.'
          : 'You can’t answer this request. Only the listing owner can, and suspended accounts can’t make changes.'
      case 404:
        return 'This request no longer exists, or you no longer have access to it.'
      case 409:
        return 'This request has already been answered or withdrawn, so it can’t be changed.'
      case 429:
        return 'You’re doing that too often. Please wait a while and try again.'
    }
  }
  return 'Something went wrong. Please try again.'
}

/** Whether trying the same action again could succeed. */
export function isRetryable(error: unknown): boolean {
  return !(error instanceof ApiError) || ![403, 404, 409].includes(error.status)
}

export { loadErrorMessage } from '../../lib/errors'
