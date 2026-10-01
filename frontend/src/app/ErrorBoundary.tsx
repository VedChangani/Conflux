import { Component, type ReactNode } from 'react'
import { Button } from '../components/Button'
import { ErrorMessage } from '../components/ErrorMessage'

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  hasError: boolean
}

/**
 * Last-resort fallback so an unexpected rendering error outside the router never
 * leaves a blank screen. Errors inside routes are handled by {@link RouteError}.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true }
  }

  render() {
    if (this.state.hasError) {
      return (
        <main className="app-main">
          <ErrorMessage message="The page failed to load. Reloading usually fixes this.">
            <div className="button-row">
              <Button variant="secondary" onClick={() => window.location.reload()}>
                Reload
              </Button>
            </div>
          </ErrorMessage>
        </main>
      )
    }
    return this.props.children
  }
}
