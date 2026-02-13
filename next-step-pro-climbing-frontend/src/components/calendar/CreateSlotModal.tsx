import { useState } from 'react'
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
    ? 'Godzina zakończenia musi być późniejsza niż rozpoczęcia'
    : null

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Dodaj nowy termin">
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
          <label className="block text-sm text-dark-400 mb-1">Tytuł (np. "Trening na ściance")</label>
          <input
            type="text"
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
            placeholder="Tytuł terminu (widoczny dla klientów)"
            maxLength={200}
            className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100"
          />
        </div>

        <div>
          <label className="block text-sm text-dark-400 mb-1">Data</label>
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
              label="Od"
              value={form.startTime}
              onChange={(v) => setForm({ ...form, startTime: v })}
            />
            <TimeScrollPicker
              label="Do"
              value={form.endTime}
              onChange={(v) => setForm({ ...form, endTime: v })}
            />
          </div>
          {timeError && (
            <p className="text-sm text-rose-400/80 mt-1">{timeError}</p>
          )}
        </div>

        <div>
          <label className="block text-sm text-dark-400 mb-1">Maks. uczestników</label>
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
            Utwórz termin
          </Button>
          <Button type="button" variant="ghost" onClick={onClose}>
            Anuluj
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
