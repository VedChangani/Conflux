import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { setAccessToken } from '../auth/tokenStorage'
import { ACCOUNT, json, mockApi, type RecordedRequest } from '../../test/api'
import { listingDetail, pageOf } from '../../test/listings'
import { conversation, messageFromBob, messageFromMe } from '../../test/messages'
import { aliceProfile, ownProfile } from '../../test/profile'
import { renderApp } from '../../test/renderApp'

const created = (targetType: string, targetId: number) =>
  json({ id: 1, targetType, targetId, reason: 'SPAM', status: 'OPEN', createdAt: '2026-03-05T10:00:00Z' }, 201)
const reportBodies = (requests: RecordedRequest[]) =>
  requests.filter((request) => request.method === 'POST' && request.path === '/reports').map((request) => request.body)

function signedIn(handlers: Parameters<typeof mockApi>[0]) {
  setAccessToken('stored-token')
  return mockApi({ 'GET /auth/me': () => json(ACCOUNT), ...handlers })
}

async function reportAs(reason: RegExp) {
  const dialog = within(await screen.findByRole('dialog'))
  fireEvent.click(dialog.getByRole('radio', { name: reason }))
  fireEvent.click(dialog.getByRole('button', { name: 'Submit report' }))
  expect(await dialog.findByText('Report submitted.')).toBeTruthy()
  return dialog
}

describe('Reporting a profile', () => {
  it('reports the user shown, by their id, from next to the profile details', async () => {
    const { requests } = signedIn({ 'GET /users/alice': () => json(aliceProfile()), 'POST /reports': () => created('USER', 7) })
    renderApp('/users/alice')

    const button = await screen.findByRole('button', { name: 'Report Alice Anders' })
    expect(button.textContent).toBe('Report')
    expect(button.closest('.profile-actions')).toBeTruthy()
    fireEvent.click(button)

    const dialog = within(screen.getByRole('dialog', { name: 'Report profile' }))
    expect(dialog.getByText('Alice Anders (@alice)')).toBeTruthy()
    await reportAs(/^Harassment/)

    expect(reportBodies(requests)).toEqual([{ targetType: 'USER', targetId: 7, reason: 'HARASSMENT', details: null }])
    // The id is sent, never shown.
    expect(document.querySelector('.profile')?.textContent).not.toMatch(/\b7\b/)
  })

  it('is not offered on the user’s own public profile', async () => {
    signedIn({ 'GET /users/ada': () => json(ownProfile()) })
    renderApp('/users/ada')

    expect(await screen.findByRole('link', { name: 'Edit your profile' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: /^Report/ })).toBeNull()
  })
})

describe('Reporting a listing', () => {
  it('reports the listing from its summary panel, under the main actions', async () => {
    const { requests } = signedIn({
      'GET /listings/ledgerly': () => json(listingDetail()),
      'POST /reports': () => created('LISTING', 1),
    })
    renderApp('/listings/ledgerly')

    const panel = within(await screen.findByRole('complementary', { name: 'Listing summary' }))
    const actions = await panel.findAllByRole('button')
    expect(actions.map((action) => action.textContent)).toEqual(['Express interest', 'Save', 'Report listing'])

    fireEvent.click(panel.getByRole('button', { name: 'Report listing' }))
    await reportAs(/^Scam or fraud/)

    expect(reportBodies(requests)).toEqual([{ targetType: 'LISTING', targetId: 1, reason: 'SCAM_OR_FRAUD', details: null }])
  })

  it('is not offered to the listing’s owner', async () => {
    signedIn({
      'GET /listings/ledgerly': () =>
        json(listingDetail({ owner: { id: ACCOUNT.id, username: ACCOUNT.username, displayName: ACCOUNT.displayName } })),
    })
    renderApp('/listings/ledgerly')

    expect(await screen.findByText('This is your listing.')).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Report listing' })).toBeNull()
  })
})

describe('Reporting a message', () => {
  it('offers a per-message action only on the other person’s messages', async () => {
    const long = `Send the deposit to my personal account today and I will transfer the code repository right after. ${'x'.repeat(50)}`
    const { requests } = signedIn({
      'GET /conversations?page=0&size=20': () => json(pageOf([conversation()], { size: 20 })),
      'GET /conversations/31': () => json(conversation()),
      'GET /conversations/31/messages?page=0&size=50': () =>
        json(pageOf([messageFromBob({ content: long }), messageFromMe()], { size: 50 })),
      'POST /reports': () => created('MESSAGE', 501),
    })
    renderApp('/messages/31')

    const thread = within(await screen.findByRole('list', { name: 'Messages, newest first' }))
    const [fromBob, fromMe] = await thread.findAllByRole('listitem')
    expect(within(fromMe).queryByRole('button', { name: /Report/ })).toBeNull()
    const button = within(fromBob).getByRole('button', { name: 'Report message from Bob Brown' })
    expect(button.textContent).toBe('Report')

    fireEvent.click(button)

    const dialog = within(screen.getByRole('dialog', { name: 'Report message' }))
    // An excerpt identifies the message; the full text stays in the thread.
    const description = dialog.getByText(/^Message from Bob Brown: “Send the deposit/)
    expect(description.textContent?.endsWith('…”')).toBe(true)
    fireEvent.change(dialog.getByLabelText('Details (optional)'), { target: { value: ' Asked me to pay outside Conflux. ' } })
    await reportAs(/^Scam or fraud/)

    expect(reportBodies(requests)).toEqual([
      { targetType: 'MESSAGE', targetId: 501, reason: 'SCAM_OR_FRAUD', details: 'Asked me to pay outside Conflux.' },
    ])
    // Reporting does not touch the conversation: nothing is reloaded or removed.
    fireEvent.click(screen.getByRole('button', { name: 'Done' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(thread.getAllByRole('listitem')).toHaveLength(2)
    expect(requests.filter((request) => request.path === '/conversations/31/messages?page=0&size=50')).toHaveLength(1)
  })
})
