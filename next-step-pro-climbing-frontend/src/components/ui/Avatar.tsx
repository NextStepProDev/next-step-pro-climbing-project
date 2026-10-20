import { useState } from 'react'
import clsx from 'clsx'

interface AvatarProps {
  src?: string | null
  name?: string | null
  /** Container size/position classes, e.g. "w-9 h-9". */
  className?: string
  /** Initial-letter text classes (when there is no photo). */
  textClassName?: string
}

export function Avatar({ src, name, className = 'w-9 h-9', textClassName = 'text-sm' }: AvatarProps) {
  const [failed, setFailed] = useState(false)
  // A failure belongs to the address that failed: a new photo (a fresh upload) gets its own try,
  // where it used to stay hidden behind the initial until the page was reloaded.
  const [failedSrc, setFailedSrc] = useState(src)
  if (src !== failedSrc) {
    setFailedSrc(src)
    setFailed(false)
  }
  const initial = name?.trim().charAt(0).toUpperCase() || '?'
  const showImage = src && !failed

  return (
    <div
      className={clsx(
        'rounded-full bg-primary-600 flex items-center justify-center overflow-hidden shrink-0',
        className,
      )}
    >
      {showImage ? (
        <img
          src={src}
          alt={name ?? ''}
          className="w-full h-full object-cover"
          onError={() => setFailed(true)}
        />
      ) : (
        <span className={clsx('font-bold text-white', textClassName)}>{initial}</span>
      )}
    </div>
  )
}
