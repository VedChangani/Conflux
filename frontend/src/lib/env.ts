/**
 * Base URL of the Conflux REST API (including `/api/v1`), without a trailing slash.
 * Read at call time so a missing value surfaces as a request error rather than
 * breaking the whole app at startup.
 */
export function getApiBaseUrl(): string {
  const value = import.meta.env.VITE_API_BASE_URL?.trim()
  if (!value) {
    throw new Error('VITE_API_BASE_URL is not configured.')
  }
  return value.replace(/\/+$/, '')
}
