import { useState, type FormEvent } from 'react'
import { Button } from '../../components/Button'
import { ErrorMessage } from '../../components/ErrorMessage'
import { TextField } from '../../components/TextField'
import { errorMessageOf, fieldErrorsOf, type FieldErrors } from '../../lib/formErrors'
import { authApi } from './authApi'
import type { RegisterRequest } from './types'

export interface RegisteredAccount {
  username: string
  displayName: string
}

const EMPTY_FORM: RegisterRequest = { email: '', username: '', displayName: '', password: '' }

export function RegisterForm({ onRegistered }: { onRegistered: (account: RegisteredAccount) => void }) {
  const [values, setValues] = useState<RegisterRequest>(EMPTY_FORM)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})

  function update(field: keyof RegisterRequest, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
    setFieldErrors((current) => ({ ...current, [field]: undefined }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) {
      return
    }
    setSubmitting(true)
    setError(null)
    setFieldErrors({})
    try {
      const account = await authApi.register({
        email: values.email.trim(),
        username: values.username.trim(),
        displayName: values.displayName.trim(),
        password: values.password,
      })
      onRegistered({ username: account.username, displayName: account.displayName })
    } catch (caught) {
      setError(caught)
      setFieldErrors(fieldErrorsOf(caught))
      setSubmitting(false)
    }
  }

  return (
    <form className="form" onSubmit={handleSubmit} noValidate aria-describedby={error ? 'register-error' : undefined}>
      {error !== null && (
        <div id="register-error">
          <ErrorMessage title="Couldn't create your account" message={errorMessageOf(error)} />
        </div>
      )}
      <TextField
        label="Email"
        name="email"
        type="email"
        autoComplete="email"
        required
        maxLength={254}
        value={values.email}
        onChange={(event) => update('email', event.target.value)}
        error={fieldErrors.email}
      />
      <div className="form-row">
        <TextField
          label="Username"
          name="username"
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          required
          maxLength={30}
          hint="3–30 characters: a–z, 0–9, _ and -"
          value={values.username}
          onChange={(event) => update('username', event.target.value)}
          error={fieldErrors.username}
        />
        <TextField
          label="Display name"
          name="displayName"
          autoComplete="name"
          required
          maxLength={100}
          hint="Shown on your profile"
          value={values.displayName}
          onChange={(event) => update('displayName', event.target.value)}
          error={fieldErrors.displayName}
        />
      </div>
      <TextField
        label="Password"
        name="password"
        type="password"
        autoComplete="new-password"
        required
        hint="At least 8 characters"
        value={values.password}
        onChange={(event) => update('password', event.target.value)}
        error={fieldErrors.password}
      />
      <Button type="submit" block loading={submitting} loadingText="Creating account…">
        Create account
      </Button>
    </form>
  )
}
