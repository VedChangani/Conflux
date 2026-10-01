const LOCALE = 'en-US'
const dateTimeFormat = new Intl.DateTimeFormat(LOCALE, { dateStyle: 'medium', timeStyle: 'short' })
const timeFormat = new Intl.DateTimeFormat(LOCALE, { timeStyle: 'short' })
const dateFormat = new Intl.DateTimeFormat(LOCALE, { dateStyle: 'medium' })

function parse(iso: string): Date | null {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? null : date
}

/** e.g. `Mar 2, 2026, 9:30 AM`, in the viewer's time zone. */
export function formatDateTime(iso: string): string {
  const date = parse(iso)
  return date ? dateTimeFormat.format(date) : ''
}

/** Compact activity time: the time for today, otherwise the date. */
export function formatActivity(iso: string, now: Date = new Date()): string {
  const date = parse(iso)
  if (!date) {
    return ''
  }
  return date.toDateString() === now.toDateString() ? timeFormat.format(date) : dateFormat.format(date)
}
