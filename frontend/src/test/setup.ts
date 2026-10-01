import { cleanup, configure } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

// The default 1s for findBy*/waitFor is too tight when the whole suite runs in parallel.
configure({ asyncUtilTimeout: 3000 })

afterEach(() => {
  cleanup()
  window.localStorage.clear()
  vi.unstubAllEnvs()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})
