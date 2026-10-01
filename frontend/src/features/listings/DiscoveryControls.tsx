import { useId, useState, type FormEvent } from 'react'
import { Button } from '../../components/Button'
import { SelectField } from '../../components/SelectField'
import { FILTER_KEYS, SEARCH_MAX_LENGTH, type DiscoveryParam, type DiscoveryQuery } from './discoveryParams'
import { FILTERS, labelOf } from './labels'

type Changes = Partial<Record<DiscoveryParam, string | null>>

interface DiscoveryControlsProps {
  query: DiscoveryQuery
  onChange: (changes: Changes) => void
}

/** Search box plus the enum filters. Every change goes straight to the URL. */
export function DiscoveryControls({ query, onChange }: DiscoveryControlsProps) {
  const [filtersOpen, setFiltersOpen] = useState(false)
  const panelId = useId()
  const activeCount = FILTER_KEYS.filter((key) => query[key] !== null).length

  return (
    <div className="discovery-controls">
      {/* Keyed by the URL value so back/forward navigation resets the draft text. */}
      <SearchForm key={query.search} search={query.search} onSearch={(search) => onChange({ search })} />

      <button
        type="button"
        className="button button-secondary filters-toggle"
        aria-expanded={filtersOpen}
        aria-controls={panelId}
        onClick={() => setFiltersOpen((open) => !open)}
      >
        Filters
        {activeCount > 0 && <span className="count-badge">{activeCount}</span>}
      </button>

      <fieldset id={panelId} className={filtersOpen ? 'filters filters-open' : 'filters'}>
        <legend className="visually-hidden">Filter listings</legend>
        {FILTERS.map((filter) => (
          <SelectField
            key={filter.key}
            label={filter.label}
            name={filter.key}
            value={query[filter.key] ?? ''}
            options={[
              { value: '', label: filter.anyLabel },
              ...filter.values.map((value) => ({ value, label: labelOf(filter.labels, value) })),
            ]}
            onChange={(value) => onChange({ [filter.key]: value || null })}
          />
        ))}
      </fieldset>
    </div>
  )
}

export interface SearchFormProps {
  search: string
  onSearch: (search: string | null) => void
}

export function SearchForm({ search, onSearch }: SearchFormProps) {
  const [draft, setDraft] = useState(search)
  const inputId = useId()

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSearch(draft.trim() || null)
  }

  function handleClear() {
    setDraft('')
    if (search) {
      onSearch(null)
    }
  }

  return (
    <form role="search" className="search-form" onSubmit={handleSubmit}>
      <label htmlFor={inputId} className="visually-hidden">
        Search listings
      </label>
      <div className="search-input-wrap">
        <svg className="search-icon" viewBox="0 0 20 20" aria-hidden="true" focusable="false">
          <circle cx="8.5" cy="8.5" r="5.75" fill="none" stroke="currentColor" strokeWidth="1.75" />
          <path d="m13 13 4.25 4.25" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" />
        </svg>
        <input
          id={inputId}
          type="search"
          name="search"
          className="field-input search-input"
          placeholder="Search by title, pitch or description"
          value={draft}
          maxLength={SEARCH_MAX_LENGTH}
          autoComplete="off"
          enterKeyHint="search"
          onChange={(event) => setDraft(event.target.value)}
        />
        {draft !== '' && (
          <button type="button" className="search-clear" aria-label="Clear search" onClick={handleClear}>
            <svg viewBox="0 0 16 16" aria-hidden="true" focusable="false">
              <path d="m4 4 8 8M12 4l-8 8" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" />
            </svg>
          </button>
        )}
      </div>
      <Button type="submit" className="search-submit">
        Search
      </Button>
    </form>
  )
}
