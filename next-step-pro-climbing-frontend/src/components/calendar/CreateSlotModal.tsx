import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import { Button } from '../ui/Button'
import { Modal } from '../ui/Modal'
import { TimeScrollPicker } from '../ui/TimeScrollPicker'
import type { CreateTimeSlotRequest } from '../../types'

interface CreateSlotModalProps {
  isOpen: boolean
  onClose: () => void
  defaultDate: string
  onSuccess?: () => void
}

export function CreateSlotModal({
  isOpen,
  onClose,
  defaultDate,
  onSuccess,
}: CreateSlotModalProps) {
  const { t } = useTranslation('calendar')
  const [form, setForm] = useState<CreateTimeSlotRequest & { title: string }>({
    date: defaultDate,
    startTime: '10:00',
    endTime: '11:00',
    maxParticipants: 4,
    title: '',
  })

  const queryClient = useQueryClient()

  const createMutation = useMutation({
    mutationFn: adminApi.createTimeSlot,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
      onSuccess?.()
      onClose()
    },
  })

  const timeError = form.endTime <= form.startTime
    ? t('createSlot.endAfterStart')
    : null

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('createSlot.title')}>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (timeError) return
          const { title, ...rest } = form
          createMutation.mutate({ ...rest, title: title || undefined })
        }}
        className="space-y-4"
      >
        <div>
          <label className="block text-sm text-dark-400 mb-1">{t('createSlot.slotTitle')}</label>
          <input
            type="text"
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
            placeholder={t('createSlot.slotTitlePlaceholder')}
            maxLength={200}
            className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100"
          />
        </div>

        <div>
          <label className="block text-sm text-dark-400 mb-1">{t('createSlot.date')}</label>
          <input
            type="date"
            value={form.date}
            onChange={(e) => {
              setForm({ ...form, date: e.target.value })
              e.target.blur()
            }}
            className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100"
          />
        </div>

        <div>
          <div className="grid grid-cols-2 gap-4">
            <TimeScrollPicker
              label={t('createSlot.from')}
              value={form.startTime}
              onChange={(v) => setForm({ ...form, startTime: v })}
            />
            <TimeScrollPicker
              label={t('createSlot.to')}
              value={form.endTime}
              onChange={(v) => setForm({ ...form, endTime: v })}
            />
          </div>
          {timeError && (
            <p className="text-sm text-rose-400/80 mt-1">{timeError}</p>
          )}
        </div>

        <div>
          <label className="block text-sm text-dark-400 mb-1">{t('createSlot.maxParticipants')}</label>
          <input
            type="number"
            min={1}
            value={form.maxParticipants}
            onChange={(e) => setForm({ ...form, maxParticipants: parseInt(e.target.value) })}
            className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100"
          />
        </div>

        <div className="flex gap-3 pt-4">
          <Button type="submit" loading={createMutation.isPending} className="flex-1">
            {t('createSlot.submit')}
          </Button>
          <Button type="button" variant="ghost" onClick={onClose}>
            {t('createSlot.cancel')}
          </Button>
        </div>

        {createMutation.isError && (
          <p className="text-sm text-rose-400/80">
            {getErrorMessage(createMutation.error)}
          </p>
        )}
      </form>
    </Modal>
  )
}
