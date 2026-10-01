import { useEffect, useId, useRef, useState, type FormEvent, type RefObject } from 'react'
import { Button } from '../../components/Button'
import { Dialog } from '../../components/Dialog'
import { TextAreaField } from '../../components/TextAreaField'
import { ApiError } from '../../services/apiClient'
import { useLoginRedirect } from '../auth/useLoginRedirect'
import { reportErrorOf, type ReportOutcomeError } from './reportErrors'
import { REASON_COPY } from './reasons'
import { reportsApi } from './reportsApi'
import {
  detailsError,
  isReportableTarget,
  serverReportErrors,
  toReportRequest,
  validateReport,
  type ReportDraft,
  type ReportFieldErrors,
} from './reportValidation'
import { REPORT_DETAILS_MAX_LENGTH, REPORT_REASONS, type ReportTarget } from './types'

const TITLES: Record<ReportTarget['type'], string> = {
  USER: 'Report profile',
  LISTING: 'Report listing',
  MESSAGE: 'Report message',
}

const numberFormat = new Intl.NumberFormat('en-US')

type Phase = 'form' | 'submitted' | 'closed-out'

interface ReportDialogProps {
  target: ReportTarget
  onClose: () => void
  returnFocusRef?: RefObject<HTMLElement | null>
}

export function ReportDialog({ target, onClose, returnFocusRef }: ReportDialogProps) {
  const redirectToLogin = useLoginRedirect()
  const [draft, setDraft] = useState<ReportDraft>({ reason: '', details: '' })
  const [errors, setErrors] = useState<ReportFieldErrors>({})
  const [outcome, setOutcome] = useState<ReportOutcomeError | null>(null)
  const [phase, setPhase] = useState<Phase>('form')
  const [submitting, setSubmitting] = useState(false)
  const submittingRef = useRef(false)
  const mounted = useRef(true)
  const doneRef = useRef<HTMLButtonElement>(null)
  const reasonsRef = useRef<HTMLFieldSetElement>(null)
  const id = useId()
  const reasonErrorId = `${id}-reason-error`
  const summaryId = `${id}-summary`

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  useEffect(() => {
    if (phase !== 'form') {
      doneRef.current?.focus()
    }
  }, [phase])

  function focusFirstProblem(fieldErrors: ReportFieldErrors) {
    if (fieldErrors.reason) {
      reasonsRef.current?.querySelector<HTMLInputElement>('input')?.focus()
    } else if (fieldErrors.details) {
      document.getElementById(`${id}-details`)?.querySelector('textarea')?.focus()
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submittingRef.current) {
      return
    }
    if (!isReportableTarget(target)) {
      setOutcome({ message: 'This can’t be reported.', final: true, tone: 'error' })
      setPhase('closed-out')
      return
    }
    const fieldErrors = validateReport(draft)
    if (fieldErrors.reason || fieldErrors.details || draft.reason === '') {
      setErrors(fieldErrors)
      setOutcome(null)
      focusFirstProblem(fieldErrors)
      return
    }
    submittingRef.current = true
    setSubmitting(true)
    setErrors({})
    setOutcome(null)
    try {
      await reportsApi.create(toReportRequest(target, draft.reason, draft.details))
      if (mounted.current) {
        setPhase('submitted')
      }
    } catch (caught) {
      if (!mounted.current) {
        return
      }
      if (caught instanceof ApiError && caught.status === 401) {
        onClose()
        redirectToLogin()
        return
      }
      const serverErrors = serverReportErrors(caught, draft)
      const result = reportErrorOf(caught, target.type, Boolean(serverErrors.reason || serverErrors.details))
      setErrors(serverErrors)
      setOutcome(result)
      if (result.final) {
        setPhase('closed-out')
      } else {
        focusFirstProblem(serverErrors)
      }
    } finally {
      submittingRef.current = false
      if (mounted.current) {
        setSubmitting(false)
      }
    }
  }

  const title = TITLES[target.type]

  if (phase === 'submitted') {
    return (
      <Dialog title={title} onClose={onClose} returnFocusRef={returnFocusRef}>
        <div className="report-result report-result-success" role="status">
          <p className="report-result-title">Report submitted.</p>
          <p>Thanks for letting us know. Our team will review it; nothing changes automatically in the meantime.</p>
        </div>
        <div className="dialog-actions">
          <Button ref={doneRef} onClick={onClose}>
            Done
          </Button>
        </div>
      </Dialog>
    )
  }

  if (phase === 'closed-out' && outcome) {
    return (
      <Dialog title={title} onClose={onClose} returnFocusRef={returnFocusRef}>
        <div
          className={outcome.tone === 'info' ? 'report-result' : 'report-result report-result-error'}
          role={outcome.tone === 'info' ? 'status' : 'alert'}
        >
          <p className="report-result-title">{outcome.message}</p>
        </div>
        <div className="dialog-actions">
          <Button ref={doneRef} variant="secondary" onClick={onClose}>
            Close
          </Button>
        </div>
      </Dialog>
    )
  }

  const detailsLeft = REPORT_DETAILS_MAX_LENGTH - draft.details.trim().length

  return (
    <Dialog title={title} onClose={onClose} returnFocusRef={returnFocusRef} dismissible={!submitting}>
      <p className="report-target">
        <span className="eyebrow">You’re reporting</span>
        <strong className="report-target-name">{target.description}</strong>
      </p>

      <form className="form report-form" onSubmit={handleSubmit} noValidate aria-busy={submitting}>
        {outcome && (
          <div id={summaryId} className="error-message" role="alert">
            <strong>Report not sent</strong>
            <p>{outcome.message}</p>
          </div>
        )}

        <fieldset
          ref={reasonsRef}
          className="report-reasons"
          aria-describedby={errors.reason ? reasonErrorId : undefined}
        >
          <legend className="field-label">Why are you reporting this?</legend>
          {errors.reason && (
            <p id={reasonErrorId} className="field-error" role="alert">
              {errors.reason}
            </p>
          )}
          {REPORT_REASONS.map((reason) => (
            <label key={reason} className="report-reason">
              <input
                type="radio"
                name="reason"
                value={reason}
                checked={draft.reason === reason}
                aria-invalid={errors.reason ? true : undefined}
                onChange={() => {
                  setDraft((current) => ({ ...current, reason }))
                  setErrors((current) => ({ ...current, reason: undefined }))
                }}
              />
              <span className="report-reason-text">
                <span className="report-reason-label">{REASON_COPY[reason].label}</span>
                <span className="report-reason-hint">{REASON_COPY[reason].hint}</span>
              </span>
            </label>
          ))}
        </fieldset>

        <div id={`${id}-details`}>
          <TextAreaField
            label="Details (optional)"
            name="details"
            rows={4}
            value={draft.details}
            error={errors.details}
            hint={
              detailsLeft >= 0
                ? `Anything that helps us review it. ${numberFormat.format(detailsLeft)} characters left.`
                : `${numberFormat.format(-detailsLeft)} characters over the ${numberFormat.format(REPORT_DETAILS_MAX_LENGTH)}-character limit.`
            }
            onChange={(event) => {
              const details = event.target.value
              setDraft((current) => ({ ...current, details }))
              setErrors((current) => (current.details ? { ...current, details: detailsError(details) } : current))
            }}
          />
        </div>

        <div className="dialog-actions">
          <Button variant="secondary" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button type="submit" loading={submitting} loadingText="Submitting…">
            Submit report
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
