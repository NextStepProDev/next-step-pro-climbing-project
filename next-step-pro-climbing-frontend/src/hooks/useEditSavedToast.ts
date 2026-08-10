import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { useToast } from '../context/ToastContext'

/**
 * Confirms an admin edit and says whether it mailed anyone.
 *
 * Moving a slot or an event notifies everyone holding a booking, but the panel used to close in
 * silence — the only way to learn whether anything went out was to ask a participant. The count
 * comes from the backend and equals the number of mails actually sent (people with email
 * notifications switched off are not written to, so they are not counted).
 *
 * Three states, because sending nothing happens for two different reasons and they are not the
 * same news. An empty slot had nobody to write to: mentioning notifications there is noise about
 * a thing that was never in question. A slot with people booked that mailed no-one IS worth
 * saying out loud — the admin has just moved someone's booking and is entitled to know they were
 * not told.
 */
export function useEditSavedToast() {
  const { t } = useTranslation('admin')
  const { showToast } = useToast()

  return useCallback(
    (result: { notifiedCount: number; hadParticipants: boolean }) => {
      if (result.notifiedCount > 0) {
        showToast(t('editSaved.notified', { count: result.notifiedCount }))
      } else {
        showToast(t(result.hadParticipants ? 'editSaved.noneNotified' : 'editSaved.saved'))
      }
    },
    [showToast, t],
  )
}
