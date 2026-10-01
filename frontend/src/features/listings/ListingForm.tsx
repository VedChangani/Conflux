import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { Button } from '../../components/Button'
import { ErrorMessage } from '../../components/ErrorMessage'
import { SelectField, type SelectOption } from '../../components/SelectField'
import { TextAreaField } from '../../components/TextAreaField'
import { TextField } from '../../components/TextField'
import { ApiError } from '../../services/apiClient'
import { ASSET_TYPE_LABELS, CATEGORY_LABELS, MARKETPLACE_MODE_LABELS, STAGE_LABELS } from './labels'
import { manageErrorMessage } from './listingManagement'
import {
  EMPTY_LISTING_FORM,
  formValuesOf,
  remainingCharacters,
  serverFieldErrors,
  toListingRequest,
  validateField,
  validateListing,
  type ListingChoiceField,
  type ListingFieldErrors,
  type ListingFormField,
  type ListingFormValues,
  type ListingTextField,
} from './listingValidation'
import { listingsApi } from './listingsApi'
import { ASSET_TYPES, CATEGORIES, MARKETPLACE_MODES, STAGES, type ListingDetail } from './types'

const CHOICE_OPTIONS: Record<ListingChoiceField, { label: string; options: SelectOption[] }> = {
  assetType: { label: 'Type', options: ASSET_TYPES.map((value) => ({ value, label: ASSET_TYPE_LABELS[value] })) },
  marketplaceMode: {
    label: 'Opportunity',
    options: MARKETPLACE_MODES.map((value) => ({ value, label: MARKETPLACE_MODE_LABELS[value] })),
  },
  category: { label: 'Category', options: CATEGORIES.map((value) => ({ value, label: CATEGORY_LABELS[value] })) },
  stage: { label: 'Stage', options: STAGES.map((value) => ({ value, label: STAGE_LABELS[value] })) },
}

const CHOICE_HINTS: Partial<Record<ListingChoiceField, string>> = {
  marketplaceMode: 'Acquisition: you’re looking for a buyer. Collaboration: you’re looking for people to build with.',
}

type ListingFormProps =
  | { mode: 'create'; onSaved: (listing: ListingDetail) => void; onCancel: () => void }
  | {
      mode: 'edit'
      listing: ListingDetail
      onSaved: (listing: ListingDetail) => void
      onCancel: () => void
      onConflict: () => void
    }

