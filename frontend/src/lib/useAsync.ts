import { useCallback, useEffect, useState } from 'react'

export type AsyncState<T> =
  | { status: 'loading'; data: undefined; error: undefined; previousData: T | undefined }
  | { status: 'success'; data: T; error: undefined; previousData: undefined }
  | { status: 'error'; data: undefined; error: unknown; previousData: undefined }

interface Settled<T> {
  load: (signal: AbortSignal) => Promise<T>
  attempt: number
  ok: boolean
  data?: T
  error?: unknown
}

export function useAsync<T>(load: (signal: AbortSignal) => Promise<T>): AsyncState<T> & { retry: () => void } {
  const [attempt, setAttempt] = useState(0)
  const [settled, setSettled] = useState<Settled<T> | null>(null)
  const [lastData, setLastData] = useState<T | undefined>(undefined)

  useEffect(() => {
    const controller = new AbortController()
    load(controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) {
          setSettled({ load, attempt, ok: true, data })
          setLastData(data)
        }
      },
      (error: unknown) => {
        if (!controller.signal.aborted) {
          setSettled({ load, attempt, ok: false, error })
        }
      },
    )
    return () => controller.abort()
  }, [load, attempt])

  const retry = useCallback(() => setAttempt((current) => current + 1), [])

  const current = settled !== null && settled.load === load && settled.attempt === attempt
  if (!current) {
    return { status: 'loading', data: undefined, error: undefined, previousData: lastData, retry }
  }
  if (settled.ok) {
    return { status: 'success', data: settled.data as T, error: undefined, previousData: undefined, retry }
  }
  return { status: 'error', data: undefined, error: settled.error, previousData: undefined, retry }
}
