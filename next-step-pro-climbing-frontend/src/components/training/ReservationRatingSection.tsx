import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Pencil } from 'lucide-react'
import { Button } from '../ui/Button'
import { RpePicker } from './RpePicker'
import { trainingCalendarApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import { decodeHtmlEntities } from '../../utils/htmlEntities'
import type { ReservationOverlayItem } from '../../types'

interface ReservationRatingSectionProps {
  reservation: ReservationOverlayItem
  // Refresh the range + stats after a successful rating
  onRated: () => void
}

/**
 * Athlete rates an attended reservation (RPE 1-10 + optional note). Rating an attended booking
 * feeds the same intensity stats as personal-training RPE. Athlete-only (the coach can't rate).
 */
export function ReservationRatingSection({ reservation, onRated }: ReservationRatingSectionProps) {
  const { t } = useTranslation('training')
  const [rpe, setRpe] = useState<number | null>(reservation.rpe)
  const [note, setNote] = useState(reservation.rpeNote ? decodeHtmlEntities(reservation.rpeNote) : '')
  const [editing, setEditing] = useState(reservation.rpe == null)

  const mutation = useMutation({
    mutationFn: () => trainingCalendarApi.rateReservation(reservation.id, rpe!, note.trim() || undefined),
    onSuccess: () => { setEditing(false); onRated() },
  })

  // Already rated and not editing → compact summary with an edit affordance
  if (!editing && reservation.rpe != null) {
    return (
      <div className="mt-4 p-3 rounded-lg bg-surface-800/60 border border-surface-700 space-y-2">
        <div className="flex items-center justify-between gap-2">
          <span className="text-sm font-medium text-surface-200">{t('rpe.rated', { value: reservation.rpe })}</span>
          <Button size="sm" variant="ghost" onClick={() => setEditing(true)}>
            <Pencil className="w-3.5 h-3.5 mr-1" />{t('completion.edit')}
          </Button>
        </div>
        {reservation.rpeNote && (
          <p className="text-sm text-surface-300 whitespace-pre-wrap">{decodeHtmlEntities(reservation.rpeNote)}</p>
        )}
      </div>
    )
  }

  return (
    <div className="mt-4 p-3 rounded-lg bg-surface-800/60 border border-surface-700 space-y-3">
      <p className="text-sm font-medium text-surface-200">{t('rpe.rate')}</p>
      <RpePicker value={rpe} onChange={setRpe} />
      <div>
        <label className="block text-sm text-surface-400 mb-1">{t('rpe.note')}</label>
        <textarea
          value={note}
          onChange={(e) => setNote(e.target.value)}
          maxLength={500}
          rows={2}
          placeholder={t('rpe.notePlaceholder')}
          className="w-full bg-surface-800 border border-surface-700 rounded-lg px-3 py-2 text-sm text-surface-100 resize-none"
        />
      </div>
      {mutation.isError && <p className="text-sm text-rose-400/80">{getErrorMessage(mutation.error)}</p>}
      <div className="flex justify-end">
        <Button size="sm" variant="primary" onClick={() => mutation.mutate()} loading={mutation.isPending} disabled={rpe == null}>
          {t('rpe.save')}
        </Button>
      </div>
    </div>
  )
}
