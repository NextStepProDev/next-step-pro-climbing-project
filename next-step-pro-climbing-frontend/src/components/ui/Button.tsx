import { forwardRef, type ButtonHTMLAttributes } from 'react'
import clsx from 'clsx'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  size?: 'sm' | 'md' | 'lg'
  loading?: boolean
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant = 'primary', size = 'md', loading, disabled, children, ...props }, ref) => {
    // hover/active effects are gated behind `enabled:` so a disabled button gives
    // no interactive feedback (no hover colour shift, no press scale) — only the
    // dimmed `disabled:opacity-50` + `cursor-not-allowed` state.
    const baseStyles = 'inline-flex items-center justify-center font-medium rounded-lg transition-all duration-150 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 disabled:opacity-50 disabled:cursor-not-allowed enabled:active:scale-95'

    const variants = {
      primary: 'bg-primary-600 text-white enabled:hover:bg-primary-700 focus-visible:outline-primary-400',
      secondary: 'bg-surface-700 text-surface-100 enabled:hover:bg-surface-600 focus-visible:outline-surface-300',
      ghost: 'bg-transparent text-surface-300 enabled:hover:bg-surface-800 enabled:hover:text-surface-100 focus-visible:outline-surface-400',
      danger: 'bg-rose-500/15 text-rose-300 enabled:hover:bg-rose-500/25 border border-rose-500/20 focus-visible:outline-rose-400',
    }

    const sizes = {
      sm: 'px-3 py-1.5 text-sm',
      md: 'px-4 py-2 text-sm',
      lg: 'px-6 py-3 text-base',
    }

    return (
      <button
        ref={ref}
        className={clsx(baseStyles, variants[variant], sizes[size], className)}
        disabled={disabled || loading}
        {...props}
      >
        {loading && (
          <svg className="animate-spin -ml-1 mr-2 h-4 w-4" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
          </svg>
        )}
        {children}
      </button>
    )
  }
)

Button.displayName = 'Button'
