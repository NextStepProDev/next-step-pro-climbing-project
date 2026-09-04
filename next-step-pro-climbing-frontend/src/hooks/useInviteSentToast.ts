import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { useToast } from '../context/ToastContext'
import type { NotifyInvitesResult } from '../types'

/**
 * Confirms a "save and send invitations" in one message, because the admin took one action.
 *
 * The count comes from the server and equals the mails that actually left — people who switched
 * emails off are skipped there, and reported separately here. Saying only "sent 0" would read as
 * a failure in exactly the case where the system behaved correctly and deliberately.
 */
export function useInviteSentToast() {
  const { t } = useTranslation('admin')
  const { showToast } = useToast()

  return useCallback(
    (result: NotifyInvitesResult) => {
      const sent = result.notifiedCount > 0
        ? t('inviteNotify.savedAndSent', { count: result.notifiedCount })
        : t('inviteNotify.savedNothingSent')
      const skipped = result.skippedNotificationsOff > 0
        ? ' ' + t('inviteNotify.skipped', { count: result.skippedNotificationsOff })
        : ''
      showToast(sent + skipped)
    },
    [showToast, t],
  )
}
