import { useEffect, useRef, useState, type FormEvent, type ReactNode, type RefObject } from 'react'
import { Button } from '../../components/Button'
import { Dialog } from '../../components/Dialog'
import { TextAreaField } from '../../components/TextAreaField'
import { useAuth } from '../auth/useAuth'
import { adminActionError, noteLengthError, serverNoteError, type AdminActionError } from './adminErrors'
import { RESOLUTION_NOTE_MAX_LENGTH, type AdminAction } from './types'

const numberFormat = new Intl.NumberFormat('en-US')

interface ModerationDialogProps {
  action: AdminAction
  title: string
  /** What confirming will do. */
  children: ReactNode
  confirmLabel: string
  runningLabel: string
  /** Suspensions use the danger style; everything else the primary one. */
  danger?: boolean
  /** Offer the optional resolution note (report decisions only). */
  withNote?: boolean
  /** Runs the request; rejects with the API error. The note is passed as typed. */
  onConfirm: (note: string) => Promise<void>
  /** After a successful request. The caller closes the dialog. */
  onDone: () => void
  /**
   * The report or its target changed elsewhere (404/409): read it again. Called as the dialog
   * closes, so the explanation isn't swept away by the refreshed panel.
   */
  onStale: () => void
  onClose: () => void
  returnFocusRef?: RefObject<HTMLElement | null>
}

/**
 * The confirmation step for every moderation action: what will happen, an optional note,
 * and Confirm. One request at a time; while it runs the dialog can't be dismissed. Errors
 * that a retry can fix keep the form; the others replace it with the explanation.
 */
export function ModerationDialog({
  action,
  title,
  children,
  confirmLabel,
  runningLabel,
  danger = false,
  withNote = false,
  onConfirm,
  onDone,
  onStale,
  onClose,
  returnFocusRef,
}: ModerationDialogProps) {
  const { refreshAccount } = useAuth()
  const [note, setNote] = useState('')
  const [noteError, setNoteError] = useState<string | undefined>(undefined)
  const [error, setError] = useState<AdminActionError | null>(null)
  const [submitting, setSubmitting] = useState(false)
  // A ref as well as state: a second submit can arrive before the disabled button renders.
  const submittingRef = useRef(false)
  const mounted = useRef(true)
  const closeRef = useRef<HTMLButtonElement>(null)
  const noteRef = useRef<HTMLDivElement>(null)
  const stale = useRef(false)
  const final = error !== null && !error.retryable

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  // The form was replaced by a final explanation: focus its button.
  useEffect(() => {
    if (final) {
      closeRef.current?.focus()
    }
  }, [final])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submittingRef.current) {
      return
    }
    const lengthError = withNote ? noteLengthError(note) : undefined
    if (lengthError) {
      setNoteError(lengthError)
      noteRef.current?.querySelector('textarea')?.focus()
      return
    }
    submittingRef.current = true
    setSubmitting(true)
    setError(null)
    setNoteError(undefined)
    try {
      await onConfirm(note)
      if (mounted.current) {
        onDone()
      }
    } catch (caught) {
      if (!mounted.current) {
        return
      }
      const result = adminActionError(caught, action)
      if (result.kind === 'unauthorized') {
        // The session has ended; the protected route sends the user to log in.
        onClose()
        return
      }
      const fieldError = withNote ? serverNoteError(caught, note) : undefined
      if (fieldError) {
        setNoteError(fieldError)
        setError({ ...result, message: 'Please correct the note.', retryable: true })
        noteRef.current?.querySelector('textarea')?.focus()
        return
      }
      setError(result)
      if (result.kind === 'forbidden') {
        // The account may have lost its admin role: the admin area then closes itself.
        void refreshAccount()
      }
      if (result.refresh) {
        stale.current = true
      }
    } finally {
      submittingRef.current = false
      if (mounted.current) {
        setSubmitting(false)
      }
    }
  }

  function close() {
    if (stale.current) {
      stale.current = false
      onStale()
    }
    onClose()
  }

  if (final) {
    return (
      <Dialog title={title} onClose={close} returnFocusRef={returnFocusRef}>
        <div className="report-result report-result-error" role="alert">
          <p className="report-result-title">
            {error.kind === 'forbidden' ? 'Administrator access required' : 'This couldn’t be done'}
          </p>
          <p>{error.message}</p>
        </div>
        <div className="dialog-actions">
          <Button ref={closeRef} variant="secondary" onClick={close}>
            Close
          </Button>
        </div>
      </Dialog>
    )
  }

  const noteLeft = RESOLUTION_NOTE_MAX_LENGTH - note.trim().length

  return (
    <Dialog title={title} onClose={close} returnFocusRef={returnFocusRef} dismissible={!submitting}>
      <form className="form moderation-form" onSubmit={handleSubmit} noValidate aria-busy={submitting}>
        <div className="moderation-consequence">{children}</div>

        {error && (
          <div className="error-message" role="alert">
            <strong>Not saved</strong>
            <p>{error.message}</p>
          </div>
        )}

        {withNote && (
          <div ref={noteRef}>
            <TextAreaField
              label="Resolution note (optional)"
              name="resolutionNote"
              rows={3}
              value={note}
              error={noteError}
              disabled={submitting}
              hint={
                noteLeft >= 0
                  ? `Kept with the report for other administrators. ${numberFormat.format(noteLeft)} characters left.`
                  : `${numberFormat.format(-noteLeft)} characters over the ${numberFormat.format(RESOLUTION_NOTE_MAX_LENGTH)}-character limit.`
              }
              onChange={(event) => {
                const value = event.target.value
                setNote(value)
                setNoteError((current) => (current ? noteLengthError(value) : current))
              }}
            />
          </div>
        )}

        <div className="dialog-actions">
          <Button variant="secondary" onClick={close} disabled={submitting}>
            Cancel
          </Button>
          <Button
            type="submit"
            className={danger ? 'button-danger' : undefined}
            loading={submitting}
            loadingText={runningLabel}
          >
            {confirmLabel}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
