import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { pzaLogo } from '../../assets'
import clsx from 'clsx'

interface PzaBadgeProps {
  variant?: 'badge' | 'sticker'
  size?: 'sm' | 'md' | 'lg'
  className?: string
}

const stickerSizes = {
  sm: 'w-7 h-7',
  md: 'w-9 h-9',
  lg: 'w-12 h-12',
}

const logoSizes = {
  sm: 'h-5',
  md: 'h-6',
  lg: 'h-8',
}

export function PzaBadge({ variant = 'badge', size = 'md', className }: PzaBadgeProps) {
  const { t } = useTranslation('common')
  const [hidden, setHidden] = useState(false)

  if (variant === 'sticker') {
    if (hidden) return null
    return (
      <div
        className={clsx(
          'absolute bottom-0 right-0 rounded-full bg-white shadow-md border border-dark-600 flex items-center justify-center overflow-hidden',
          stickerSizes[size],
          className,
        )}
        title={t('pza.recommended')}
      >
        <img
          src={pzaLogo}
          alt="PZA"
          className="w-full h-full object-contain p-0.5"
          onError={() => setHidden(true)}
        />
      </div>
    )
  }

  if (hidden) return null
  return (
    <div
      className={clsx(
        'inline-flex items-center gap-2 px-3 py-1.5 bg-dark-800 border border-dark-700 rounded-lg',
        className,
      )}
    >
      <img
        src={pzaLogo}
        alt="PZA"
        className={clsx('object-contain', logoSizes[size])}
        onError={() => setHidden(true)}
      />
      <span className="text-dark-200 text-sm font-medium">{t('pza.recommended')}</span>
    </div>
  )
}
