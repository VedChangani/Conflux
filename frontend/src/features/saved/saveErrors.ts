import { ApiError } from '../../services/apiClient'

export function saveErrorMessage(error: unknown, saving: boolean): string {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 0:
        return 'Unable to reach the server. Check your connection and try again.'
      case 403:
        return 'Your account is suspended, so saved listings can’t be changed.'
      case 404:
        return 'This listing is no longer available, so it can’t be saved.'
      case 409:
        return 'This listing changed in the meantime. Refresh the page and try again.'
      case 429:
        return 'You’re doing that too often. Please wait a moment and try again.'
    }
  }
  return saving ? 'We couldn’t save this listing. Please try again.' : 'We couldn’t remove this listing. Please try again.'
}
