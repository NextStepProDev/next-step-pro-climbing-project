import { useState } from 'react'
import { format, parseISO } from 'date-fns'
import { ClipboardPaste, Plus } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Modal } from '../ui/Modal'
import { ConfirmModal } from '../ui/ConfirmModal'
import { InvitationBlock, ReservationBlock, TrainingBlock } from './TrainingBlock'
import { useDateLocale } from '../../utils/dateFnsLocale'
import { getErrorMessage } from '../../utils/errors'
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
  // Returns the mutation's promise so a rejection can be SAID here. The detail modal has its
  // own error line; the sheet stands on top of it, so a failure that only reached the page
  // underneath would look exactly like a delete that worked.
  onTrainingDelete?: (training: PersonalTraining) => Promise<unknown>
  // On a phone the month is a dot grid with no room to name a paste target, so the sheet is
  // where a paste lands: the add button becomes "paste here" while something is on the
  // clipboard — the same swap the month cell and the week's all-day lane make.
  pasteActive?: boolean
  onPaste?: (date: string) => void
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
  onTrainingCopy, onTrainingCut, onTrainingDelete, pasteActive, onPaste, isCoachView,
}: TrainingDaySheetProps) {
  const { t } = useTranslation('training')
  const locale = useDateLocale()

  // ⚠️ Deliberately NOT routed through closeThen: this exists to clear a run of entries that
  // should not be there, and a sheet that shut itself after each one would make cleaning up
  // five mistakes five round trips. The list shrinks on its own once the query is invalidated.
  const [confirmDelete, setConfirmDelete] = useState<PersonalTraining | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)

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
        {deleteError && (
          <p className="p-2 rounded-lg bg-rose-500/10 border border-rose-500/30 text-sm text-rose-300">
            {deleteError}
          </p>
        )}
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
            onDelete={onTrainingDelete ? () => setConfirmDelete(tr) : undefined}
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

        {pasteActive && onPaste ? (
          <button
            onClick={() => closeThen(onPaste)(date)}
            className="flex items-center justify-center gap-1.5 w-full mt-2 px-3 py-2 text-sm font-medium border border-dashed border-primary-500/50 text-primary-300 rounded-lg hover:bg-primary-500/10 transition-colors"
          >
            <ClipboardPaste className="w-4 h-4" />
            {t('clipboard.pasteHere')}
          </button>
        ) : (
          <button
            onClick={() => closeThen(onAdd)(date)}
            className="flex items-center justify-center gap-1.5 w-full mt-2 px-3 py-2 text-sm font-medium border border-dashed border-surface-600 text-surface-300 rounded-lg hover:border-primary-500 hover:text-primary-300 transition-colors"
          >
            <Plus className="w-4 h-4" />
            {t('month.addOnDay')}
          </button>
        )}
      </div>

      <ConfirmModal
        isOpen={confirmDelete !== null}
        onClose={() => setConfirmDelete(null)}
        onConfirm={() => {
          const target = confirmDelete
          setConfirmDelete(null)
          setDeleteError(null)
          if (target) {
            onTrainingDelete?.(target)?.catch((err) => setDeleteError(getErrorMessage(err)))
          }
        }}
        title={t('detail.deleteConfirmTitle')}
        message={t('detail.deleteConfirmMessage')}
        variant="danger"
      />
    </Modal>
  )
}
