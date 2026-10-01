import { useId, type ReactNode } from 'react'

interface StatusPanelProps {
  /** Short label above the heading, e.g. "404" or "403 · Restricted". */
  code: string
  title: string
  /** Explanation under the heading. */
  children: ReactNode
  /** Ways forward, e.g. a link home. */
  actions?: ReactNode
  /** Sets the document title (" · Conflux" is added). */
  documentTitle?: string
  /** `h2` where the panel sits inside a page that already has its own `h1`. */
  headingLevel?: 'h1' | 'h2'
  className?: string
}

/**
 * The dashed panel shown instead of a page that can't be displayed: not found (404),
 * access denied (403) or an unexpected failure. One look and structure everywhere.
 */
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
