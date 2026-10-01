import type { ButtonHTMLAttributes, Ref } from 'react'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  ref?: Ref<HTMLButtonElement>
  variant?: 'primary' | 'secondary' | 'ghost'
  /** Disables the button and shows `loadingText` while an action is in progress. */
  loading?: boolean
  loadingText?: string
  block?: boolean
}

export function Button({
  variant = 'primary',
  loading = false,
  loadingText,
  block = false,
  disabled,
  className,
  children,
  type = 'button',
  ...rest
}: ButtonProps) {
  const classes = ['button', `button-${variant}`, block && 'button-block', className].filter(Boolean).join(' ')
  return (
    <button type={type} className={classes} disabled={disabled || loading} aria-busy={loading || undefined} {...rest}>
      {loading && loadingText ? loadingText : children}
    </button>
  )
}
