import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ACCOUNT, deferred, json, mockApi, type RecordedRequest } from '../../test/api'
import { ADMIN, OPEN_QUEUE } from '../../test/admin'
import { pageOf } from '../../test/listings'
import { renderApp } from '../../test/renderApp'
import { setAccessToken } from '../auth/tokenStorage'
import type { Account } from '../auth/types'

const nav = () => screen.getByRole('navigation', { name: 'Main' })

function signedInAs(account: Account, handlers: Parameters<typeof mockApi>[0] = {}) {
  setAccessToken('stored-token')
  return mockApi({ 'GET /auth/me': () => json(account), ...handlers })
}

const adminRequests = (requests: RecordedRequest[]) => requests.filter((request) => request.path.startsWith('/admin'))

describe('Admin route access', () => {
  it('sends anonymous visitors to log in, remembering the admin page', async () => {
    const { requests } = mockApi({})
    const router = renderApp('/admin/reports?status=RESOLVED')

    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    expect(router.state.location.state).toMatchObject({ from: { pathname: '/admin/reports', search: '?status=RESOLVED' } })
    expect(adminRequests(requests)).toHaveLength(0)
  })

  it('shows a 403 page to a signed-in member, without calling any admin endpoint', async () => {
    const { requests } = signedInAs(ACCOUNT)
    renderApp('/admin/reports/101')

    expect(await screen.findByRole('heading', { level: 1, name: 'You don’t have access to this page' })).toBeTruthy()
    expect(screen.getByText('The moderation area is only available to Conflux administrators.')).toBeTruthy()
    expect(document.title).toBe('Access denied · Conflux')
    expect(adminRequests(requests)).toHaveLength(0)
  })

  it('shows the 403 page to a suspended administrator', async () => {
    const { requests } = signedInAs({ ...ADMIN, status: 'SUSPENDED' })
    renderApp('/admin')

    expect(await screen.findByText('Your account is suspended, so the moderation tools aren’t available.')).toBeTruthy()
    expect(adminRequests(requests)).toHaveLength(0)
  })

  it('waits for the session check before deciding', async () => {
    setAccessToken('stored-token')
    const me = deferred<Response>()
    const { requests } = mockApi({ 'GET /auth/me': () => me.promise, [OPEN_QUEUE]: () => json(pageOf([], { size: 20 })) })
    renderApp('/admin/reports')

    expect(screen.getByRole('status').textContent).toContain('Checking your session')
    expect(screen.queryByRole('heading', { name: 'You don’t have access to this page' })).toBeNull()

    me.resolve(json(ADMIN))

    expect(await screen.findByRole('heading', { level: 1, name: 'Moderation' })).toBeTruthy()
    await waitFor(() => expect(adminRequests(requests)).toHaveLength(1))
  })

  it('takes an administrator from /admin to the report queue, with the stored token', async () => {
    const { requests } = signedInAs(ADMIN, { [OPEN_QUEUE]: () => json(pageOf([], { size: 20 })) })
    const router = renderApp('/admin')

    expect(await screen.findByRole('heading', { level: 1, name: 'Moderation' })).toBeTruthy()
    expect(router.state.location.pathname).toBe('/admin/reports')
    await waitFor(() => expect(adminRequests(requests)).toHaveLength(1))
    const [queue] = adminRequests(requests)
    expect(`${queue.method} ${queue.path}`).toBe(OPEN_QUEUE)
    expect(queue.headers.get('Authorization')).toBe('Bearer stored-token')
  })
})

describe('Admin navigation', () => {
  it('is not shown to regular members, whose navigation is unchanged', async () => {
    signedInAs(ACCOUNT)
    renderApp('/')

    expect(await within(nav()).findByText('Ada Lovelace')).toBeTruthy()
    const sections = within(nav())
      .getAllByRole('link')
      .map((link) => link.getAttribute('aria-label') ?? link.textContent)
    expect(sections).toEqual(['Conflux', 'Browse', 'Saved', 'Connections', 'Messages', 'Profile (Ada Lovelace)'])
    expect(within(nav()).queryByRole('link', { name: /Moderation/ })).toBeNull()
  })

  it('is not shown to anonymous visitors', () => {
    renderApp('/')

    expect(within(nav()).queryByRole('link', { name: /Moderation/ })).toBeNull()
  })

  it('is not shown to a suspended administrator', async () => {
    signedInAs({ ...ADMIN, status: 'SUSPENDED' })
    renderApp('/')

    expect(await within(nav()).findByText('Ada Lovelace')).toBeTruthy()
    expect(within(nav()).queryByRole('link', { name: /Moderation/ })).toBeNull()
  })

  it('leads an administrator to the moderation area', async () => {
    signedInAs(ADMIN, { [OPEN_QUEUE]: () => json(pageOf([], { size: 20 })) })
    renderApp('/')

    const link = await within(nav()).findByRole('link', { name: 'Moderation' })
    expect(link.getAttribute('href')).toBe('/admin')
    expect(nav().textContent).not.toContain('ADMIN')

    fireEvent.click(link)

    expect(await screen.findByRole('heading', { level: 1, name: 'Moderation' })).toBeTruthy()
    expect(link.getAttribute('aria-current')).toBe('page')
  })
})
