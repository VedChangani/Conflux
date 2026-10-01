import { paths } from '../../app/paths'

const AUTH_PAGES: string[] = [paths.login, paths.register]

/**
 * Where to go after logging in: the page {@link ProtectedRoute} recorded in
 * `location.state.from`, or home. Only same-app paths are accepted.
 */
export function redirectTargetFrom(state: unknown): string {
  const from = (state as { from?: { pathname?: unknown; search?: unknown; hash?: unknown } } | null)?.from
  if (!from || typeof from.pathname !== 'string' || !from.pathname.startsWith('/') || from.pathname.startsWith('//')) {
    return paths.home
  }
  if (AUTH_PAGES.includes(from.pathname)) {
    return paths.home
  }
  const search = typeof from.search === 'string' ? from.search : ''
  const hash = typeof from.hash === 'string' ? from.hash : ''
  return `${from.pathname}${search}${hash}`
}
