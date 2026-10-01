/** A positive integer id from the URL, or `null` for anything else (no request is worth sending). */
export function parseId(raw: string | undefined): number | null {
  return raw !== undefined && /^[1-9]\d{0,14}$/.test(raw) ? Number(raw) : null
}
