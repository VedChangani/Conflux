import { LISTING_STATUS_LABELS, labelOf } from './labels'

export function ListingStatusBadge({ status }: { status: string }) {
  return (
    <span className="status-badge listing-status-badge" data-status={status.toLowerCase()}>
      <StatusIcon status={status} />
      {labelOf(LISTING_STATUS_LABELS, status)}
    </span>
  )
}

function StatusIcon({ status }: { status: string }) {
  const stroke = { fill: 'none', stroke: 'currentColor', strokeWidth: 1.6, strokeLinecap: 'round', strokeLinejoin: 'round' } as const
  let shape
  switch (status) {
    case 'DRAFT':
      shape = <path d="M10.25 3.25 12.75 5.75 6 12.5H3.5V10Z" {...stroke} />
      break
    case 'PUBLISHED':
      shape = <path d="m3.5 8.5 3 3 6-7" {...stroke} strokeWidth={1.75} />
      break
    case 'ARCHIVED':
      shape = <path d="M2.75 4.5h10.5M3.75 4.5v7.75h8.5V4.5M6.5 7.25h3" {...stroke} />
      break
    case 'SUSPENDED':
      shape = (
        <>
          <circle cx="8" cy="8" r="5.75" {...stroke} strokeWidth={1.5} />
          <path d="M6.5 5.75v4.5M9.5 5.75v4.5" {...stroke} strokeWidth={1.5} />
        </>
      )
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
