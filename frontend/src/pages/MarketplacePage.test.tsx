import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { setAccessToken } from '../features/auth/tokenStorage'
import { ACCOUNT, deferred, json, mockApi, problem } from '../test/api'
import { listingCard, listingDetail, pageOf } from '../test/listings'
import { renderApp } from '../test/renderApp'

const LEDGERLY = listingCard()
const PAIRWISE = listingCard({
  id: 2,
  slug: 'pairwise',
  title: 'Pairwise',
  shortPitch: 'Find a technical co-founder.',
  assetType: 'IDEA',
  marketplaceMode: 'COLLABORATE',
  category: 'SOCIAL',
  stage: 'CONCEPT',
  askingPrice: null,
  currency: null,
  publishedAt: '2026-02-03T12:00:00Z',
  owner: { id: 8, username: 'bob', displayName: 'Bob Brown' },
})

const DEFAULT_QUERY = 'GET /listings?page=0&size=12'

const results = () => screen.getByRole('region', { name: /listings|results for/i })
const status = () => within(results()).getByRole('status')
const select = (label: string) => screen.getByLabelText(label) as HTMLSelectElement
const searchInput = () => screen.getByRole('searchbox', { name: 'Search listings' }) as HTMLInputElement

function choose(label: string, value: string) {
  fireEvent.change(select(label), { target: { value } })
}

function search(text: string) {
  fireEvent.change(searchInput(), { target: { value: text } })
  fireEvent.click(screen.getByRole('button', { name: 'Search' }))
}

