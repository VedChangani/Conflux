import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useAuth } from '../auth/useAuth'
import { savedApi } from './savedApi'
import { SavedListingsContext, type SavedListingsContextValue } from './savedContext'

const LOOKUP_PAGE_SIZE = 50
const LOOKUP_MAX_PAGES = 20

interface SessionState {
  accountId: number
  lookup: 'idle' | 'loading' | 'done' | 'failed'
  found: ReadonlySet<number>
  complete: boolean
  changes: ReadonlyMap<number, boolean>
}

function newSession(accountId: number): SessionState {
  return { accountId, lookup: 'idle', found: new Set(), complete: false, changes: new Map() }
}

async function lookUpSavedIds(signal: AbortSignal): Promise<{ ids: Set<number>; complete: boolean }> {
  const ids = new Set<number>()
  for (let page = 0; page < LOOKUP_MAX_PAGES; page++) {
    const result = await savedApi.list(page, LOOKUP_PAGE_SIZE, signal)
    result.content.forEach((listing) => ids.add(listing.id))
    if (result.last || result.content.length === 0) {
      return { ids, complete: true }
    }
  }
  return { ids, complete: false }
}

export function SavedListingsProvider({ children }: { children: ReactNode }) {
  const { status, account } = useAuth()
  const accountId = status === 'authenticated' && account ? account.id : null
  const [state, setState] = useState<SessionState | null>(null)
  const inFlight = useRef(new Map<string, { saved: boolean; request: Promise<void> }>())

  if (accountId === null && state !== null) {
    setState(null)
  }
  const session = state !== null && state.accountId === accountId ? state : null

  const ensureLoaded = useCallback(() => {
    if (accountId === null) {
      return
    }
    setState((previous) => {
      const base = previous?.accountId === accountId ? previous : newSession(accountId)
      return base.lookup === 'idle' ? { ...base, lookup: 'loading' } : base
    })
  }, [accountId])

  const lookupRunning = session?.lookup === 'loading'
  useEffect(() => {
    if (!lookupRunning || accountId === null) {
      return
    }
    const controller = new AbortController()
    const settle = (changes: Partial<SessionState>) => {
      if (!controller.signal.aborted) {
        setState((previous) => (previous?.accountId === accountId ? { ...previous, ...changes } : previous))
      }
    }
    lookUpSavedIds(controller.signal).then(
      ({ ids, complete }) => settle({ lookup: 'done', found: ids, complete }),
      () => settle({ lookup: 'failed' }),
    )
    return () => controller.abort()
  }, [lookupRunning, accountId])

  const setSaved = useCallback(
    (listingId: number, saved: boolean): Promise<void> => {
      if (accountId === null) {
        return Promise.reject(new Error('Saving listings requires a signed-in account.'))
      }
      const key = `${accountId}:${listingId}`
      const running = inFlight.current.get(key)
      if (running?.saved === saved) {
        return running.request
      }
      const send = () => (saved ? savedApi.save(listingId) : savedApi.unsave(listingId))
      const request = (running ? running.request.catch(() => undefined).then(send) : send()).then(() => {
        setState((previous) => {
          const base = previous?.accountId === accountId ? previous : newSession(accountId)
          return { ...base, changes: new Map(base.changes).set(listingId, saved) }
        })
      })
      const entry = { saved, request }
      inFlight.current.set(key, entry)
      const forget = () => {
        if (inFlight.current.get(key) === entry) {
          inFlight.current.delete(key)
        }
      }
      request.then(forget, forget)
      return request
    },
    [accountId],
  )

  const value = useMemo<SavedListingsContextValue>(
    () => ({
      isSaved: (listingId) => {
        if (session === null) {
          return undefined
        }
        const change = session.changes.get(listingId)
        if (change !== undefined) {
          return change
        }
        if (session.found.has(listingId)) {
          return true
        }
        return session.lookup === 'done' && session.complete ? false : undefined
      },
      checking: accountId !== null && (session === null || session.lookup === 'idle' || session.lookup === 'loading'),
      ensureLoaded,
      setSaved,
    }),
    [accountId, session, ensureLoaded, setSaved],
  )

  return <SavedListingsContext value={value}>{children}</SavedListingsContext>
}
