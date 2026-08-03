import { format, parseISO } from 'date-fns'
import { Plus } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Modal } from '../ui/Modal'
import { InvitationBlock, ReservationBlock, TrainingBlock } from './TrainingBlock'
import { useDateLocale } from '../../utils/dateFnsLocale'
import type { InvitationOverlayItem, PersonalTraining, ReservationOverlayItem } from '../../types'

interface TrainingDaySheetProps {
  date: string
  trainings: PersonalTraining[]
  reservations: ReservationOverlayItem[]
  invitations: InvitationOverlayItem[]
  invitationLabel: string
  onClose: () => void
  onTrainingClick: (training: PersonalTraining) => void
  onReservationClick: (reservation: ReservationOverlayItem) => void
  onInvitationClick: (invitation: InvitationOverlayItem) => void
  onAdd: (date: string) => void
  onTrainingCopy?: (training: PersonalTraining) => void
  onTrainingCut?: (training: PersonalTraining) => void
  isCoachView?: boolean
}

/**
 * One day's entries, opened from the month view: by tapping a cell on a phone, or by the
 * "+N" button when a desktop cell has more than it can show.
 *
 * It renders the SAME block components as the grid, at tile density. A second, sheet-only
 * rendering of a training would be a second thing to keep in step with every change to
 * how a training looks.
 *
 * Every action closes the sheet before it acts: arming the clipboard or opening the detail
 * modal from behind an open sheet leaves two layers of chrome over the calendar.
 */
export function TrainingDaySheet({
  date, trainings, reservations, invitations, invitationLabel, onClose,
  onTrainingClick, onReservationClick, onInvitationClick, onAdd,
  onTrainingCopy, onTrainingCut, isCoachView,
}: TrainingDaySheetProps) {
  const { t } = useTranslation('training')
  const locale = useDateLocale()

  const closeThen = <T,>(action: (value: T) => void) => (value: T) => {
    onClose()
    action(value)
  }

  const isEmpty = trainings.length === 0 && reservations.length === 0 && invitations.length === 0

  return (
    <Modal
      isOpen
      onClose={onClose}
      title={format(parseISO(date), 'EEEE, d MMMM yyyy', { locale })}
      size="md"
    >
      <div className="space-y-1.5">
        {/* Same order as the grid: the entry that needs an action comes first */}
        {invitations.map((inv, i) => (
          <InvitationBlock
            key={`inv-${i}-${inv.slotId ?? inv.eventId}`}
            invitation={inv}
            label={invitationLabel}
            onClick={() => closeThen(onInvitationClick)(inv)}
            density="tile"
          />
        ))}
        {trainings.map((tr) => (
          <TrainingBlock
            key={tr.id}
            training={tr}
            onClick={() => closeThen(onTrainingClick)(tr)}
            density="tile"
            onCopy={onTrainingCopy ? () => closeThen(onTrainingCopy)(tr) : undefined}
            // Completed entries are history: they may be re-planned forward, never moved away
            onCut={onTrainingCut && tr.status !== 'COMPLETED' ? () => closeThen(onTrainingCut)(tr) : undefined}
          />
        ))}
        {reservations.map((r) => (
          <ReservationBlock
            key={r.id}
            reservation={r}
            label={t('overlay.reservation')}
            onClick={() => closeThen(onReservationClick)(r)}
            density="tile"
            isCoachView={isCoachView}
          />
        ))}

        {isEmpty && (
          <p className="py-4 text-center text-sm text-surface-500">{t('month.emptyDay')}</p>
        )}

        <button
          onClick={() => closeThen(onAdd)(date)}
          className="flex items-center justify-center gap-1.5 w-full mt-2 px-3 py-2 text-sm font-medium border border-dashed border-surface-600 text-surface-300 rounded-lg hover:border-primary-500 hover:text-primary-300 transition-colors"
        >
          <Plus className="w-4 h-4" />
          {t('month.addOnDay')}
        </button>
      </div>
    </Modal>
  )
}
