import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { setAccessToken } from '../features/auth/tokenStorage'
import { ACCOUNT, deferred, json, mockApi, problem } from '../test/api'
import { ownerListing, pageOf } from '../test/listings'
import { renderApp } from '../test/renderApp'

function signedIn(handlers: Parameters<typeof mockApi>[0]) {
  setAccessToken('stored-token')
  return mockApi({ 'GET /auth/me': () => json(ACCOUNT), ...handlers })
}

const textbox = (name: string) => screen.getByRole('textbox', { name })
const select = (name: string) => screen.getByRole('combobox', { name })
const type = (name: string, value: string) => fireEvent.change(textbox(name), { target: { value } })
const choose = (name: string, value: string) => fireEvent.change(select(name), { target: { value } })
const submit = () => fireEvent.click(screen.getByRole('button', { name: 'Create draft' }))

async function openForm() {
  expect(await screen.findByRole('heading', { level: 1, name: 'Create a listing' })).toBeTruthy()
}

function fillRequired() {
  type('Title', '  Harbor Metrics  ')
  type('Short pitch', 'Fleet dashboards for small teams.')
  type('Description', 'Dashboards for container fleets.')
  choose('Type', 'MVP')
  choose('Stage', 'MVP')
  choose('Opportunity', 'ACQUIRE')
  choose('Category', 'DEVELOPER_TOOLS')
}

describe('Create listing', () => {
  it('is protected: anonymous visitors are sent to log in', async () => {
    mockApi({})
    const router = renderApp('/my-listings/new')

    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeTruthy()
    expect(router.state.location.pathname).toBe('/login')
  })

  it('validates required fields on the client and sends nothing until they are fixed', async () => {
    const { requests } = signedIn({})
    renderApp('/my-listings/new')
    await openForm()
    expect(document.title).toBe('Create a listing · Conflux')

    submit()

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('Please correct the highlighted fields.')
    expect(screen.getByText('Enter a title.')).toBeTruthy()
    expect(screen.getByText('Enter a short pitch.')).toBeTruthy()
    expect(screen.getByText('Enter a description.')).toBeTruthy()
    expect(screen.getByText('Choose a type.')).toBeTruthy()
    expect(screen.getByText('Choose a stage.')).toBeTruthy()
    expect(screen.getByText('Choose an opportunity.')).toBeTruthy()
    expect(screen.getByText('Choose a category.')).toBeTruthy()
    expect(textbox('Title').getAttribute('aria-invalid')).toBe('true')
    expect(select('Type').getAttribute('aria-invalid')).toBe('true')
    await waitFor(() => expect(document.activeElement).toBe(textbox('Title')))
    expect(requests.filter((request) => request.method === 'POST')).toHaveLength(0)

    type('Title', 'Harbor Metrics')
    expect(screen.queryByText('Enter a title.')).toBeNull()
  })

  it('checks the price, digits and currency exactly as the backend does', async () => {
    const { requests } = signedIn({})
    renderApp('/my-listings/new')
    await openForm()
    fillRequired()

    type('Asking price', '1,200')
    submit()
    expect(await screen.findByText(/Enter the price as a plain number/)).toBeTruthy()

    type('Asking price', '1200.555')
    expect(screen.getByText('Use at most 2 decimal places.')).toBeTruthy()

    type('Asking price', '1200.5')
    type('Currency', 'eu')
    expect(textbox('Currency')).toHaveProperty('value', 'EU')
    submit()
    expect(await screen.findByText(/three-letter currency code/)).toBeTruthy()
    expect(requests.filter((request) => request.method === 'POST')).toHaveLength(0)
  })

  it('creates a DRAFT with exactly the ListingRequest fields, then shows the returned listing', async () => {
    const created = ownerListing({
      title: 'Harbor Metrics',
      askingPrice: 1200.5,
      currency: 'EUR',
      priceNegotiable: true,
      solution: 'Automate it.',
    })
    const refresh = deferred<Response>()
    const { requests } = signedIn({
      'POST /listings': () => json(created, 201),
      'GET /listings/mine/5': () => refresh.promise,
    })
    const router = renderApp('/my-listings/new')
    await openForm()

    fillRequired()
    type('The solution', ' Automate it. ')
    type('Asking price', '1200.50')
    type('Currency', 'eur')
    fireEvent.click(screen.getByRole('checkbox', { name: 'The price is negotiable' }))
    submit()

    await waitFor(() => expect(router.state.location.pathname).toBe('/my-listings/5'))
    const post = requests.find((request) => request.method === 'POST' && request.path === '/listings')
    expect(post?.body).toEqual({
      title: 'Harbor Metrics',
      shortPitch: 'Fleet dashboards for small teams.',
      description: 'Dashboards for container fleets.',
      problem: null,
      solution: 'Automate it.',
      assetType: 'MVP',
      marketplaceMode: 'ACQUIRE',
      category: 'DEVELOPER_TOOLS',
      stage: 'MVP',
      askingPrice: 1200.5,
      currency: 'EUR',
      priceNegotiable: true,
      collaborationDetails: null,
    })
    expect(post?.headers.get('Authorization')).toBe('Bearer stored-token')
    expect(Object.keys(post?.body as object)).not.toContain('slug')

    expect(await screen.findByRole('heading', { level: 1, name: 'Harbor Metrics' })).toBeTruthy()
    expect(screen.getByText('Draft created.').closest('[role="status"]')).toBeTruthy()
    const panel = within(screen.getByRole('complementary', { name: 'Listing status' }))
    expect(panel.getByText('Draft')).toBeTruthy()
    expect(panel.getByRole('button', { name: 'Publish' })).toBeTruthy()
    expect(panel.getByRole('link', { name: 'Edit' }).getAttribute('href')).toBe('/my-listings/5/edit')

    refresh.resolve(json(created))
    await waitFor(() => expect(requests.some((request) => request.path === '/listings/mine/5')).toBe(true))
  })

  it('places backend validation errors on their fields', async () => {
    signedIn({
      'POST /listings': () =>
        problem(400, 'Invalid request content.', [{ field: 'title', message: 'must not be blank' }]),
    })
    renderApp('/my-listings/new')
    await openForm()
    fillRequired()
    submit()

    expect((await screen.findByRole('alert')).textContent).toContain('Please correct the highlighted fields.')
    expect(screen.getByText('This value wasn’t accepted. Check it and try again.')).toBeTruthy()
    expect(document.body.textContent).not.toContain('must not be blank')
  })

  it.each([
    [403, 'This account is suspended.', 'Your account is suspended, so you can’t create listings.'],
    [409, 'A unique URL could not be generated.', 'The listing couldn’t be created because of a conflict. Please try again.'],
    [429, 'Too many requests.', 'You’ve created several listings recently. Please wait a while before creating another.'],
    [500, 'java.lang.NullPointerException', 'Your listing wasn’t saved because something went wrong. Please try again.'],
  ])('explains a %i in its own words and keeps what was typed', async (status, detail, message) => {
    signedIn({ 'POST /listings': () => problem(status, detail) })
    renderApp('/my-listings/new')
    await openForm()
    fillRequired()
    submit()

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('Your listing wasn’t created')
    expect(alert.textContent).toContain(message)
    expect(document.body.textContent).not.toContain(detail)
    expect(textbox('Title')).toHaveProperty('value', '  Harbor Metrics  ')
  })

  it('cancels back to the user’s listings', async () => {
    signedIn({ 'GET /listings/mine?page=0&size=12': () => json(pageOf([])) })
    const router = renderApp('/my-listings/new')
    await openForm()

    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/my-listings'))
  })
})
