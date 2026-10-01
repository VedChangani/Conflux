const LOCALE = 'en-US'
const dateTimeFormat = new Intl.DateTimeFormat(LOCALE, { dateStyle: 'medium', timeStyle: 'short' })
const timeFormat = new Intl.DateTimeFormat(LOCALE, { timeStyle: 'short' })
const dateFormat = new Intl.DateTimeFormat(LOCALE, { dateStyle: 'medium' })

function parse(iso: string): Date | null {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatDateTime(iso: string): string {
  const date = parse(iso)
  return date ? dateTimeFormat.format(date) : ''
}

export function formatActivity(iso: string, now: Date = new Date()): string {
  const date = parse(iso)
  if (!date) {
    return ''
  }
  return date.toDateString() === now.toDateString() ? timeFormat.format(date) : dateFormat.format(date)
}
