import { ApiError } from '../services/apiClient'

const GENERIC = 'Something went wrong. Please try again.'

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
