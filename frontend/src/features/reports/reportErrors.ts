import { ApiError } from '../../services/apiClient'
import type { ReportTargetType } from './types'

export interface ReportOutcomeError {
  message: string
  final: boolean
  tone: 'error' | 'info'
}

const NOUNS: Record<ReportTargetType, string> = { USER: 'profile', LISTING: 'listing', MESSAGE: 'message' }

export function reportErrorOf(error: unknown, targetType: ReportTargetType, hasFieldErrors: boolean): ReportOutcomeError {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 0:
        return {
          message: 'Unable to reach the server. Your report wasn’t sent; check your connection and try again.',
          final: false,
          tone: 'error',
        }
      case 400:
        if (hasFieldErrors) {
          return { message: 'Please correct the highlighted fields.', final: false, tone: 'error' }
        }
        if (error.fieldErrors.some(({ field }) => field === 'targetType' || field === 'targetId')) {
          return { message: `This ${NOUNS[targetType]} can’t be reported.`, final: true, tone: 'error' }
        }
        return { message: 'Your report couldn’t be submitted. Check the form and try again.', final: false, tone: 'error' }
      case 403:
        return { message: 'Your account is suspended, so you can’t submit reports.', final: true, tone: 'error' }
      case 404:
        return {
          message: `This ${NOUNS[targetType]} isn’t available any more, so it can’t be reported.`,
          final: true,
          tone: 'error',
        }
      case 409:
        return { message: 'You have already reported this.', final: true, tone: 'info' }
      case 429:
        return {
          message: 'You’ve submitted several reports recently. Please wait a while before sending another.',
          final: false,
          tone: 'error',
        }
    }
  }
  return {
    message: 'Your report wasn’t sent because something went wrong. Please try again.',
    final: false,
    tone: 'error',
  }
}
