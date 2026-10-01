import { statusLabel } from './labels'

export function StatusBadge({ status }: { status: string }) {
  return (
    <span className="status-badge" data-status={status.toLowerCase()}>
      <StatusIcon status={status} />
      {statusLabel(status)}
    </span>
  )
}

function StatusIcon({ status }: { status: string }) {
  const stroke = { fill: 'none', stroke: 'currentColor', strokeWidth: 1.75, strokeLinecap: 'round' } as const
  return (
    <svg className="status-badge-icon" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      {status === 'ACCEPTED' ? (
        <path d="m3.5 8.5 3 3 6-7" {...stroke} />
      ) : status === 'REJECTED' ? (
        <path d="m4.5 4.5 7 7M11.5 4.5l-7 7" {...stroke} />
      ) : status === 'WITHDRAWN' ? (
        <path d="M6 4 3 7l3 3M3.5 7H10a3 3 0 0 1 0 6H8" {...stroke} strokeLinejoin="round" />
      ) : (
        <>
          <circle cx="8" cy="8" r="5.75" {...stroke} strokeWidth={1.5} />
          <path d="M8 5v3.25l2 1.25" {...stroke} strokeWidth={1.5} />
        </>
      )}
    </svg>
  )
}
