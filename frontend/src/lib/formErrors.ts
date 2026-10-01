import { ApiError } from '../services/apiClient'

export type FieldErrors = Partial<Record<string, string>>

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1)
}

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

export function errorMessageOf(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.fieldErrors.length > 0) {
      return 'Please correct the highlighted fields.'
    }
    switch (error.status) {
      case 0:
        return error.message
      case 400:
        return 'Some details weren’t accepted. Check the form and try again.'
      case 401:
      case 409:
        return error.message
      case 429:
        return 'Too many attempts in a short time. Wait a few minutes, then try again.'
    }
  }
  return 'Something went wrong. Please try again.'
}
