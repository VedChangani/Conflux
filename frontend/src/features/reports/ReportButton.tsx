import { useRef, useState } from 'react'
import { Button } from '../../components/Button'
import { useAuth } from '../auth/useAuth'
import { useLoginRedirect } from '../auth/useLoginRedirect'
import { ReportDialog } from './ReportDialog'
import { isReportableTarget } from './reportValidation'
import type { ReportTarget } from './types'

interface ReportButtonProps {
  target: ReportTarget
  label?: string
  accessibleLabel?: string
  compact?: boolean
  className?: string
}

export function ReportButton({ target, label = 'Report', accessibleLabel, compact = false, className }: ReportButtonProps) {
  const { status } = useAuth()
  const redirectToLogin = useLoginRedirect()
  const [open, setOpen] = useState(false)
  const buttonRef = useRef<HTMLButtonElement>(null)

  if (!isReportableTarget(target)) {
    return null
  }

  const authenticated = status === 'authenticated'
  const classes = ['report-button', compact ? 'report-button-compact' : 'button-small', className].filter(Boolean).join(' ')

  return (
    <>
      <Button
        ref={buttonRef}
        variant="ghost"
        className={classes}
        aria-label={accessibleLabel}
        aria-haspopup={authenticated ? 'dialog' : undefined}
        onClick={() => (authenticated ? setOpen(true) : redirectToLogin())}
      >
        <svg className="report-icon" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
          <path
            d="M3.5 14V2.5M3.5 3h8l-1.75 3 1.75 3h-8"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
        {label}
      </Button>
      {open && <ReportDialog target={target} onClose={() => setOpen(false)} returnFocusRef={buttonRef} />}
    </>
  )
}
