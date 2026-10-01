/** A single failing field from a backend validation error (HTTP 400). */
export interface FieldError {
  field: string
  message: string
}

/**
 * RFC 9457 problem details, as returned by the backend with
 * `Content-Type: application/problem+json`.
 */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  /** Present on validation failures. */
  errors?: FieldError[]
}

/** The backend's stable page shape (`PageResponse<T>`). `page` is zero-based. */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}
