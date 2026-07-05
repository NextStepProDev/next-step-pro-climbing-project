import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useLocation } from 'react-router-dom'
import { format } from 'date-fns'
import { CalendarPlus, LogIn } from 'lucide-react'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { TimeScrollPicker } from '../ui/TimeScrollPicker'
import { SuccessCheckmark } from '../ui/SuccessCheckmark'
import { useAuth } from '../../context/AuthContext'
import { saveRedirectPath } from '../../utils/redirect'
import { trainingRequestApi, coursesApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import { useDateLocale } from '../../utils/dateFnsLocale'

/** Okno dostępności, w którym składana jest propozycja — ogranicza datę i godziny. */
export interface ProposeWindow {
  slotId: string
  date: string
  startTime: string // "HH:mm"
  endTime: string // "HH:mm"
}

interface ProposeTrainingModalProps {
  isOpen: boolean
  onClose: () => void
  /** Data startowa formularza (yyyy-MM-dd); w trybie okna nadpisywana datą okna. */
  defaultDate: string
  /** Tryb okna dostępności: data zablokowana, godziny ograniczone do ram okna. */
  window?: ProposeWindow
}

/**
 * Formularz propozycji terminu treningu (zalogowany użytkownik).
 * Dwa tryby: w oknie dostępności (godziny ograniczone do okna) albo swobodny
 * (dowolna przyszła data). Admin dostaje maila i widzi propozycję w panelu.
 */
export function ProposeTrainingModal({ isOpen, onClose, defaultDate, window: proposeWindow }: ProposeTrainingModalProps) {
  const { t, i18n } = useTranslation('calendar')
  const locale = useDateLocale()
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()

  const windowStart = proposeWindow?.startTime.slice(0, 5)
  const windowEnd = proposeWindow?.endTime.slice(0, 5)

  const [form, setForm] = useState({
    date: proposeWindow?.date ?? defaultDate,
    startTime: windowStart ?? '10:00',
    endTime: windowEnd ?? '11:00',
    participants: 1,
    courseId: '',
    comment: '',
  })
  const [showSuccess, setShowSuccess] = useState(false)

  const { data: courses = [] } = useQuery({
    queryKey: ['courses', i18n.language],
    queryFn: () => coursesApi.getAll(i18n.language),
    enabled: isOpen && isAuthenticated,
    staleTime: 5 * 60 * 1000,
  })

  const createMutation = useMutation({
    mutationFn: trainingRequestApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['trainingRequests'] })
      setShowSuccess(true)
    },
  })

  const timeError = form.endTime <= form.startTime ? t('propose.endAfterStart') : null
  const windowError = proposeWindow && windowStart && windowEnd && (form.startTime < windowStart || form.endTime > windowEnd)
    ? t('propose.outsideWindow', { from: windowStart, to: windowEnd })
    : null

  const handleLoginRedirect = () => {
    saveRedirectPath(location.pathname + location.search)
    navigate('/login')
  }

  const submitForm = () => {
    if (timeError || windowError || !form.date) return
    createMutation.mutate({
      requestedDate: form.date,
      startTime: form.startTime,
      endTime: form.endTime,
      participants: form.participants,
      comment: form.comment.trim() || undefined,
      courseId: form.courseId || undefined,
      windowSlotId: proposeWindow?.slotId,
    })
  }

  return (
    <>
      {showSuccess && <SuccessCheckmark onDone={() => { setShowSuccess(false); onClose() }} />}
      <Modal isOpen={isOpen} onClose={onClose} title={t('propose.title')}>
        {!isAuthenticated ? (
          <div className="text-center py-6">
            <LogIn className="w-10 h-10 text-primary-500 mx-auto mb-3" />
            <p className="text-surface-300 mb-4">{t('propose.loginRequired')}</p>
            <Button variant="primary" onClick={handleLoginRedirect}>
              {t('propose.loginButton')}
            </Button>
          </div>
        ) : (
          <form onSubmit={(e) => { e.preventDefault(); submitForm() }} className="space-y-4">
            <p className="text-sm text-surface-400">{t('propose.intro')}</p>

            {proposeWindow ? (
              <div className="p-3 bg-teal-500/10 border border-teal-500/20 rounded-lg">
                <div className="flex items-center gap-2">
                  <CalendarPlus className="w-4 h-4 text-teal-400 shrink-0" />
                  <span className="text-sm text-teal-300 font-medium capitalize">
                    {format(new Date(proposeWindow.date), 'EEEE, d MMMM yyyy', { locale })}
                  </span>
                </div>
                <p className="text-xs text-teal-200/80 mt-1">
                  {t('propose.windowInfo', { from: windowStart, to: windowEnd })}
                </p>
              </div>
            ) : (
              <div>
                <label className="block text-sm text-surface-400 mb-1">{t('propose.date')}</label>
                <input
                  type="date"
                  value={form.date}
                  min={format(new Date(), 'yyyy-MM-dd')}
                  onChange={(e) => setForm({ ...form, date: e.target.value })}
                  required
                  className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
                />
              </div>
            )}

            <div>
              <div className="grid grid-cols-2 gap-4">
                <TimeScrollPicker
                  label={t('propose.from')}
                  value={form.startTime}
                  onChange={(v) => setForm({ ...form, startTime: v })}
                />
                <TimeScrollPicker
                  label={t('propose.to')}
                  value={form.endTime}
                  onChange={(v) => setForm({ ...form, endTime: v })}
                />
              </div>
              {(timeError || windowError) && (
                <p className="text-sm text-rose-400/80 mt-1">{timeError ?? windowError}</p>
              )}
            </div>

            <div>
              <label className="block text-sm text-surface-400 mb-1">{t('propose.participants')}</label>
              <input
                type="number"
                min={1}
                max={20}
                value={form.participants}
                onChange={(e) => setForm({ ...form, participants: Math.max(1, parseInt(e.target.value) || 1) })}
                className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
              />
            </div>

            {courses.length > 0 && (
              <div>
                <label className="block text-sm text-surface-400 mb-1">{t('propose.course')}</label>
                <select
                  value={form.courseId}
                  onChange={(e) => setForm({ ...form, courseId: e.target.value })}
                  className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
                >
                  <option value="">{t('propose.noCourse')}</option>
                  {courses.map((course) => (
                    <option key={course.id} value={course.id}>{course.title}</option>
                  ))}
                </select>
              </div>
            )}

            <div>
              <label className="block text-sm text-surface-400 mb-1">{t('propose.comment')}</label>
              <textarea
                value={form.comment}
                onChange={(e) => setForm({ ...form, comment: e.target.value })}
                placeholder={t('propose.commentPlaceholder')}
                maxLength={1000}
                rows={3}
                className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 resize-none"
              />
            </div>

            <div className="flex gap-3 pt-2">
              <Button type="submit" loading={createMutation.isPending} className="flex-1">
                {t('propose.submit')}
              </Button>
              <Button type="button" variant="ghost" onClick={onClose}>
                {t('propose.cancel')}
              </Button>
            </div>

            {createMutation.isError && (
              <p className="text-sm text-rose-400/80">{getErrorMessage(createMutation.error)}</p>
            )}
          </form>
        )}
      </Modal>
    </>
  )
}
