import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { setAccessToken } from '../features/auth/tokenStorage'
import { ACCOUNT, deferred, json, mockApi, problem } from '../test/api'
import { listingDetail, pageOf } from '../test/listings'
import { renderApp } from '../test/renderApp'

const DETAIL = 'GET /listings/ledgerly'

describe('Listing detail page', () => {
  it('shows the public listing', async () => {
    mockApi({ [DETAIL]: () => json(listingDetail({ priceNegotiable: true })) })
    renderApp('/listings/ledgerly')

    expect(await screen.findByRole('heading', { level: 1, name: 'Ledgerly' })).toBeTruthy()
    expect(screen.getByText('Close the books faster.')).toBeTruthy()

    const tags = within(screen.getByRole('list', { name: 'Listing tags' }))
    expect(tags.getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      'MVP',
      'Acquisition',
      'Fintech',
      'Prototype',
    ])

    const about = screen.getByRole('region', { name: 'About this listing' })
    expect(within(about).getAllByRole('paragraph').map((p) => p.textContent)).toEqual([
      'Automated reconciliation for small finance teams.',
      'Built on open banking APIs.',
    ])
    expect(within(screen.getByRole('region', { name: 'The problem' })).getByText('Month-end close takes days.')).toBeTruthy()
    expect(within(screen.getByRole('region', { name: 'The solution' })).getByText('Match transactions automatically.')).toBeTruthy()
    expect(screen.queryByRole('region', { name: 'Collaboration details' })).toBeNull()

    const summary = within(screen.getByRole('complementary', { name: 'Listing summary' }))
    expect(summary.getByText('$25,000')).toBeTruthy()
    expect(summary.getByText('Negotiable')).toBeTruthy()
    expect(summary.getByText('Jan 15, 2026')).toBeTruthy()
    expect(summary.getByText('Feb 1, 2026')).toBeTruthy()
    expect(summary.getByText('Alice Anders')).toBeTruthy()
    expect(summary.getByText('@alice')).toBeTruthy()

    // Internal status and ids are not part of the public page.
    const text = document.body.textContent ?? ''
    expect(text).not.toContain('PUBLISHED')
    expect(document.title).toBe('Ledgerly · Conflux')
  })

  it('shows collaboration details and the unpriced state', async () => {
    mockApi({
      [DETAIL]: () =>
        json(
          listingDetail({
            marketplaceMode: 'COLLABORATE',
            askingPrice: null,
            currency: null,
            problem: null,
            solution: null,
            collaborationDetails: 'Looking for a backend engineer, 10 hours a week.',
          }),
        ),
    })
    renderApp('/listings/ledgerly')

    const section = await screen.findByRole('region', { name: 'Collaboration details' })
    expect(section.textContent).toContain('Looking for a backend engineer, 10 hours a week.')
    expect(screen.queryByRole('region', { name: 'The problem' })).toBeNull()
    expect(screen.queryByRole('region', { name: 'The solution' })).toBeNull()
    expect(within(screen.getByRole('complementary', { name: 'Listing summary' })).getByText('Open to collaborate')).toBeTruthy()
  })

  it('shows a loading state, and requests the slug without the stored token', async () => {
    setAccessToken('stored-token')
    const response = deferred<Response>()
    const { requests } = mockApi({ 'GET /auth/me': () => json(ACCOUNT), [DETAIL]: () => response.promise })
    renderApp('/listings/ledgerly')

    // After the session check, the listing's own loading status.
    expect((await screen.findByText('Loading listing…')).getAttribute('role')).toBe('status')
    response.resolve(json(listingDetail()))

    expect(await screen.findByRole('heading', { level: 1, name: 'Ledgerly' })).toBeTruthy()
    const detailRequest = requests.find((request) => request.path === '/listings/ledgerly')
    expect(detailRequest?.headers.has('Authorization')).toBe(false)
  })

  it('encodes the slug in the request', async () => {
    const { requests } = mockApi({})
    renderApp('/listings/caf%C3%A9%20bar')

    await screen.findByRole('heading', { level: 1, name: 'Listing not found' })
    expect(requests[0].path).toBe('/listings/caf%C3%A9%20bar')
  })

  it('shows a not-found page for a missing or unpublished listing', async () => {
    mockApi({ 'GET /listings/gone': () => problem(404, 'Listing not found.') })
    renderApp('/listings/gone')

    expect(await screen.findByRole('heading', { level: 1, name: 'Listing not found' })).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Browse the marketplace' }).getAttribute('href')).toBe('/listings')
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('shows other failures without backend details, with a working retry', async () => {
    const { handlers } = mockApi({ [DETAIL]: () => problem(503, 'Service temporarily unavailable.') })
    renderApp('/listings/ledgerly')

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain("We couldn't load this listing")
    expect(alert.textContent).toContain('Something went wrong. Please try again.')
    expect(document.body.textContent).not.toContain('Service temporarily unavailable.')
    expect(screen.queryByRole('heading', { name: 'Listing not found' })).toBeNull()

    handlers[DETAIL] = () => json(listingDetail())
    fireEvent.click(within(alert).getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Ledgerly' })).toBeTruthy()
  })

  it('links back to the marketplace when opened directly', async () => {
    mockApi({ [DETAIL]: () => json(listingDetail()), 'GET /listings?page=0&size=12': () => json(pageOf([])) })
    const router = renderApp('/listings/ledgerly')
    await screen.findByRole('heading', { level: 1, name: 'Ledgerly' })

    const back = within(screen.getByRole('navigation', { name: 'Breadcrumb' })).getByRole('link')
    expect(back.textContent).toContain('Back to marketplace')

    fireEvent.click(back)

    await waitFor(() => expect(router.state.location.pathname).toBe('/listings'))
    expect(await screen.findByRole('heading', { level: 1, name: 'Browse listings' })).toBeTruthy()
  })

  it('does not treat a deeper path as a listing', () => {
    mockApi({})
    renderApp('/listings/ledgerly/edit')

    expect(screen.getByRole('heading', { level: 1, name: 'Page not found' })).toBeTruthy()
  })
})
