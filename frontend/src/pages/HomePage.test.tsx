import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { json, mockApi, problem } from '../test/api'
import { listingCard, pageOf } from '../test/listings'
import { renderApp } from '../test/renderApp'

const LATEST = 'GET /listings?page=0&size=6'

describe('Home page', () => {
  it('shows the latest listings from the discovery API', async () => {
    const { requests } = mockApi({ [LATEST]: () => json(pageOf([listingCard()], { size: 6 })) })
    renderApp('/')

    expect(screen.getByRole('heading', { level: 1, name: 'Conflux' })).toBeTruthy()
    const latest = screen.getByRole('region', { name: 'Latest listings' })
    expect(await within(latest).findByRole('heading', { level: 3, name: 'Ledgerly' })).toBeTruthy()
    expect(within(latest).getByRole('link', { name: /View all listings/ }).getAttribute('href')).toBe('/listings')
    expect(requests.map((request) => request.path)).toEqual(['/listings?page=0&size=6'])
  })

  it('searches the marketplace', async () => {
    mockApi({
      [LATEST]: () => json(pageOf([], { size: 6 })),
      'GET /listings?search=invoice&page=0&size=12': () => json(pageOf([listingCard()])),
    })
    const router = renderApp('/')

    fireEvent.change(screen.getByRole('searchbox', { name: 'Search listings' }), { target: { value: ' invoice ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Search' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/listings'))
    expect(router.state.location.search).toBe('?search=invoice')
    expect(await screen.findByRole('heading', { level: 3, name: 'Ledgerly' })).toBeTruthy()
  })

  it('links to the marketplace filtered by type', async () => {
    mockApi({ [LATEST]: () => json(pageOf([], { size: 6 })) })
    renderApp('/')

    const shortcuts = within(screen.getByRole('navigation', { name: 'Browse by type' }))
    expect(shortcuts.getAllByRole('link').map((link) => [link.textContent, link.getAttribute('href')])).toEqual([
      ['Idea', '/listings?assetType=IDEA'],
      ['Project', '/listings?assetType=PROJECT'],
      ['MVP', '/listings?assetType=MVP'],
      ['Startup', '/listings?assetType=STARTUP'],
    ])
    expect(await screen.findByRole('heading', { name: 'No listings yet' })).toBeTruthy()
  })

  it('shows an error with a retry when the latest listings fail to load', async () => {
    const { handlers } = mockApi({ [LATEST]: () => problem(500, 'An unexpected error occurred.') })
    renderApp('/')

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain("We couldn't load the latest listings")
    expect(screen.getByRole('heading', { level: 1, name: 'Conflux' })).toBeTruthy()

    handlers[LATEST] = () => json(pageOf([listingCard()], { size: 6 }))
    fireEvent.click(within(alert).getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { level: 3, name: 'Ledgerly' })).toBeTruthy()
  })
})
