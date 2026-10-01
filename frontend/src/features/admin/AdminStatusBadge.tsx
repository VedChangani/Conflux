import { listingStatusLabel, reportStatusLabel, userStatusLabel } from './labels'

type BadgeKind = 'report' | 'user' | 'listing'

const LABEL: Record<BadgeKind, (status: string) => string> = {
  report: reportStatusLabel,
  user: userStatusLabel,
  listing: listingStatusLabel,
}

export function AdminStatusBadge({ kind, status }: { kind: BadgeKind; status: string }) {
  return (
    <span className="status-badge admin-badge" data-kind={kind} data-status={status.toLowerCase()}>
      <BadgeIcon status={status} />
      {LABEL[kind](status)}
    </span>
  )
}

function BadgeIcon({ status }: { status: string }) {
  const stroke = { fill: 'none', stroke: 'currentColor', strokeWidth: 1.75, strokeLinecap: 'round', strokeLinejoin: 'round' } as const
  let shape
  switch (status) {
    case 'OPEN':
      shape = (
        <>
          <circle cx="8" cy="8" r="5.75" {...stroke} strokeWidth={1.5} />
          <circle cx="8" cy="8" r="2.25" fill="currentColor" />
        </>
      )
      break
    case 'RESOLVED':
    case 'ACTIVE':
    case 'PUBLISHED':
      shape = <path d="m3.5 8.5 3 3 6-7" {...stroke} />
      break
    case 'DISMISSED':
      shape = <path d="M4 12 12 4" {...stroke} />
      break
    case 'SUSPENDED':
      shape = (
        <>
          <circle cx="8" cy="8" r="5.75" {...stroke} strokeWidth={1.5} />
          <path d="M6.5 5.75v4.5M9.5 5.75v4.5" {...stroke} strokeWidth={1.5} />
        </>
      )
      break
    case 'ARCHIVED':
      shape = <path d="M2.75 4.5h10.5M3.75 4.5v7.75h8.5V4.5M6.5 7.25h3" {...stroke} strokeWidth={1.5} />
      break
    default:
      shape = <path d="M4 8h8" {...stroke} />
  }
  return (
    <svg className="status-badge-icon" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      {shape}
    </svg>
  )
}
