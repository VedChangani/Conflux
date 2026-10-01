import { fireEvent, screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { getAccessToken } from '../features/auth/tokenStorage'
import { ACCOUNT, json, mockApi, problem } from '../test/api'
import { renderApp } from '../test/renderApp'

const FORM = {
  Email: 'ada@example.com',
  Username: 'ada',
  'Display name': 'Ada Lovelace',
  Password: 'correct horse battery',
}

function submitRegistration(values: Record<string, string> = FORM) {
  for (const [label, value] of Object.entries(values)) {
    fireEvent.change(screen.getByLabelText(label), { target: { value } })
  }
  fireEvent.click(screen.getByRole('button', { name: 'Create account' }))
}

describe('RegisterPage', () => {
  it('registers without logging in and directs the user to log in', async () => {
    const { requests } = mockApi({ 'POST /auth/register': () => json(ACCOUNT, 201) })
    const router = renderApp('/register')

    submitRegistration()

    const heading = await screen.findByRole('heading', { name: 'Account created' })
    await waitFor(() => expect(document.activeElement).toBe(heading))
    expect(screen.getByText('@ada')).toBeTruthy()
    expect(document.body.textContent).not.toContain(ACCOUNT.email)
    expect(document.body.textContent).not.toContain(FORM.Password)

    expect(requests).toHaveLength(1)
    expect(requests[0].body).toEqual({
      email: 'ada@example.com',
      username: 'ada',
      displayName: 'Ada Lovelace',
      password: 'correct horse battery',
    })
    expect(requests[0].headers.has('Authorization')).toBe(false)
    expect(getAccessToken()).toBeNull()
    expect(screen.getByRole('link', { name: 'Log in' })).toBeTruthy()

    fireEvent.click(screen.getByRole('link', { name: 'Continue to log in' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    expect(router.state.location.pathname).toBe('/login')
    expect((screen.getByLabelText('Email or username') as HTMLInputElement).value).toBe('ada')
    expect(screen.getByRole('status').textContent).toContain('Account created.')
  })

  it('shows backend validation errors next to the fields', async () => {
    mockApi({
      'POST /auth/register': () =>
        problem(400, 'Invalid request content.', [
          { field: 'username', message: "must be 3-30 characters, only a-z, 0-9, '_' and '-'" },
          { field: 'password', message: 'must be between 8 and 72 bytes (UTF-8)' },
        ]),
    })
    renderApp('/register')

    submitRegistration({ ...FORM, Username: 'A!', Password: 'short' })

    expect(await screen.findByText("Must be 3-30 characters, only a-z, 0-9, '_' and '-'.")).toBeTruthy()
    expect(screen.getByText('Must be between 8 and 72 bytes (UTF-8).')).toBeTruthy()
    expect(screen.getByLabelText('Username').getAttribute('aria-invalid')).toBe('true')
    expect(screen.getByLabelText('Password').getAttribute('aria-invalid')).toBe('true')
    expect(screen.getByLabelText('Email').getAttribute('aria-invalid')).toBeNull()
    expect(screen.getByRole('alert').textContent).toContain('Please correct the highlighted fields.')

    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'ada' } })

    expect(screen.getByLabelText('Username').getAttribute('aria-invalid')).toBeNull()
    expect(screen.queryByText("Must be 3-30 characters, only a-z, 0-9, '_' and '-'.")).toBeNull()
  })

  it('shows a duplicate email/username conflict and keeps the form', async () => {
    mockApi({ 'POST /auth/register': () => problem(409, 'Username is already taken.') })
    renderApp('/register')

    submitRegistration()

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain("Couldn't create your account")
    expect(alert.textContent).toContain('Username is already taken.')
    expect((screen.getByLabelText('Email') as HTMLInputElement).value).toBe('ada@example.com')
    expect(screen.getByRole('button', { name: 'Create account' })).toHaveProperty('disabled', false)
    expect(screen.queryByRole('heading', { name: 'Account created' })).toBeNull()
  })
})
