import { useId, type ReactNode } from 'react'

interface StatusPanelProps {
  code: string
  title: string
  children: ReactNode
  actions?: ReactNode
  documentTitle?: string
  headingLevel?: 'h1' | 'h2'
  className?: string
}

export function StatusPanel({
  code,
  title,
  children,
  actions,
  documentTitle,
  headingLevel: Heading = 'h1',
  className,
}: StatusPanelProps) {
  const headingId = useId()

  return (
    <section
      className={className ? `not-found-panel ${className}` : 'not-found-panel'}
      aria-labelledby={headingId}
    >
      {documentTitle && <title>{`${documentTitle} · Conflux`}</title>}
      <p className="eyebrow">{code}</p>
      <Heading id={headingId} className={Heading === 'h1' ? 'page-title' : 'results-heading'}>
        {title}
      </Heading>
      <p className={Heading === 'h1' ? 'lead' : undefined}>{children}</p>
      {actions && <div className="button-row">{actions}</div>}
    </section>
  )
}
