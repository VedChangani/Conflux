/** The 1-based `page` from the URL; anything missing or malformed means the first page. */
export function readPageParam(params: URLSearchParams): number {
  const raw = params.get('page') ?? ''
  return /^\d+$/.test(raw) ? Math.max(Number(raw), 1) : 1
}
