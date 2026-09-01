import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { Coins } from 'lucide-react'
import { adminSettlementsApi } from '../../../api/client'
import { parseCalendarDate } from '../../../utils/calendarDate'
import { useDateLocale } from '../../../utils/dateFnsLocale'
import { DateInput } from '../../ui/DateInput'
import { Button } from '../../ui/Button'
import { getErrorMessage } from '../../../utils/errors'
import { formatPln, parseAmount } from '../../../utils/money'
import { todayInWarsaw } from '../../../utils/calendarDate'

/**
 * What this client has paid and still owes.
 *
 * Its own request rather than fields on the user card's DTO: the settlement types are deliberately
 * unreachable from the package that builds that card, and one rule beats an exception to it.
 *
 * Whole history, not the Settlements tab's selected year — "what do I have with this person" has no
 * year in it.
 */
export function UserMoneyCard({ userId }: { userId: string }) {
  const { t, i18n } = useTranslation('admin')
  const locale = useDateLocale()

  const { data } = useQuery({
    queryKey: ['admin', 'settlements', 'payer', userId],
    queryFn: () => adminSettlementsApi.getPayerSummary(userId),
  })

  const money = (amount: number) => formatPln(amount, i18n.language)

  // Nothing recorded is not a zero balance — rendering "0 zł paid" would state something about
  // somebody nobody has ever priced.
  if (!data) return null
  const empty = data.settlementCount === 0 && data.outstanding === 0 && data.recent.length === 0

  return (
    <div className="bg-surface-900 rounded-xl border border-surface-800 p-4 space-y-3">
      <div className="flex items-center gap-2 text-sm font-medium text-surface-300">
        <Coins className="w-4 h-4 text-surface-400" />
        {t('users.detail.money.title')}
      </div>

      <SubscriptionRow userId={userId} />

      {empty ? (
        <p className="text-sm text-surface-400">{t('users.detail.money.none')}</p>
      ) : (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <Figure label={t('users.detail.money.paid')} value={money(data.paid)} />
            <Figure
              label={t('users.detail.money.owed')}
              value={data.outstanding > 0 ? money(data.outstanding) : '—'}
              tone={data.outstanding > 0 ? 'text-amber-500' : 'text-surface-500'}
            />
            <Figure label={t('users.detail.money.count')} value={String(data.settlementCount)} />
            <Figure
              label={t('users.detail.money.last')}
              value={
                data.lastPayment
                  ? format(parseCalendarDate(data.lastPayment), 'dd.MM.yyyy', { locale })
                  : '—'
              }
            />
          </div>

          <ul className="divide-y divide-surface-800">
            {data.recent.map((line) => (
              <li key={`${line.date}:${line.title}:${line.amount}`} className="flex items-center gap-3 py-1.5 text-xs">
                <span className="w-24 shrink-0 text-surface-400 tabular-nums">
                  {format(parseCalendarDate(line.date), 'dd.MM.yyyy', { locale })}
                </span>
                <span className="flex-1 min-w-0 truncate text-surface-300">
                  {line.monthlyFee
                    ? t('users.detail.money.feeFor', {
                        month: format(parseCalendarDate(line.date), 'LLLL yyyy', { locale }),
                      })
                    : line.title ?? t('users.detail.money.untitled')}
                </span>
                <span className="shrink-0 text-surface-200 tabular-nums">{money(line.amount)}</span>
                <span className="w-24 shrink-0 text-right tabular-nums">
                  {/* A part payment is neither of the two old states. Showing only the charge beside
                      a payment date read as settled in full, which is how somebody stops chasing a
                      remainder they never knew about. */}
                  {line.settledOn && line.paidAmount < line.amount ? (
                    <span className="text-amber-500">
                      {t('users.detail.money.short', {
                        amount: money(line.amount - line.paidAmount),
                      })}
                    </span>
                  ) : line.settledOn ? (
                    <span className="text-surface-500">
                      {format(parseCalendarDate(line.settledOn), 'dd.MM.yyyy', { locale })}
                    </span>
                  ) : (
                    <span className="text-amber-500">{t('users.detail.money.unpaid')}</span>
                  )}
                </span>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}

function Figure({ label, value, tone }: { label: string; value: string; tone?: string }) {
  return (
    <div>
      <div className={`text-lg font-semibold tabular-nums ${tone ?? 'text-surface-100'}`}>{value}</div>
      <div className="text-xs text-surface-400">{label}</div>
    </div>
  )
}

/**
 * The standing monthly fee for coaching this person.
 *
 * Lives on their card because that is where "what do I have with this person" already lives — the
 * Settlements tab answers about money in aggregate, this answers about one relationship.
 *
 * ⚠️ The end date is a MONTH picker in disguise (any day, server snaps it) and defaults to today,
 * because a collaboration ends in a conversation and gets written down a week later. A past month is
 * the ordinary case, not the exception.
 */
function SubscriptionRow({ userId }: { userId: string }) {
  const { t, i18n } = useTranslation('admin')
  const locale = useDateLocale()
  const queryClient = useQueryClient()
  const [adding, setAdding] = useState(false)
  const [amount, setAmount] = useState('')
  const [startedOn, setStartedOn] = useState(() => todayInWarsaw())
  const [endedOn, setEndedOn] = useState(() => todayInWarsaw())

  const { data: subs } = useQuery({
    queryKey: ['admin', 'settlements', 'subscriptions', userId],
    queryFn: () => adminSettlementsApi.listSubscriptions(userId),
  })

  // The whole prefix: creating a subscription bills months, which changes the money block above it
  // and every figure on the Settlements tab.
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['admin', 'settlements'] })

  const create = useMutation({
    mutationFn: () =>
      adminSettlementsApi.createSubscription(userId, parseAmount(amount) as number, startedOn, null),
    onSuccess: () => {
      setAdding(false)
      setAmount('')
      refresh()
    },
  })
  const end = useMutation({
    mutationFn: (id: string) => adminSettlementsApi.endSubscription(id, endedOn),
    onSuccess: refresh,
  })
  const reopen = useMutation({
    mutationFn: (id: string) => adminSettlementsApi.reopenSubscription(id),
    onSuccess: refresh,
  })

  if (!subs) return null
  const money = (value: number) => formatPln(value, i18n.language)
  const active = subs.find((s) => s.active)

  return (
    <div className="rounded-lg border border-surface-800 p-3 space-y-2">
      <div className="text-xs text-surface-400">{t('users.detail.money.subscription.title')}</div>

      {subs.map((sub) => (
        <div key={sub.id} className="flex flex-wrap items-center gap-2 text-sm">
          <span className={sub.active ? 'text-surface-200' : 'text-surface-500'}>
            {money(sub.amount)} {t('users.detail.money.subscription.perMonth')}
          </span>
          <span className="text-xs text-surface-500">
            {format(parseCalendarDate(sub.startedOn), 'LLLL yyyy', { locale })}
            {sub.endedOn
              ? ` — ${format(parseCalendarDate(sub.endedOn), 'LLLL yyyy', { locale })}`
              : ` — ${t('users.detail.money.subscription.openEnded')}`}
          </span>
          {sub.active ? (
            <>
              <DateInput
                value={endedOn}
                onChange={setEndedOn}
                aria-label={t('users.detail.money.subscription.endLabel')}
                className="bg-surface-800 border border-surface-600 rounded px-2 py-1 text-xs text-surface-100 focus:outline-none focus:border-primary-500"
              />
              <Button size="sm" variant="ghost" loading={end.isPending} onClick={() => end.mutate(sub.id)}>
                {t('users.detail.money.subscription.end')}
              </Button>
            </>
          ) : (
            <Button size="sm" variant="ghost" loading={reopen.isPending} onClick={() => reopen.mutate(sub.id)}>
              {t('users.detail.money.subscription.reopen')}
            </Button>
          )}
        </div>
      ))}

      {active === undefined && (adding ? (
        <div className="flex flex-wrap items-end gap-2">
          <input
            inputMode="decimal"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder={t('users.detail.money.subscription.amount')}
            aria-label={t('users.detail.money.subscription.amount')}
            className="w-28 bg-surface-800 border border-surface-600 rounded px-2 py-1 text-sm text-surface-100 focus:outline-none focus:border-primary-500"
          />
          <DateInput
            value={startedOn}
            onChange={setStartedOn}
            aria-label={t('users.detail.money.subscription.from')}
            className="bg-surface-800 border border-surface-600 rounded px-2 py-1 text-sm text-surface-100 focus:outline-none focus:border-primary-500"
          />
          <Button
            size="sm"
            variant="primary"
            disabled={parseAmount(amount) === null || startedOn === ''}
            loading={create.isPending}
            onClick={() => create.mutate()}
          >
            {t('users.detail.money.subscription.start')}
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setAdding(false)}>
            {t('settlements.section.cancel')}
          </Button>
          {create.isError && (
            <span className="text-xs text-rose-400/80">{getErrorMessage(create.error)}</span>
          )}
        </div>
      ) : (
        <Button size="sm" variant="secondary" onClick={() => setAdding(true)}>
          {t('users.detail.money.subscription.add')}
        </Button>
      ))}
    </div>
  )
}
