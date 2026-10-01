import type { ReactNode } from 'react'

/** Standard page wrapper: a heading followed by the page content. */
export function Page({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <section className="page">
      <h1>{title}</h1>
      {children}
    </section>
  )
}
