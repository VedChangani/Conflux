import { useEffect, useId, useRef, type KeyboardEvent, type MouseEvent, type ReactNode, type RefObject } from 'react'
import { createPortal } from 'react-dom'

const FOCUSABLE = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled]):not([type="hidden"])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

function focusableIn(container: HTMLElement): HTMLElement[] {
  return Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(
    (element) => !element.closest('[inert]') && element.getAttribute('aria-hidden') !== 'true',
  )
}

interface DialogProps {
  title: string
  onClose: () => void
  children: ReactNode
  /** When false (e.g. while a request runs), Escape, the backdrop and × do not close it. */
  dismissible?: boolean
  /**
   * Where focus goes when the dialog closes: the control that opened it. Needed because
   * some browsers (Safari) do not focus a button when it is clicked. Pass a `useRef` object.
   */
  returnFocusRef?: RefObject<HTMLElement | null>
}

/**
 * A modal dialog: rendered over the page, with the rest of the page inert while it is open.
 * Focus moves into it (to its first control), stays inside it, and returns to the control
 * that opened it (`returnFocusRef`, else whatever was focused before) when it closes. Escape and the backdrop close
 * it while `dismissible`.
 */
export function Dialog({ title, onClose, children, dismissible = true, returnFocusRef }: DialogProps) {
  const titleId = useId()
  const backdropRef = useRef<HTMLDivElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    // The opener exists before the dialog and outlives it, so it is captured now.
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const returnTarget = returnFocusRef?.current ?? previous
    const backdrop = backdropRef.current
    // Make everything else inert, so neither pointer, keyboard nor screen reader reaches it.
    const madeInert = Array.from(document.body.children).filter(
      (element): element is HTMLElement =>
        element !== backdrop && element instanceof HTMLElement && !element.hasAttribute('inert'),
    )
    madeInert.forEach((element) => element.setAttribute('inert', ''))
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    const panel = panelRef.current
    if (panel) {
      // The first real control (the × button is a fallback way out, not a starting point).
      const first = focusableIn(panel).find((element) => !element.classList.contains('dialog-close'))
      const start = first ?? panel
      start.focus()
    }

    return () => {
      madeInert.forEach((element) => element.removeAttribute('inert'))
      document.body.style.overflow = overflow
      returnTarget?.focus()
    }
    // Runs once per opening: the dialog is mounted exactly while it is open, and
    // `returnFocusRef` is a stable ref object.
  }, [returnFocusRef])

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      event.stopPropagation()
      if (dismissible) {
        onClose()
      }
      return
    }
    if (event.key === 'Tab' && panelRef.current) {
      const focusable = focusableIn(panelRef.current)
      if (focusable.length === 0) {
        event.preventDefault()
        return
      }
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
  }

  function handleBackdrop(event: MouseEvent<HTMLDivElement>) {
    if (event.target === event.currentTarget && dismissible) {
      onClose()
    }
  }

  return createPortal(
    <div ref={backdropRef} className="dialog-backdrop" onMouseDown={handleBackdrop}>
      <div
        ref={panelRef}
        className="dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onKeyDown={handleKeyDown}
      >
        <div className="dialog-header">
          <h2 id={titleId} className="dialog-title">
            {title}
          </h2>
          <button type="button" className="dialog-close" aria-label="Close dialog" onClick={onClose} disabled={!dismissible}>
            <svg viewBox="0 0 16 16" aria-hidden="true" focusable="false">
              <path d="m4 4 8 8M12 4l-8 8" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" />
            </svg>
          </button>
        </div>
        {children}
      </div>
    </div>,
    document.body,
  )
}
