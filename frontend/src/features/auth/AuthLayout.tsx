import { useId, type ReactNode } from 'react'

interface AuthLayoutProps {
  eyebrow: string
  title: string
  intro?: string
  children: ReactNode
  footer?: ReactNode
}

/** Shared frame for the login and registration screens. */
export function AuthLayout({ eyebrow, title, intro, children, footer }: AuthLayoutProps) {
  const headingId = useId()

  return (
    <div className="auth-layout">
      <aside className="auth-aside">
        <p className="eyebrow">The Conflux marketplace</p>
        <p className="auth-statement">
          Ideas, projects and startups, <mark>ready for their next builder.</mark>
        </p>
        <ol className="auth-points">
          <li>
            <span className="auth-point-index">01</span>
            Acquire promising projects, from concept to revenue.
          </li>
          <li>
            <span className="auth-point-index">02</span>
            Find the people to build them with.
          </li>
          <li>
            <span className="auth-point-index">03</span>
            Talk directly with the founders behind them.
          </li>
        </ol>
      </aside>

      <section className="auth-card" aria-labelledby={headingId}>
        <p className="eyebrow">{eyebrow}</p>
        <h1 id={headingId} className="auth-title">
          {title}
        </h1>
        {intro && <p className="auth-intro">{intro}</p>}
        {children}
        {footer && <div className="auth-footer">{footer}</div>}
      </section>
    </div>
  )
}
