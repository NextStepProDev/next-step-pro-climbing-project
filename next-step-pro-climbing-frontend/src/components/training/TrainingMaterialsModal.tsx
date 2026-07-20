import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { CalendarDays, ExternalLink, FileText, Image as ImageIcon, LayoutTemplate, Trash2 } from 'lucide-react'
import { Modal } from '../ui/Modal'
import { ConfirmModal } from '../ui/ConfirmModal'
import { LoadingSpinner } from '../ui/LoadingSpinner'
import { adminTrainingCalendarApi } from '../../api/client'
import { decodeHtmlEntities } from '../../utils/htmlEntities'
import type { TrainingMaterial } from '../../types'

const MATERIALS_KEY = ['admin', 'trainingMaterials']

function formatSize(bytes: number | null): string {
  if (bytes == null) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

interface TrainingMaterialsModalProps {
  isOpen: boolean
  onClose: () => void
}

/**
 * Central place to review and delete uploaded training materials without hunting through the
 * calendar. Deleting a file removes it from the owning training/template and from disk.
 */
export function TrainingMaterialsModal({ isOpen, onClose }: TrainingMaterialsModalProps) {
  const { t } = useTranslation('training')
  const queryClient = useQueryClient()
  const [confirmDelete, setConfirmDelete] = useState<TrainingMaterial | null>(null)

  const materialsQuery = useQuery({
    queryKey: MATERIALS_KEY,
    queryFn: adminTrainingCalendarApi.getMaterials,
    enabled: isOpen,
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => adminTrainingCalendarApi.deleteMaterial(id),
    onSuccess: () => {
      setConfirmDelete(null)
      queryClient.invalidateQueries({ queryKey: MATERIALS_KEY })
      // A deleted material also disappears from its training/template views
      queryClient.invalidateQueries({ queryKey: ['trainingCalendar'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'trainingTemplates'] })
    },
  })

  const materials = materialsQuery.data ?? []
  const totalBytes = materials.reduce((sum, m) => sum + (m.sizeBytes ?? 0), 0)

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('materials.title')} size="lg">
      {materialsQuery.isLoading ? (
        <div className="py-10 flex justify-center"><LoadingSpinner /></div>
      ) : materials.length === 0 ? (
        <p className="text-sm text-surface-400 py-6 text-center">{t('materials.empty')}</p>
      ) : (
        <div className="space-y-3">
          <p className="text-xs text-surface-500">
            {t('materials.summary', { count: materials.length, size: formatSize(totalBytes) })}
          </p>
          <ul className="space-y-2 max-h-[60vh] overflow-y-auto pr-1">
            {materials.map((m) => {
              const isImage = m.mimeType?.startsWith('image/')
              return (
                <li key={m.id} className="flex items-center gap-3 p-3 rounded-lg border border-surface-800 bg-surface-900">
                  {isImage
                    ? <ImageIcon className="w-4 h-4 shrink-0 text-primary-300" />
                    : <FileText className="w-4 h-4 shrink-0 text-rose-300" />}
                  <div className="min-w-0 flex-1">
                    <a
                      href={m.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-sm font-medium text-surface-100 hover:text-primary-300 inline-flex items-center gap-1 max-w-full"
                    >
                      <span className="truncate">{m.fileName ?? m.url}</span>
                      <ExternalLink className="w-3 h-3 shrink-0 opacity-70" />
                    </a>
                    <p className="text-xs text-surface-500 flex items-center gap-2 mt-0.5 min-w-0">
                      <span className="inline-flex items-center gap-1 shrink-0">
                        {m.ownerType === 'TEMPLATE'
                          ? <LayoutTemplate className="w-3 h-3" />
                          : <CalendarDays className="w-3 h-3" />}
                      </span>
                      <span className="truncate">{decodeHtmlEntities(m.ownerLabel)}</span>
                      {m.sizeBytes != null && <span className="shrink-0">· {formatSize(m.sizeBytes)}</span>}
                    </p>
                  </div>
                  <button
                    onClick={() => setConfirmDelete(m)}
                    className="p-1.5 rounded-lg text-surface-400 hover:text-rose-300 hover:bg-surface-800 transition-colors shrink-0"
                    title={t('materials.delete')}
                    aria-label={t('materials.delete')}
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </li>
              )
            })}
          </ul>
        </div>
      )}

      <ConfirmModal
        isOpen={confirmDelete !== null}
        onClose={() => setConfirmDelete(null)}
        onConfirm={() => { if (confirmDelete) deleteMutation.mutate(confirmDelete.id) }}
        title={t('materials.deleteConfirmTitle')}
        message={t('materials.deleteConfirmMessage')}
        variant="danger"
      />
    </Modal>
  )
}
