import { useTranslation } from 'react-i18next'
import { FileClock } from 'lucide-react'
import clsx from 'clsx'
import { Button } from './Button'
import type { DraftOffer } from '../../hooks/useDraft'

interface DraftBannerProps {
  offer: DraftOffer<unknown> | null
  onRestore: () => void
  onDiscard: () => void
  /** Uploaded images a draft cannot keep (a File does not survive storage) — say so before restoring. */
  droppedFiles?: number
  /** Spacing from the host's layout; the banner brings no margin of its own. */
  className?: string
}

/** "You have an unsaved draft from …" with Restore / Discard. Renders nothing without an offer. */
export function DraftBanner({ offer, onRestore, onDiscard, droppedFiles = 0, className }: DraftBannerProps) {
  const { t, i18n } = useTranslation('common')
  if (!offer) return null

  // An instant, so the reader's own clock is right here.
  const time = new Date(offer.savedAt).toLocaleString(i18n.language, { dateStyle: 'short', timeStyle: 'short' })

  return (
    <div
      role="status"
      className={clsx('rounded-lg border border-amber-500/40 bg-amber-500/10 p-3 text-sm text-surface-200', className)}
    >
      <div className="flex items-start gap-2">
        <FileClock className="w-4 h-4 mt-0.5 shrink-0 text-amber-500" aria-hidden="true" />
        <div className="flex-1 space-y-1">
          <p>{t('draft.found', { time })}</p>
          {offer.stale && <p className="text-amber-500">{t('draft.stale')}</p>}
          {droppedFiles > 0 && <p className="text-surface-400">{t('draft.filesDropped', { n: droppedFiles })}</p>}
        </div>
      </div>
      <div className="mt-2 flex flex-wrap justify-end gap-2">
        <Button type="button" variant="ghost" size="sm" onClick={onDiscard}>
          {t('draft.discard')}
        </Button>
        <Button type="button" variant="secondary" size="sm" onClick={onRestore}>
          {t('draft.restore')}
        </Button>
      </div>
    </div>
  )
}
