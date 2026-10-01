import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { setAccessToken } from '../features/auth/tokenStorage'
import { ACCOUNT, json, mockApi, problem } from '../test/api'
import { ownerListing } from '../test/listings'
import { renderApp } from '../test/renderApp'

function signedIn(handlers: Parameters<typeof mockApi>[0]) {
  setAccessToken('stored-token')
  return mockApi({ 'GET /auth/me': () => json(ACCOUNT), ...handlers })
}

const textbox = (name: string) => screen.getByRole('textbox', { name })
const save = () => fireEvent.click(screen.getByRole('button', { name: 'Save changes' }))
const editHeading = () => screen.findByRole('heading', { level: 1, name: 'Edit listing' })
const panel = () => within(screen.getByRole('complementary', { name: 'Listing status' }))

describe('Edit listing', () => {
  it('is protected: anonymous visitors are sent to log in', async () => {
    const { requests } = mockApi({})
    const router = renderApp('/my-listings/5/edit')

    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    expect(router.state.location.pathname).toBe('/login')
    expect(requests.some((request) => request.path.startsWith('/listings/mine'))).toBe(false)
  })

  it('prefills the stored values and never offers the slug for editing', async () => {
    signedIn({
      'GET /listings/mine/5': () =>
        json(ownerListing({ askingPrice: 1250.5, currency: 'EUR', solution: 'Automate it.', priceNegotiable: true })),
    })
    renderApp('/my-listings/5/edit')
    await editHeading()

    expect(document.title).toBe('Edit Harbor Metrics · Conflux')
    expect(textbox('Title')).toHaveProperty('value', 'Harbor Metrics')
    expect(textbox('The solution')).toHaveProperty('value', 'Automate it.')
    expect(textbox('Asking price')).toHaveProperty('value', '1250.5')
    expect(textbox('Currency')).toHaveProperty('value', 'EUR')
    expect(screen.getByRole('combobox', { name: 'Category' })).toHaveProperty('value', 'DEVELOPER_TOOLS')
    expect(screen.getByRole('checkbox', { name: 'The price is negotiable' })).toHaveProperty('checked', true)
    expect(screen.getByText(/This is a private draft/)).toBeTruthy()
    expect(screen.getByText('/listings/harbor-metrics-1a2b3c4d')).toBeTruthy()
    expect(screen.queryByRole('textbox', { name: /slug|address/i })).toBeNull()
  })

  it('saves a draft with PUT and it stays a DRAFT', async () => {
    const saved = ownerListing({ title: 'Harbor Metrics Pro', updatedAt: '2026-03-04T09:00:00Z' })
    const { handlers, requests } = signedIn({
      'GET /listings/mine/5': () => json(ownerListing()),
      'PUT /listings/5': () => {
        handlers['GET /listings/mine/5'] = () => json(saved)
        return json(saved)
      },
    })
    const router = renderApp('/my-listings/5/edit')
    await editHeading()

    fireEvent.change(textbox('Title'), { target: { value: 'Harbor Metrics Pro ' } })
    save()

    await waitFor(() => expect(router.state.location.pathname).toBe('/my-listings/5'))
    const put = requests.find((request) => request.method === 'PUT')
    expect(put?.path).toBe('/listings/5')
    expect(put?.body).toMatchObject({ title: 'Harbor Metrics Pro', askingPrice: 12000, currency: 'USD', priceNegotiable: true })
    expect(Object.keys(put?.body as object)).not.toEqual(expect.arrayContaining(['status', 'slug', 'publishedAt']))

    expect(await screen.findByRole('heading', { level: 1, name: 'Harbor Metrics Pro' })).toBeTruthy()
    await waitFor(() => expect(requests.filter((request) => request.path === '/listings/mine/5')).toHaveLength(2))
    expect(screen.getByRole('heading', { level: 1, name: 'Harbor Metrics Pro' })).toBeTruthy()
    expect(screen.getByText('Changes saved.').closest('[role="status"]')?.textContent).toContain('still a private draft')
    expect(panel().getByText('Draft')).toBeTruthy()
  })

  it('saves a published listing and it stays PUBLISHED with its original publishedAt', async () => {
    const published = { status: 'PUBLISHED', publishedAt: '2026-03-03T08:00:00Z' }
    const stored = ownerListing({ ...published, shortPitch: 'Sharper pitch.' })
    const { handlers } = signedIn({
      'GET /listings/mine/5': () => json(ownerListing(published)),
      'PUT /listings/5': () => {
        handlers['GET /listings/mine/5'] = () => json(stored)
        return json(stored)
      },
    })
    renderApp('/my-listings/5/edit')
    await editHeading()
    expect(screen.getByText(/This listing is live/)).toBeTruthy()

    fireEvent.change(textbox('Short pitch'), { target: { value: 'Sharper pitch.' } })
    save()

    expect(await screen.findByRole('heading', { level: 1, name: 'Harbor Metrics' })).toBeTruthy()
    expect(screen.getByText('Sharper pitch.')).toBeTruthy()
    expect(screen.getByText('Changes saved.').closest('[role="status"]')?.textContent).toContain('live on the marketplace')
    expect(panel().getByText('Published', { selector: '.listing-status-badge' })).toBeTruthy()
    expect(document.querySelector('time[datetime="2026-03-03T08:00:00Z"]')).toBeTruthy()
  })

  it.each([
    ['ARCHIVED', /Archived: off the marketplace for good/],
    ['SUSPENDED', /Suspended by Conflux moderators/],
  ])('does not offer the form for a %s listing', async (status, summary) => {
    const { requests } = signedIn({ 'GET /listings/mine/5': () => json(ownerListing({ status })) })
    renderApp('/my-listings/5/edit')

    expect(await screen.findByRole('heading', { level: 1, name: 'This listing can’t be edited' })).toBeTruthy()
    expect(screen.getByText(summary)).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Save changes' })).toBeNull()
    expect(screen.getByRole('link', { name: 'View the listing' }).getAttribute('href')).toBe('/my-listings/5')
    expect(requests.some((request) => request.method === 'PUT')).toBe(false)
  })

  it('handles a 409 on save: re-reads the listing and explains that nothing was saved', async () => {
    const { handlers } = signedIn({
      'GET /listings/mine/5': () => json(ownerListing({ status: 'PUBLISHED' })),
      'PUT /listings/5': () => problem(409, 'A listing with status SUSPENDED cannot be edited.'),
    })
    renderApp('/my-listings/5/edit')
    await editHeading()

    handlers['GET /listings/mine/5'] = () => json(ownerListing({ status: 'SUSPENDED' }))
    save()

    expect(await screen.findByRole('heading', { level: 1, name: 'This listing can’t be edited' })).toBeTruthy()
    expect(screen.getByText(/Your changes weren’t saved\./)).toBeTruthy()
    expect(screen.getByText(/Suspended by Conflux moderators/)).toBeTruthy()
    expect(document.body.textContent).not.toContain('cannot be edited.')
  })

  it.each([
    [403, 'Your account is suspended, so your listings can’t be changed.'],
    [429, 'You’ve saved changes too often recently. Please wait a while and try again.'],
  ])('explains a %i on save and keeps the edits', async (status, message) => {
    signedIn({
      'GET /listings/mine/5': () => json(ownerListing()),
      'PUT /listings/5': () => problem(status, 'backend wording'),
    })
    renderApp('/my-listings/5/edit')
    await editHeading()

    fireEvent.change(textbox('Title'), { target: { value: 'Edited title' } })
    save()

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('Your changes weren’t saved')
    expect(alert.textContent).toContain(message)
    expect(textbox('Title')).toHaveProperty('value', 'Edited title')
    expect(document.body.textContent).not.toContain('backend wording')
  })

  it('shows not found for a listing that is missing or someone else’s', async () => {
    const { requests } = signedIn({ 'GET /listings/mine/77': () => problem(404, 'Listing not found.') })
    renderApp('/my-listings/77/edit')

    expect(await screen.findByRole('heading', { level: 1, name: 'Listing not found' })).toBeTruthy()
    expect(requests.some((request) => request.method === 'PUT')).toBe(false)
  })

  it('cancels back to the listing without saving', async () => {
    const { requests } = signedIn({ 'GET /listings/mine/5': () => json(ownerListing()) })
    const router = renderApp('/my-listings/5/edit')
    await editHeading()

    fireEvent.change(textbox('Title'), { target: { value: 'Never saved' } })
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/my-listings/5'))
    expect(requests.some((request) => request.method === 'PUT')).toBe(false)
  })
})
