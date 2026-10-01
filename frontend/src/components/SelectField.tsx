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
}

/** Labelled native select. A `value` missing from `options` is still shown, as-is. */
export function SelectField({ label, value, options, onChange, className, ...selectProps }: SelectFieldProps) {
  const id = useId()
  const known = options.some((option) => option.value === value)

  return (
    <div className={className ? `field ${className}` : 'field'}>
      <label className="field-label" htmlFor={id}>
        {label}
      </label>
      <select
        id={id}
        className="field-input field-select"
        value={value}
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
    </div>
  )
}
