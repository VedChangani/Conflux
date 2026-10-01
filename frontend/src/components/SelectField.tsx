import { useId, type SelectHTMLAttributes } from 'react'

export interface SelectOption {
  value: string
  label: string
}

interface SelectFieldProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'id' | 'onChange' | 'value'> {
  label: string
  value: string
  options: readonly SelectOption[]
  onChange: (value: string) => void
  hint?: string
  error?: string
}

export function SelectField({ label, value, options, onChange, hint, error, className, ...selectProps }: SelectFieldProps) {
  const id = useId()
  const known = options.some((option) => option.value === value)
  const hintId = hint ? `${id}-hint` : undefined
  const errorId = error ? `${id}-error` : undefined
  const describedBy = [errorId, hintId].filter(Boolean).join(' ') || undefined

  return (
    <div className={className ? `field ${className}` : 'field'}>
      <label className="field-label" htmlFor={id}>
        {label}
      </label>
      <select
        id={id}
        className="field-input field-select"
        value={value}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        onChange={(event) => onChange(event.target.value)}
        {...selectProps}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
        {!known && <option value={value}>{value}</option>}
      </select>
      {error && (
        <p id={errorId} className="field-error">
          {error}
        </p>
      )}
      {hint && (
        <p id={hintId} className="field-hint">
          {hint}
        </p>
      )}
    </div>
  )
}
