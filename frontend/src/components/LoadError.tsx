import { loadErrorMessage } from '../lib/errors'
import { Button } from './Button'
import { ErrorMessage } from './ErrorMessage'

interface LoadErrorProps {
  title: string
  error: unknown
  onRetry: () => void
}

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
