import { ApiError } from '../services/apiClient'

export type FieldErrors = Partial<Record<string, string>>

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1)
}

/** Backend validation messages keyed by request field name (e.g. `email`). */
export function fieldErrorsOf(error: unknown): FieldErrors {
  const result: FieldErrors = {}
  if (error instanceof ApiError) {
    for (const { field, message } of error.fieldErrors) {
      const text = capitalize(message)
      result[field] = result[field] ? `${result[field]} ${text}.` : `${text}.`
    }
  }
  return result
}

/** A user-facing summary of a failed request. Never includes submitted values. */
export function errorMessageOf(error: unknown): string {
  if (error instanceof ApiError) {
    return error.fieldErrors.length > 0 ? 'Please correct the highlighted fields.' : error.message
  }
  return 'Something went wrong. Please try again.'
}