export function ListingForm(props: ListingFormProps) {
  const { mode, onSaved, onCancel } = props
  const editing = props.mode === 'edit' ? props.listing : null
  const [values, setValues] = useState<ListingFormValues>(() => (editing ? formValuesOf(editing) : EMPTY_LISTING_FORM))
  const [errors, setErrors] = useState<ListingFieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const savingRef = useRef(false)
  const [focusRequest, setFocusRequest] = useState(0)
  const formRef = useRef<HTMLFormElement>(null)
  const formErrorId = useId()

  useEffect(() => {
    if (focusRequest === 0) {
      return
    }
    const form = formRef.current
    const target = form?.querySelector<HTMLElement>('[aria-invalid="true"]') ?? form?.querySelector<HTMLElement>('.form-error')
    target?.focus()
  }, [focusRequest])

  function update<K extends ListingFormField>(field: K, value: ListingFormValues[K]) {
    const next = { ...values, [field]: value }
    const revalidate: ListingFormField[] = field === 'askingPrice' ? ['askingPrice', 'currency'] : [field]
    setValues(next)
    setErrors((current) => {
      if (!revalidate.some((name) => current[name])) {
        return current
      }
      const updated = { ...current }
      for (const name of revalidate) {
        updated[name] = validateField(name, next) ?? undefined
      }
      return updated
    })
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (savingRef.current) {
      return
    }
    const clientErrors = validateListing(values)
    if (Object.keys(clientErrors).length > 0) {
      setErrors(clientErrors)
      setFormError('Please correct the highlighted fields.')
      setFocusRequest((current) => current + 1)
      return
    }
    savingRef.current = true
    setSaving(true)
    setErrors({})
    setFormError(null)
    const request = toListingRequest(values)
    try {
      onSaved(await (editing ? listingsApi.update(editing.id, request) : listingsApi.create(request)))
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 401) {
        return
      }
      if (props.mode === 'edit' && caught instanceof ApiError && caught.status === 409) {
        props.onConflict()
        return
      }
      const fieldErrors = serverFieldErrors(caught, values)
      setErrors(fieldErrors)
      setFormError(manageErrorMessage(caught, editing ? 'update' : 'create', Object.keys(fieldErrors).length > 0))
      setFocusRequest((current) => current + 1)
    } finally {
      savingRef.current = false
      setSaving(false)
    }
  }

  const text = (name: ListingTextField) => ({
    name,
    value: values[name],
    error: errors[name],
    onChange: (event: { target: { value: string } }) => update(name, event.target.value),
  })

  const choice = (name: ListingChoiceField) => {
    const { label, options } = CHOICE_OPTIONS[name]
    return (
      <SelectField
        label={label}
        name={name}
        required
        value={values[name]}
        options={values[name] === '' ? [{ value: '', label: `Choose ${label.toLowerCase()}…` }, ...options] : options}
        onChange={(value) => update(name, value)}
        error={errors[name]}
        hint={CHOICE_HINTS[name]}
      />
    )
  }

  return (
    <form
      ref={formRef}
      className="form listing-form"
      onSubmit={handleSubmit}
      noValidate
      aria-describedby={formError ? formErrorId : undefined}
      aria-busy={saving}
    >
      {formError && (
        <div id={formErrorId} className="form-error" tabIndex={-1}>
          <ErrorMessage
            title={mode === 'create' ? 'Your listing wasn’t created' : 'Your changes weren’t saved'}
            message={formError}
          />
        </div>
      )}

      <fieldset className="listing-fieldset">
        <legend className="listing-legend">The basics</legend>
        <TextField
          label="Title"
          required
          hint={`The name buyers and builders will see. ${remainingCharacters('title', values.title)}.`}
          {...text('title')}
        />
        <TextAreaField
          label="Short pitch"
          rows={3}
          required
          className="field-short"
          hint={`One or two sentences, shown on marketplace cards. ${remainingCharacters('shortPitch', values.shortPitch)}.`}
          {...text('shortPitch')}
        />
      </fieldset>

      <fieldset className="listing-fieldset">
        <legend className="listing-legend">Classification</legend>
        <div className="form-row">
          {choice('assetType')}
          {choice('stage')}
        </div>
        <div className="form-row">
          {choice('marketplaceMode')}
          {choice('category')}
        </div>
      </fieldset>

      <fieldset className="listing-fieldset">
        <legend className="listing-legend">The story</legend>
        <TextAreaField
          label="Description"
          rows={8}
          required
          hint={`What it is, who it’s for and where it stands. Blank lines start new paragraphs. ${remainingCharacters('description', values.description)}.`}
          {...text('description')}
        />
        <TextAreaField
          label="The problem"
          rows={4}
          hint={`Optional. ${remainingCharacters('problem', values.problem)}.`}
          {...text('problem')}
        />
        <TextAreaField
          label="The solution"
          rows={4}
          hint={`Optional. ${remainingCharacters('solution', values.solution)}.`}
          {...text('solution')}
        />
      </fieldset>

      <fieldset className="listing-fieldset">
        <legend className="listing-legend">Price and terms</legend>
        <div className="form-row listing-price-row">
          <TextField
            label="Asking price"
            name="askingPrice"
            inputMode="decimal"
            autoComplete="off"
            placeholder="e.g. 25000"
            value={values.askingPrice}
            error={errors.askingPrice}
            hint="Optional. Leave empty to show “Price on request”, or “Open to collaborate” for collaborations."
            onChange={(event) => update('askingPrice', event.target.value)}
          />
          <TextField
            label="Currency"
            name="currency"
            autoComplete="off"
            spellCheck={false}
            maxLength={3}
            value={values.currency}
            error={errors.currency}
            hint="Three-letter code, used with the asking price."
            onChange={(event) => update('currency', event.target.value.toUpperCase())}
          />
        </div>
        <label className="checkbox-field">
          <input
            type="checkbox"
            name="priceNegotiable"
            checked={values.priceNegotiable}
            onChange={(event) => update('priceNegotiable', event.target.checked)}
          />
          <span>The price is negotiable</span>
        </label>
        <TextAreaField
          label="Collaboration details"
          rows={4}
          hint={`Optional. The roles, commitment or terms you have in mind. ${remainingCharacters('collaborationDetails', values.collaborationDetails)}.`}
          {...text('collaborationDetails')}
        />
      </fieldset>

      <div className="button-row listing-form-actions">
        <Button type="submit" loading={saving} loadingText={mode === 'create' ? 'Creating…' : 'Saving…'}>
          {mode === 'create' ? 'Create draft' : 'Save changes'}
        </Button>
        <Button variant="secondary" onClick={onCancel} disabled={saving}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
