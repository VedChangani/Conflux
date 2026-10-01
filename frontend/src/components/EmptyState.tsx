import type { ReactNode } from 'react'

interface EmptyStateProps {
  title: string
  children: ReactNode
  /** Optional next steps, e.g. a link or button. */
  action?: ReactNode
}

/** The dashed "nothing here" panel used by result lists. */
export function EmptyState({ title, children, action }: EmptyStateProps) {
  return (
    <div className="empty-state">
      <span className="empty-state-mark" aria-hidden="true" />
      <h3 className="empty-state-title">{title}</h3>
      <p className="empty-state-text">{children}</p>
      {action && <div className="button-row">{action}</div>}
    </div>
  )
}
