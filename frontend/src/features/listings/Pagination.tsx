import { Link } from 'react-router'

interface PaginationProps {
  page: number
  totalPages: number
  hrefFor: (page: number) => string
  onNavigate?: () => void
}

type PageItem = number | 'gap'

function pageItems(page: number, totalPages: number): PageItem[] {
  const pages = new Set([1, totalPages, page - 1, page, page + 1])
  if (page - 3 === 1) pages.add(2)
  if (page + 3 === totalPages) pages.add(totalPages - 1)
  const sorted = [...pages].filter((p) => p >= 1 && p <= totalPages).sort((a, b) => a - b)
  const items: PageItem[] = []
  for (const p of sorted) {
    const previous = items.at(-1)
    if (typeof previous === 'number' && p - previous > 1) {
      items.push('gap')
    }
    items.push(p)
  }
  return items
}

export function Pagination({ page, totalPages, hrefFor, onNavigate }: PaginationProps) {
  const hasPrevious = page > 1
  const hasNext = page < totalPages
  const previousPage = Math.min(page - 1, Math.max(totalPages, 1))

  return (
    <nav className="pagination" aria-label="Pagination">
      {hasPrevious ? (
        <Link to={hrefFor(previousPage)} className="pagination-step" rel="prev" onClick={onNavigate}>
          <span aria-hidden="true">←</span> Previous
        </Link>
      ) : (
        <span className="pagination-step" aria-disabled="true">
          <span aria-hidden="true">←</span> Previous
        </span>
      )}

      <ol className="pagination-pages">
        {pageItems(page, totalPages).map((item, index) =>
          item === 'gap' ? (
            <li key={`gap-${index}`} className="pagination-gap" aria-hidden="true">
              …
            </li>
          ) : (
            <li key={item}>
              {item === page ? (
                <span className="pagination-page" aria-current="page">
                  <span className="visually-hidden">Page </span>
                  {item}
                </span>
              ) : (
                <Link to={hrefFor(item)} className="pagination-page" aria-label={`Page ${item}`} onClick={onNavigate}>
                  {item}
                </Link>
              )}
            </li>
          ),
        )}
      </ol>

      <p className="pagination-summary">
        Page {page} of {Math.max(totalPages, 1)}
      </p>

      {hasNext ? (
        <Link to={hrefFor(page + 1)} className="pagination-step" rel="next" onClick={onNavigate}>
          Next <span aria-hidden="true">→</span>
        </Link>
      ) : (
        <span className="pagination-step" aria-disabled="true">
          Next <span aria-hidden="true">→</span>
        </span>
      )}
    </nav>
  )
}