describe('Marketplace discovery', () => {
  it('renders the marketplace route and loads the first page', async () => {
    const { requests } = mockApi({ [DEFAULT_QUERY]: () => json(pageOf([LEDGERLY, PAIRWISE])) })
    renderApp('/listings')

    expect(screen.getByRole('heading', { level: 1, name: 'Browse listings' })).toBeTruthy()
    expect(within(screen.getByRole('navigation', { name: 'Main' })).getByRole('link', { name: 'Browse' })).toBeTruthy()
    expect(await screen.findByRole('heading', { level: 3, name: 'Ledgerly' })).toBeTruthy()
    expect(requests.map((request) => `${request.method} ${request.path}`)).toEqual([DEFAULT_QUERY])
  })

  it('shows a loading state until the results arrive', async () => {
    const response = deferred<Response>()
    mockApi({ [DEFAULT_QUERY]: () => response.promise })
    renderApp('/listings')

    expect(status().textContent).toBe('Loading listings…')
    expect(results().getAttribute('aria-busy')).toBe('true')
    expect(screen.queryByRole('heading', { level: 3 })).toBeNull()

    response.resolve(json(pageOf([LEDGERLY])))

    expect(await screen.findByRole('heading', { level: 3, name: 'Ledgerly' })).toBeTruthy()
    expect(results().getAttribute('aria-busy')).toBe('false')
    expect(status().textContent).toBe('Showing 1–1 of 1 listing')
  })

  it('shows the public card fields and nothing private', async () => {
    mockApi({ [DEFAULT_QUERY]: () => json(pageOf([LEDGERLY, PAIRWISE])) })
    renderApp('/listings')

    const [ledgerly, pairwise] = await screen.findAllByRole('article')
    const card = within(ledgerly)
    expect(card.getByRole('heading', { name: 'Ledgerly' })).toBeTruthy()
    expect(card.getByText('Close the books faster.')).toBeTruthy()
    expect(card.getByText('MVP')).toBeTruthy()
    expect(card.getByText('Acquisition')).toBeTruthy()
    expect(card.getByText('Fintech')).toBeTruthy()
    expect(card.getByText('Prototype')).toBeTruthy()
    expect(card.getByText('$25,000')).toBeTruthy()
    expect(card.getByText('Alice Anders')).toBeTruthy()
    expect(card.getByText('Jan 15, 2026').getAttribute('datetime')).toBe('2026-01-15T12:00:00Z')
    expect(card.getByRole('link', { name: 'Ledgerly' }).getAttribute('href')).toBe('/listings/ledgerly')
    // One link per card keeps each card a single tab stop.
    expect(card.getAllByRole('link')).toHaveLength(1)

    const other = within(pairwise)
    expect(other.getByText('Idea')).toBeTruthy()
    expect(other.getByText('Collaboration')).toBeTruthy()
    expect(other.getByText('Open to collaborate')).toBeTruthy()
    expect(other.queryByText(/\$/)).toBeNull()

    expect(status().textContent).toBe('Showing 1–2 of 2 listings')
    // The owner's username is for the detail page; ids are never rendered.
    expect(results().textContent).not.toContain('@alice')
  })

  it('never sends the stored token to the public endpoint', async () => {
    setAccessToken('stored-token')
    const { requests } = mockApi({
      'GET /auth/me': () => json(ACCOUNT),
      [DEFAULT_QUERY]: () => json(pageOf([LEDGERLY])),
    })
    renderApp('/listings')

    await screen.findByRole('heading', { level: 3, name: 'Ledgerly' })
    const discovery = requests.find((request) => request.path.startsWith('/listings'))
    expect(discovery?.headers.has('Authorization')).toBe(false)
  })

  it('searches through the backend and keeps the term in the URL', async () => {
    const { requests } = mockApi({
      [DEFAULT_QUERY]: () => json(pageOf([LEDGERLY, PAIRWISE])),
      'GET /listings?search=ledger&page=0&size=12': () => json(pageOf([LEDGERLY])),
    })
    const router = renderApp('/listings')
    await screen.findByRole('heading', { level: 3, name: 'Pairwise' })

    search('  ledger  ')

    await waitFor(() => expect(router.state.location.search).toBe('?search=ledger'))
    await waitFor(() => expect(screen.queryByRole('heading', { level: 3, name: 'Pairwise' })).toBeNull())
    expect(screen.getByRole('heading', { level: 2, name: 'Results for “ledger”' })).toBeTruthy()
    expect(searchInput().value).toBe('ledger')
    expect(requests.at(-1)?.path).toBe('/listings?search=ledger&page=0&size=12')
    expect(screen.getByRole('button', { name: 'Remove search filter: “ledger”' })).toBeTruthy()
  })

  it('clears the search with the clear button', async () => {
    mockApi({
      [DEFAULT_QUERY]: () => json(pageOf([LEDGERLY, PAIRWISE])),
      'GET /listings?search=ledger&page=0&size=12': () => json(pageOf([LEDGERLY])),
    })
    const router = renderApp('/listings?search=ledger')
    expect(searchInput().value).toBe('ledger')
    await screen.findByRole('heading', { level: 3, name: 'Ledgerly' })

    fireEvent.click(screen.getByRole('button', { name: 'Clear search' }))

    await waitFor(() => expect(router.state.location.search).toBe(''))
    expect(searchInput().value).toBe('')
    expect(await screen.findByRole('heading', { level: 3, name: 'Pairwise' })).toBeTruthy()
  })

  it('combines filters, resets to the first page and shows them as removable chips', async () => {
    const { requests } = mockApi({
      'GET /listings?search=ai&page=2&size=12': () => json(pageOf([], { page: 2, totalElements: 30 })),
      'GET /listings?search=ai&assetType=MVP&page=0&size=12': () => json(pageOf([LEDGERLY])),
      'GET /listings?search=ai&assetType=MVP&category=FINTECH&page=0&size=12': () => json(pageOf([LEDGERLY])),
      'GET /listings?search=ai&assetType=MVP&marketplaceMode=ACQUIRE&category=FINTECH&stage=PROTOTYPE&page=0&size=12':
        () => json(pageOf([LEDGERLY])),
      'GET /listings?search=ai&assetType=MVP&marketplaceMode=ACQUIRE&stage=PROTOTYPE&page=0&size=12': () =>
        json(pageOf([LEDGERLY])),
    })
    const router = renderApp('/listings?search=ai&page=3')
    await waitFor(() => expect(requests).toHaveLength(1))

    choose('Type', 'MVP')
    await waitFor(() => expect(router.state.location.search).toBe('?search=ai&assetType=MVP'))

    choose('Category', 'FINTECH')
    await waitFor(() => expect(router.state.location.search).toContain('category=FINTECH'))
    choose('Opportunity', 'ACQUIRE')
    await waitFor(() => expect(router.state.location.search).toContain('marketplaceMode=ACQUIRE'))
    choose('Stage', 'PROTOTYPE')

    await waitFor(() =>
      expect(requests.at(-1)?.path).toBe(
        '/listings?search=ai&assetType=MVP&marketplaceMode=ACQUIRE&category=FINTECH&stage=PROTOTYPE&page=0&size=12',
      ),
    )
    expect(select('Type').value).toBe('MVP')
    expect(select('Category').value).toBe('FINTECH')

    const chips = within(screen.getByRole('region', { name: 'Active filters' }))
    expect(chips.getAllByRole('button', { name: /^Remove / })).toHaveLength(5)

    fireEvent.click(chips.getByRole('button', { name: 'Remove category filter: Fintech' }))

    await waitFor(() =>
      expect(requests.at(-1)?.path).toBe(
        '/listings?search=ai&assetType=MVP&marketplaceMode=ACQUIRE&stage=PROTOTYPE&page=0&size=12',
      ),
    )
    expect(select('Category').value).toBe('')
  })

  it('clears all filters but keeps the chosen order', async () => {
    mockApi({
      'GET /listings?assetType=IDEA&stage=CONCEPT&sort=OLDEST&page=0&size=12': () => json(pageOf([PAIRWISE])),
      'GET /listings?sort=OLDEST&page=0&size=12': () => json(pageOf([LEDGERLY, PAIRWISE])),
    })
    const router = renderApp('/listings?assetType=IDEA&stage=CONCEPT&sort=OLDEST')
    await screen.findByRole('heading', { level: 3, name: 'Pairwise' })

    fireEvent.click(screen.getByRole('button', { name: 'Clear all' }))

    await waitFor(() => expect(router.state.location.search).toBe('?sort=OLDEST'))
    expect(await screen.findByRole('heading', { level: 3, name: 'Ledgerly' })).toBeTruthy()
    expect(screen.queryByRole('region', { name: 'Active filters' })).toBeNull()
    expect(select('Sort by').value).toBe('OLDEST')
  })

  it('sorts with the backend-supported orderings', async () => {
    const { requests } = mockApi({
      [DEFAULT_QUERY]: () => json(pageOf([LEDGERLY, PAIRWISE])),
      'GET /listings?sort=PRICE_LOW&page=0&size=12': () => json(pageOf([LEDGERLY, PAIRWISE])),
    })
    const router = renderApp('/listings?page=2')

    const options = [...select('Sort by').options].map((option) => [option.value, option.text])
    expect(options).toEqual([
      ['NEWEST', 'Newest'],
      ['OLDEST', 'Oldest'],
      ['UPDATED', 'Recently updated'],
      ['PRICE_LOW', 'Price: low to high'],
      ['PRICE_HIGH', 'Price: high to low'],
    ])
    expect(select('Sort by').value).toBe('NEWEST')

    choose('Sort by', 'PRICE_LOW')

    await waitFor(() => expect(router.state.location.search).toBe('?sort=PRICE_LOW'))
    await waitFor(() => expect(requests.at(-1)?.path).toBe('/listings?sort=PRICE_LOW&page=0&size=12'))

    // The default order is not written to the URL.
    choose('Sort by', 'NEWEST')
    await waitFor(() => expect(router.state.location.search).toBe(''))
  })

  it('changes the page size within the backend limit', async () => {
    const { requests } = mockApi({
      [DEFAULT_QUERY]: () => json(pageOf([LEDGERLY])),
      'GET /listings?page=0&size=48': () => json(pageOf([LEDGERLY], { size: 48 })),
    })
    const router = renderApp('/listings')
    await screen.findByRole('heading', { level: 3, name: 'Ledgerly' })

    expect([...select('Per page').options].map((option) => Number(option.value))).toEqual([12, 24, 48])
    choose('Per page', '48')

    await waitFor(() => expect(router.state.location.search).toBe('?size=48'))
    await waitFor(() => expect(requests.at(-1)?.path).toBe('/listings?page=0&size=48'))
  })

  it('paginates with the backend page metadata', async () => {
    const pageOne = Array.from({ length: 12 }, (_, i) => listingCard({ id: i + 1, slug: `a-${i}`, title: `A${i}` }))
    const pageTwo = Array.from({ length: 12 }, (_, i) => listingCard({ id: i + 20, slug: `b-${i}`, title: `B${i}` }))
    const pageThree = Array.from({ length: 6 }, (_, i) => listingCard({ id: i + 40, slug: `c-${i}`, title: `C${i}` }))
    const { requests } = mockApi({
      'GET /listings?category=FINTECH&page=0&size=12': () => json(pageOf(pageOne, { page: 0, totalElements: 30 })),
      'GET /listings?category=FINTECH&page=1&size=12': () => json(pageOf(pageTwo, { page: 1, totalElements: 30 })),
      'GET /listings?category=FINTECH&page=2&size=12': () => json(pageOf(pageThree, { page: 2, totalElements: 30 })),
    })
    const router = renderApp('/listings?category=FINTECH')
    await screen.findByRole('heading', { level: 3, name: 'A0' })

    const pagination = () => within(screen.getByRole('navigation', { name: 'Pagination' }))
    expect(pagination().getByText('Previous').closest('[aria-disabled]')?.getAttribute('aria-disabled')).toBe('true')
    expect(pagination().queryByRole('link', { name: /Previous/ })).toBeNull()
    expect(pagination().getByText('Page 1 of 3')).toBeTruthy()
    expect(pagination().getByText('1').closest('[aria-current]')?.getAttribute('aria-current')).toBe('page')
    expect(status().textContent).toBe('Showing 1–12 of 30 listings')

    fireEvent.click(pagination().getByRole('link', { name: /Next/ }))

    expect(await screen.findByRole('heading', { level: 3, name: 'B0' })).toBeTruthy()
    expect(router.state.location.search).toBe('?category=FINTECH&page=2')
    expect(requests.at(-1)?.path).toBe('/listings?category=FINTECH&page=1&size=12')
    expect(status().textContent).toBe('Showing 13–24 of 30 listings')
    // Focus returns to the top of the results for keyboard and screen-reader users.
    expect(document.activeElement).toBe(screen.getByRole('heading', { level: 2 }))
    expect(pagination().getByRole('link', { name: /Previous/ }).getAttribute('href')).toBe('/listings?category=FINTECH')

    fireEvent.click(pagination().getByRole('link', { name: 'Page 3' }))

    expect(await screen.findByRole('heading', { level: 3, name: 'C0' })).toBeTruthy()
    expect(status().textContent).toBe('Showing 25–30 of 30 listings')
    expect(pagination().queryByRole('link', { name: /Next/ })).toBeNull()
    expect(pagination().getByText('Next').closest('[aria-disabled]')?.getAttribute('aria-disabled')).toBe('true')
  })

  it('hides pagination when everything fits on one page', async () => {
    mockApi({ [DEFAULT_QUERY]: () => json(pageOf([LEDGERLY, PAIRWISE])) })
    renderApp('/listings')
    await screen.findByRole('heading', { level: 3, name: 'Ledgerly' })

    expect(screen.queryByRole('navigation', { name: 'Pagination' })).toBeNull()
  })

  it('handles a page past the end of the results', async () => {
    mockApi({ 'GET /listings?page=8&size=12': () => json(pageOf([], { page: 8, totalElements: 30 })) })
    renderApp('/listings?page=9')

    expect(await screen.findByRole('heading', { name: 'There is no page 9' })).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Go to page 3' }).getAttribute('href')).toBe('/listings?page=3')
    expect(status().textContent).toBe('30 listings')
  })

  it('shows an empty state with a reset when nothing matches', async () => {
    mockApi({
      'GET /listings?search=zzz&category=AI&page=0&size=12': () => json(pageOf([])),
      [DEFAULT_QUERY]: () => json(pageOf([LEDGERLY])),
    })
    const router = renderApp('/listings?search=zzz&category=AI')

    expect(await screen.findByRole('heading', { name: 'No listings match your search' })).toBeTruthy()
    expect(status().textContent).toBe('No listings found')
    expect(screen.queryByRole('navigation', { name: 'Pagination' })).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: 'Clear search and filters' }))

    await waitFor(() => expect(router.state.location.search).toBe(''))
    expect(await screen.findByRole('heading', { level: 3, name: 'Ledgerly' })).toBeTruthy()
  })

  it('shows a different empty state when the marketplace has no listings', async () => {
    mockApi({ [DEFAULT_QUERY]: () => json(pageOf([])) })
    renderApp('/listings')

    expect(await screen.findByRole('heading', { name: 'No listings yet' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Clear search and filters' })).toBeNull()
  })

  it('shows a server error with a working retry', async () => {
    const { handlers } = mockApi({ [DEFAULT_QUERY]: () => problem(500, 'An unexpected error occurred.') })
    renderApp('/listings')

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain("We couldn't load listings")
    expect(alert.textContent).toContain('An unexpected error occurred.')

    handlers[DEFAULT_QUERY] = () => json(pageOf([LEDGERLY]))
    fireEvent.click(within(alert).getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { level: 3, name: 'Ledgerly' })).toBeTruthy()
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('passes unknown values to the backend and shows its validation error', async () => {
    const { requests } = mockApi({
      'GET /listings?assetType=APP&page=0&size=12': () =>
        problem(400, "Failed to convert 'assetType' with value: 'APP'"),
      [DEFAULT_QUERY]: () => json(pageOf([LEDGERLY])),
    })
    const router = renderApp('/listings?assetType=APP')

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain("Some search options aren't valid")
    expect(alert.textContent).toContain("Failed to convert 'assetType' with value: 'APP'")
    expect(requests[0].path).toBe('/listings?assetType=APP&page=0&size=12')
    // The unknown value stays visible in its control rather than silently changing.
    expect(select('Type').value).toBe('APP')

    fireEvent.click(within(alert).getByRole('link', { name: 'Reset search' }))

    await waitFor(() => expect(router.state.location.search).toBe(''))
    expect(await screen.findByRole('heading', { level: 3, name: 'Ledgerly' })).toBeTruthy()
  })

  it('shows field-level validation errors from the backend', async () => {
    mockApi({
      'GET /listings?page=0&size=99': () =>
        problem(400, 'Invalid request content.', [{ field: 'size', message: 'must be less than or equal to 50' }]),
    })
    renderApp('/listings?size=99')

    const alert = await screen.findByRole('alert')
    expect(alert.textContent).toContain('Invalid request content.')
    expect(within(alert).getByRole('listitem').textContent).toBe('size must be less than or equal to 50')
  })

  it('restores the whole query from a shared URL', async () => {
    const { requests } = mockApi({
      'GET /listings?search=invoice&marketplaceMode=ACQUIRE&category=FINTECH&sort=PRICE_HIGH&page=1&size=24': () =>
        json(pageOf([LEDGERLY], { page: 1, size: 24, totalElements: 25 })),
    })
    renderApp('/listings?category=FINTECH&search=invoice&marketplaceMode=ACQUIRE&sort=PRICE_HIGH&page=2&size=24')

    await screen.findByRole('heading', { level: 3, name: 'Ledgerly' })
    expect(requests).toHaveLength(1)
    expect(searchInput().value).toBe('invoice')
    expect(select('Opportunity').value).toBe('ACQUIRE')
    expect(select('Category').value).toBe('FINTECH')
    expect(select('Type').value).toBe('')
    expect(select('Sort by').value).toBe('PRICE_HIGH')
    expect(select('Per page').value).toBe('24')
    expect(status().textContent).toBe('Showing 25–25 of 25 listings')
  })

  it('follows browser back and forward through query changes', async () => {
    mockApi({
      [DEFAULT_QUERY]: () => json(pageOf([LEDGERLY, PAIRWISE])),
      'GET /listings?assetType=IDEA&page=0&size=12': () => json(pageOf([PAIRWISE])),
      'GET /listings?search=pair&assetType=IDEA&page=0&size=12': () => json(pageOf([PAIRWISE])),
    })
    const router = renderApp('/listings')
    await screen.findByRole('heading', { level: 3, name: 'Ledgerly' })

    choose('Type', 'IDEA')
    await waitFor(() => expect(screen.queryByRole('heading', { level: 3, name: 'Ledgerly' })).toBeNull())
    search('pair')
    await waitFor(() => expect(router.state.location.search).toBe('?assetType=IDEA&search=pair'))

    await router.navigate(-1)
    await waitFor(() => expect(searchInput().value).toBe(''))
    expect(select('Type').value).toBe('IDEA')

    await router.navigate(-1)
    expect(await screen.findByRole('heading', { level: 3, name: 'Ledgerly' })).toBeTruthy()
    expect(select('Type').value).toBe('')

    await router.navigate(1)
    await waitFor(() => expect(select('Type').value).toBe('IDEA'))
  })

  it('opens a listing and returns to the same results', async () => {
    mockApi({
      'GET /listings?category=FINTECH&sort=OLDEST&page=1&size=12': () =>
        json(pageOf([LEDGERLY], { page: 1, totalElements: 13 })),
      'GET /listings/ledgerly': () => json(listingDetail()),
    })
    const router = renderApp('/listings?category=FINTECH&sort=OLDEST&page=2')

    fireEvent.click(await screen.findByRole('link', { name: 'Ledgerly' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Ledgerly' })).toBeTruthy()
    expect(router.state.location.pathname).toBe('/listings/ledgerly')

    fireEvent.click(screen.getByRole('link', { name: /Back to results/ }))

    await screen.findByRole('heading', { level: 3, name: 'Ledgerly' })
    expect(router.state.location.pathname).toBe('/listings')
    expect(router.state.location.search).toBe('?category=FINTECH&sort=OLDEST&page=2')
    expect(select('Category').value).toBe('FINTECH')
    expect(select('Sort by').value).toBe('OLDEST')
  })
})

describe('Marketplace controls accessibility', () => {
  it('labels every control and exposes the mobile filter toggle state', async () => {
    mockApi({ 'GET /listings?stage=LIVE&page=0&size=12': () => json(pageOf([LEDGERLY])) })
    renderApp('/listings?stage=LIVE')
    await screen.findByRole('heading', { level: 3, name: 'Ledgerly' })

    expect(screen.getByRole('search')).toBeTruthy()
    expect(searchInput().maxLength).toBe(100)
    for (const label of ['Type', 'Opportunity', 'Category', 'Stage', 'Sort by', 'Per page']) {
      expect(select(label).tagName).toBe('SELECT')
    }
    expect(screen.getByRole('group', { name: 'Filter listings' })).toBeTruthy()

    const toggle = screen.getByRole('button', { name: /^Filters/ })
    expect(toggle.textContent).toBe('Filters1')
    expect(toggle.getAttribute('aria-expanded')).toBe('false')
    expect(toggle.getAttribute('aria-controls')).toBe(screen.getByRole('group', { name: 'Filter listings' }).id)

    fireEvent.click(toggle)
    expect(toggle.getAttribute('aria-expanded')).toBe('true')
  })

  it('uses friendly labels for enum options', async () => {
    mockApi({ [DEFAULT_QUERY]: () => json(pageOf([])) })
    renderApp('/listings')

    const categories = [...select('Category').options].map((option) => option.text)
    expect(categories).toContain('Developer tools')
    expect(categories).toContain('E-commerce')
    expect(categories[0]).toBe('All categories')
    expect([...select('Opportunity').options].map((option) => option.text)).toEqual([
      'Any opportunity',
      'Acquisition',
      'Collaboration',
    ])
  })
})
