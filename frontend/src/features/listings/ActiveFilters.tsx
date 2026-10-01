import { FILTER_KEYS, hasActiveFilters, type DiscoveryParam, type DiscoveryQuery } from './discoveryParams'
import { FILTERS, labelOf } from './labels'

interface ActiveFiltersProps {
  query: DiscoveryQuery
  onRemove: (param: DiscoveryParam) => void
  onClearAll: () => void
}

export function ActiveFilters({ query, onRemove, onClearAll }: ActiveFiltersProps) {
  if (!hasActiveFilters(query)) {
    return null
  }

  const chips: { param: DiscoveryParam; name: string; value: string }[] = []
  if (query.search) {
    chips.push({ param: 'search', name: 'Search', value: `“${query.search}”` })
  }
  for (const key of FILTER_KEYS) {
    const value = query[key]
    const filter = FILTERS.find((candidate) => candidate.key === key)
    if (value !== null && filter) {
      chips.push({ param: key, name: filter.label, value: labelOf(filter.labels, value) })
    }
  }

  return (
    <section className="active-filters" aria-label="Active filters">
      <ul className="active-filters-list">
        {chips.map((chip) => (
          <li key={chip.param}>
            <button
              type="button"
              className="chip"
              aria-label={`Remove ${chip.name.toLowerCase()} filter: ${chip.value}`}
              onClick={() => onRemove(chip.param)}
            >
              <span className="chip-name">{chip.name}</span>
              <span className="chip-value">{chip.value}</span>
              <svg className="chip-icon" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
                <path d="m4.5 4.5 7 7M11.5 4.5l-7 7" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" />
              </svg>
            </button>
          </li>
        ))}
      </ul>
      <button type="button" className="text-button" onClick={onClearAll}>
        Clear all
      </button>
    </section>
  )
}
