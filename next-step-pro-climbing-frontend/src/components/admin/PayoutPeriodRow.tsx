import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useLocation } from 'react-router-dom'
import { format } from 'date-fns'
import { ChevronDown, ChevronRight } from 'lucide-react'
import { adminSettlementsApi } from '../../api/client'
import { parseCalendarDate } from '../../utils/calendarDate'
import { useDateLocale } from '../../utils/dateFnsLocale'
import { useMoney } from './useMoney'
import type { PayoutPeriod } from '../../types'

/**
 * One month of one payer, expandable into everything it adds up: the sessions it counted and the
 * transfers that arrived.
 *
 * The expansion is not decoration. The row is three aggregates and one derived figure, and the
 * derived one — the rate — is what the feature exists for; when it looks wrong there has to be a
 * way down to the rows behind it. A mistyped 14000 for 1400 is removable here, and a month whose
 * hours read short can be opened to find which session is missing its times.
 *
 * ⚠️ It opens whenever there is anything underneath, sessions included — not only transfers.
 * "Five sessions, no transfer yet" is the row people come to this table for, and it was the one
 * row that could not be opened.
 */
export function PayoutPeriodRow({
  period,
  onChanged,
  linkPayer = true,
}: {
  period: PayoutPeriod
  onChanged: () => void
  /** False on the payer's own screen, where the name would link to the page it is already on. */
  linkPayer?: boolean
}) {
  const { t } = useTranslation('admin')
  const money = useMoney()
  const locale = useDateLocale()
  const location = useLocation()
  const year = new URLSearchParams(location.search).get('year')
  const [open, setOpen] = useState(false)
  const expandable = period.transfers.length > 0 || period.heldSessions.length > 0

  const remove = useMutation({
    mutationFn: (payoutId: string) => adminSettlementsApi.deletePayout(payoutId),
    onSuccess: onChanged,
  })

  return (
    <>
      <tr>
        {/* The payer's name is the way into their own history — every other list on this tab is
            clickable through to the thing it names, and this was the one that dead-ended. The year
            travels along so the back arrow returns to the tab as it was left. Not a link on the
            payer's own screen: a row that links to the page it is already on is a dead click. */}
        <td className="py-2 text-surface-200">
          {linkPayer ? (
            <Link
              to={`/admin/settlements/sources/${period.sourceId}${year ? `?year=${year}` : ''}`}
              className="hover:text-primary-300 transition-colors"
            >
              {period.sourceName}
            </Link>
          ) : (
            period.sourceName
          )}
        </td>
        <td className="py-2 text-surface-400">
          {expandable ? (
            <button
              type="button"
              onClick={() => setOpen((v) => !v)}
              aria-expanded={open}
              className="flex items-center gap-1 hover:text-primary-300 transition-colors"
            >
              {open ? <ChevronDown className="w-3 h-3 shrink-0" /> : <ChevronRight className="w-3 h-3 shrink-0" />}
              {format(parseCalendarDate(period.month), 'LLLL yyyy', { locale })}
            </button>
          ) : (
            format(parseCalendarDate(period.month), 'LLLL yyyy', { locale })
          )}
        </td>
        <td className="py-2 text-right text-surface-400 tabular-nums">{period.sessions}</td>
        <td className="py-2 text-right text-surface-400 tabular-nums">
          {(period.minutes / 60).toFixed(1)}
          {/* The denominator's own gap, stated rather than hidden. */}
          {period.sessionsWithoutHours > 0 && (
            <span className="text-amber-500">
              {' '}
              {t('settlements.tab.payouts.withoutHours', { n: period.sessionsWithoutHours })}
            </span>
          )}
        </td>
        <td className="py-2 text-right tabular-nums">
          {period.amount > 0 ? (
            <span className="text-surface-200">{money(period.amount)}</span>
          ) : (
            /* Work done, nothing received: the row people actually come here for. */
            <span className="text-amber-500">{t('settlements.tab.payouts.awaiting')}</span>
          )}
        </td>
        <td className="py-2 text-right text-surface-200 tabular-nums">
          {period.ratePerHour === null ? (
            <span className="text-surface-500">—</span>
          ) : (
            money(period.ratePerHour)
          )}
        </td>
      </tr>
      {/* The sessions first: they are what the two middle columns counted, and the reason the rate
          is whatever it is. Each one links into its own entry — editing a session (its hours, or
          who settles it) happens there, the same door every other list on this tab opens. */}
      {open && period.heldSessions.map((session) => (
        <tr key={`${session.targetType}:${session.targetId}`} className="text-xs">
          <td />
          <td className="py-1 pl-4 text-surface-500 tabular-nums">
            {format(parseCalendarDate(session.date), 'dd.MM.yyyy', { locale })}
          </td>
          <td className="py-1 text-surface-400" colSpan={2}>
            <Link
              to={`/calendar?date=${session.date}&${session.targetType}=${session.targetId}`}
              state={{ returnTo: location.pathname + location.search }}
              className="hover:text-primary-300 transition-colors"
            >
              {session.title ?? t(`settlements.tab.outstanding.untitled.${session.targetType}`)}
            </Link>
          </td>
          {/* The figure sits on the right, where the transfer rows put theirs, rather than under the
              "hours" column it belongs to: that column is a narrow numeric one, and a session title
              dropped into it would resize the whole aggregate table around the longest name. */}
          <td className="py-1 text-right text-surface-400 tabular-nums" colSpan={2}>
            {/* Amber on the ones with no knowable length, because those are the sessions the hours
                column is missing — the "+N without hours" above, given faces. */}
            {session.minutes === null ? (
              <span className="text-amber-500">{t('settlements.tab.payouts.noHours')}</span>
            ) : (
              t('settlements.tab.payouts.sessionHours', { hours: (session.minutes / 60).toFixed(1) })
            )}
          </td>
        </tr>
      ))}
      {open && period.transfers.map((transfer) => (
        <tr key={transfer.id} className="text-xs">
          <td />
          {/* ⚠️ Three, not two: the table has six columns and this row had five, so every transfer
              rendered its amount under "hours" and left the rate column empty. */}
          <td className="py-1 pl-4 text-surface-500" colSpan={3}>
            {t('settlements.tab.payouts.received', {
              date: format(parseCalendarDate(transfer.receivedOn), 'dd.MM.yyyy', { locale }),
            })}
          </td>
          <td className="py-1 text-right text-surface-300 tabular-nums">{money(transfer.amount)}</td>
          <td className="py-1 text-right">
            <button
              type="button"
              onClick={() => remove.mutate(transfer.id)}
              aria-label={t('settlements.tab.payouts.deleteTransfer', {
                amount: money(transfer.amount),
              })}
              className="text-surface-500 hover:text-rose-400 transition-colors"
            >
              ×
            </button>
          </td>
        </tr>
      ))}
    </>
  )
}
