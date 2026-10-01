import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { Button } from '../../components/Button'
import { ErrorMessage } from '../../components/ErrorMessage'
import { TextAreaField } from '../../components/TextAreaField'
import { TextField } from '../../components/TextField'
import { ApiError } from '../../services/apiClient'
import { profileApi } from './profileApi'
import { PROFILE_LIMITS, type ProfileField, type ProfileFormValues, type UserProfile } from './types'
import {
  formValuesOf,
  serverFieldErrors,
  toProfileUpdate,
  validateField,
  validateProfile,
  type ProfileFieldErrors,
} from './validation'

const numberFormat = new Intl.NumberFormat('en-US')

/** What went wrong with a save, in the form's own words. 401 ends the session instead. */
function saveErrorMessage(error: unknown, hasFieldErrors: boolean): string {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 0:
        return 'Unable to reach the server. Your changes weren’t saved; check your connection and try again.'
      case 400:
        return hasFieldErrors
          ? 'Please correct the highlighted fields.'
          : 'Some details weren’t accepted. Check the form and try again.'
      case 403:
        return 'Your account is suspended, so your profile can’t be updated.'
      case 429:
        return 'You’ve updated your profile too often. Please try again later.'
    }
  }
  return 'Your changes weren’t saved because something went wrong. Please try again.'
}

function remaining(field: ProfileField, value: string): string {
  const left = PROFILE_LIMITS[field] - value.trim().length
  return left >= 0
    ? `${numberFormat.format(left)} characters left`
    : `${numberFormat.format(-left)} characters over the limit`
}

interface ProfileFormProps {
  profile: UserProfile
  /** Called with the profile as the backend stored it. */
  onSaved: (profile: UserProfile) => void
  onCancel: () => void
}

/**
 * Edits the six editable fields and saves them all at once (`PUT /profile` replaces the
 * whole editable profile). The values stay as typed if saving fails; the shown profile only
 * changes once the backend has accepted them.
 */
export function ProfileForm({ profile, onSaved, onCancel }: ProfileFormProps) {
  const [values, setValues] = useState<ProfileFormValues>(() => formValuesOf(profile))
  const [errors, setErrors] = useState<ProfileFieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  // A ref as well as state: a second submit can arrive before the disabled button renders.
  const savingRef = useRef(false)
  const [focusRequest, setFocusRequest] = useState(0)
  const formRef = useRef<HTMLFormElement>(null)
  const headingId = useId()
  const formErrorId = useId()

  // After a failed attempt, move focus to the first invalid field, or to the summary.
  useEffect(() => {
    if (focusRequest === 0) {
      return
    }
    const form = formRef.current
    const target = form?.querySelector<HTMLElement>('[aria-invalid="true"]') ?? form?.querySelector<HTMLElement>('.form-error')
    target?.focus()
  }, [focusRequest])

  function update(field: ProfileField, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
    // Re-check a field that was flagged, so its message goes away once fixed.
    setErrors((current) =>
      current[field] ? { ...current, [field]: validateField(field, value) ?? undefined } : current,
    )
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (savingRef.current) {
      return
    }
    const clientErrors = validateProfile(values)
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
    try {
      onSaved(await profileApi.update(toProfileUpdate(values)))
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 401) {
        return
      }
      const fieldErrors = serverFieldErrors(caught, values)
      setErrors(fieldErrors)
      setFormError(saveErrorMessage(caught, Object.keys(fieldErrors).length > 0))
      setFocusRequest((current) => current + 1)
    } finally {
      savingRef.current = false
      setSaving(false)
    }
  }

  const field = (name: ProfileField) => ({
    name,
    value: values[name],
    error: errors[name],
    onChange: (event: { target: { value: string } }) => update(name, event.target.value),
  })

  return (
    <section className="profile-edit" aria-labelledby={headingId}>
      <header className="profile-edit-header">
        <p className="eyebrow">Your profile</p>
        <h1 id={headingId} className="page-title" tabIndex={-1}>
          Edit profile
        </h1>
        <p className="lead">
          This is what other members see on <strong>@{profile.username}</strong>. Your username can’t be changed.
        </p>
      </header>

      <form
        ref={formRef}
        className="form profile-form"
        onSubmit={handleSubmit}
        noValidate
        aria-describedby={formError ? formErrorId : undefined}
        aria-busy={saving}
      >
        {formError && (
          <div id={formErrorId} className="form-error" tabIndex={-1}>
            <ErrorMessage title="Your profile wasn’t saved" message={formError} />
          </div>
        )}

        <div className="profile-form-columns">
          <fieldset className="profile-fieldset">
            <legend className="profile-legend">About you</legend>
            <TextField
              label="Display name"
              autoComplete="name"
              required
              hint="Shown on your listings, requests and messages."
              {...field('displayName')}
            />
            <TextField
              label="Location"
              autoComplete="address-level2"
              hint={`Optional, e.g. Berlin, Germany. ${remaining('location', values.location)}.`}
              {...field('location')}
            />
            <TextAreaField
              label="Bio"
              rows={6}
              hint={`Optional. What you build, and what you’re looking for. ${remaining('bio', values.bio)}.`}
              {...field('bio')}
            />
          </fieldset>

          <fieldset className="profile-fieldset">
            <legend className="profile-legend">Links</legend>
            <p className="field-hint profile-fieldset-hint">Optional. Full addresses starting with https://.</p>
            <TextField label="Website" type="url" inputMode="url" autoComplete="url" spellCheck={false} placeholder="https://example.com" {...field('websiteUrl')} />
            <TextField label="GitHub" type="url" inputMode="url" spellCheck={false} placeholder="https://github.com/username" {...field('githubUrl')} />
            <TextField label="LinkedIn" type="url" inputMode="url" spellCheck={false} placeholder="https://www.linkedin.com/in/username" {...field('linkedinUrl')} />
          </fieldset>
        </div>

        <div className="button-row profile-form-actions">
          <Button type="submit" loading={saving} loadingText="Saving…">
            Save changes
          </Button>
          <Button variant="secondary" onClick={onCancel} disabled={saving}>
            Cancel
          </Button>
        </div>
      </form>
    </section>
  )
}
