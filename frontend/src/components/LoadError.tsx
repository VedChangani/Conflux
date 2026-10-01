import { loadErrorMessage } from '../lib/errors'
import { Button } from './Button'
import { ErrorMessage } from './ErrorMessage'

interface LoadErrorProps {
  /** What failed, e.g. "We couldn’t load your requests". */
  title: string
  error: unknown
  onRetry: () => void
}

/**
 * The standard "this failed to load" alert: a title, a message written by the app (never
 * the backend's wording) and a Try again button that repeats the request.
 */
export function LoadError({ title, error, onRetry }: LoadErrorProps) {
  return (
    <ErrorMessage title={title} message={loadErrorMessage(error)}>
      <div className="button-row">
        <Button variant="secondary" onClick={onRetry}>
          Try again
        </Button>
      </div>
    </ErrorMessage>
  )
}
