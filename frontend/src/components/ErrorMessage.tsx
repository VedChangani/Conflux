import type { ReactNode } from 'react'

interface ErrorMessageProps {
  title?: string
  message?: string
  children?: ReactNode
}

export function ErrorMessage({
  title = 'Something went wrong',
  message = 'Please try again.',
  children,
}: ErrorMessageProps) {
  return (
    <div className="error-message" role="alert">
      <strong>{title}</strong>
      <p>{message}</p>
      {children}
    </div>
  )
}
