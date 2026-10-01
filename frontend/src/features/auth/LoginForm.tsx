import { useState, type FormEvent } from 'react'
import { Button } from '../../components/Button'
import { ErrorMessage } from '../../components/ErrorMessage'
import { TextField } from '../../components/TextField'
import { errorMessageOf, fieldErrorsOf } from '../../lib/formErrors'
import { useAuth } from './useAuth'

export function LoginForm({ initialIdentifier = '' }: { initialIdentifier?: string }) {
  const { login } = useAuth()
  const [identifier, setIdentifier] = useState(initialIdentifier)
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<unknown>(null)

  const fieldErrors = fieldErrorsOf(error)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) {
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      await login({ identifier: identifier.trim(), password })
    } catch (caught) {
      setError(caught)
      setPassword('')
      setSubmitting(false)
    }
  }

  return (
    <form className="form" onSubmit={handleSubmit} noValidate aria-describedby={error ? 'login-error' : undefined}>
      {error !== null && (
        <div id="login-error">
          <ErrorMessage title="Couldn't log you in" message={errorMessageOf(error)} />
        </div>
      )}
      <TextField
        label="Email or username"
        name="identifier"
        autoComplete="username"
        autoCapitalize="none"
        spellCheck={false}
        required
        value={identifier}
        onChange={(event) => setIdentifier(event.target.value)}
        error={fieldErrors.identifier}
      />
      <TextField
        label="Password"
        name="password"
        type="password"
        autoComplete="current-password"
        required
        value={password}
        onChange={(event) => setPassword(event.target.value)}
        error={fieldErrors.password}
      />
      <Button type="submit" block loading={submitting} loadingText="Logging in…">
        Log in
      </Button>
    </form>
  )
}
