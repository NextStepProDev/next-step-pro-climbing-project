import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Hourglass } from 'lucide-react'
import { rescheduleApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import { useDateLocale } from '../../utils/dateFnsLocale'
import { describeTerm } from '../../utils/rescheduleTerm'
import { ConfirmModal } from '../ui/ConfirmModal'
import type { MyRescheduleRequest } from '../../types'

/**
 * Sits on the booking card while the coach has not answered. Says the old term still stands —
 * the whole risk of this feature is a client who believes they have already moved.
 */
export function PendingRescheduleNotice({ request }: { request: MyRescheduleRequest }) {
  const { t } = useTranslation('reservations')
  const locale = useDateLocale()
  const queryClient = useQueryClient()
  const [confirming, setConfirming] = useState(false)

  const withdraw = useMutation({
    mutationFn: () => rescheduleApi.withdraw(request.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reservations', 'reschedule'] })
    },
  })

  const proposed = describeTerm(locale, request.newDate, request.newEndDate, request.newStartTime, request.newEndTime)

  return (
    <div
      className="mt-3 p-3 rounded-lg border border-amber-500/30 bg-amber-500/10 text-sm"
      onClick={(e) => e.stopPropagation()}
    >
      <div className="flex items-start gap-2">
        <Hourglass className="w-4 h-4 mt-0.5 shrink-0 text-amber-400" aria-hidden="true" />
        <div className="flex-1 min-w-0">
          <p className="text-amber-200">
            {t('reschedule.pending', { term: proposed })}
          </p>
          <p className="text-xs text-amber-200/70 mt-0.5">{t('reschedule.pendingHint')}</p>
        </div>
        <button
          type="button"
          onClick={() => setConfirming(true)}
          disabled={withdraw.isPending}
          className="shrink-0 text-xs text-amber-300 hover:text-amber-100 underline underline-offset-2 disabled:opacity-50"
        >
          {t('reschedule.withdraw')}
        </button>
      </div>
      {withdraw.isError && (
        <p className="text-xs text-rose-400 mt-1">{getErrorMessage(withdraw.error)}</p>
      )}
      <ConfirmModal
        isOpen={confirming}
        onClose={() => setConfirming(false)}
        onConfirm={() => withdraw.mutate()}
        title={t('reschedule.withdrawTitle')}
        message={t('reschedule.withdrawMessage')}
        confirmText={t('reschedule.withdrawConfirm')}
      />
    </div>
  )
}
