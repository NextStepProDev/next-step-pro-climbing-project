import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ClipboardList, Clock, Flame, Paperclip, Pencil, Plus, Trash2 } from 'lucide-react'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { ConfirmModal } from '../ui/ConfirmModal'
import { LoadingSpinner } from '../ui/LoadingSpinner'
import { TrainingTemplateForm } from './TrainingTemplateForm'
import { adminTrainingCalendarApi } from '../../api/client'
import { decodeHtmlEntities } from '../../utils/htmlEntities'
import { useChildDirty } from '../../hooks/useChildDirty'
import type { TrainingTemplate } from '../../types'

const TEMPLATES_KEY = ['admin', 'trainingTemplates']

interface TrainingTemplatesModalProps {
  isOpen: boolean
  onClose: () => void
}

/** Coach's reusable template library: list + create/edit/delete. Shared across all athletes. */
export function TrainingTemplatesModal({ isOpen, onClose }: TrainingTemplatesModalProps) {
  const { t } = useTranslation('training')
  const queryClient = useQueryClient()
  // null = list view; 'new' or a template = form view
  const [editing, setEditing] = useState<TrainingTemplate | 'new' | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<TrainingTemplate | null>(null)
  // Keyed on the FORM being up, not the modal: browsing the template list must stay closable
  // without a prompt, and the flag has to reset every time the form opens or is left.
  const [isDirty, reportDirty] = useChildDirty(editing !== null)

  const templatesQuery = useQuery({
    queryKey: TEMPLATES_KEY,
    queryFn: adminTrainingCalendarApi.getTemplates,
    enabled: isOpen,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: TEMPLATES_KEY })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => adminTrainingCalendarApi.deleteTemplate(id),
    onSuccess: () => { setConfirmDelete(null); invalidate() },
  })

  const templates = templatesQuery.data ?? []

  return (
    <Modal
      isOpen={isOpen}
      onClose={() => { setEditing(null); onClose() }}
      title={editing ? (editing === 'new' ? t('templates.add') : t('templates.edit')) : t('templates.title')}
      size="lg"
      confirmClose={isDirty}
    >
      {editing ? (
        <TrainingTemplateForm
          onDirtyChange={reportDirty}
          template={editing === 'new' ? null : editing}
          onDone={() => { setEditing(null); invalidate() }}
          onCancel={() => setEditing(null)}
        />
      ) : templatesQuery.isLoading ? (
        <div className="py-10 flex justify-center"><LoadingSpinner /></div>
      ) : (
        <div className="space-y-3">
          <Button variant="primary" size="sm" onClick={() => setEditing('new')}>
            <Plus className="w-4 h-4 mr-1" />
            {t('templates.add')}
          </Button>

          {templates.length === 0 ? (
            <p className="text-sm text-surface-400 py-4 text-center">{t('templates.empty')}</p>
          ) : (
            <ul className="space-y-2">
              {templates.map((tpl) => (
                <li key={tpl.id} className="flex items-center gap-3 p-3 rounded-lg border border-surface-800 bg-surface-900">
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-surface-100 truncate">{decodeHtmlEntities(tpl.title)}</p>
                    <p className="text-xs text-surface-500 flex items-center gap-3 mt-0.5">
                      {/* A task template has no duration to show, so it shows what it does have:
                          that it is a task, and its ceiling when one is set. */}
                      {tpl.kind === 'TASK' ? (
                        <>
                          <span className="inline-flex items-center gap-1">
                            <ClipboardList className="w-3 h-3" />{t('form.kind.TASK')}
                          </span>
                          {tpl.targetCalories != null && (
                            <span className="inline-flex items-center gap-1">
                              <Flame className="w-3 h-3" />{tpl.targetCalories} kcal
                            </span>
                          )}
                        </>
                      ) : (
                        <span className="inline-flex items-center gap-1">
                          <Clock className="w-3 h-3" />{tpl.defaultDurationMinutes} min
                        </span>
                      )}
                      {tpl.attachments.length > 0 && (
                        <span className="inline-flex items-center gap-1">
                          <Paperclip className="w-3 h-3" />{tpl.attachments.length}
                        </span>
                      )}
                    </p>
                  </div>
                  <button
                    onClick={() => setEditing(tpl)}
                    className="p-1.5 rounded-lg text-surface-400 hover:text-surface-100 hover:bg-surface-800 transition-colors"
                    title={t('templates.edit')}
                  >
                    <Pencil className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => setConfirmDelete(tpl)}
                    className="p-1.5 rounded-lg text-surface-400 hover:text-rose-300 hover:bg-surface-800 transition-colors"
                    title={t('templates.delete')}
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      <ConfirmModal
        isOpen={confirmDelete !== null}
        onClose={() => setConfirmDelete(null)}
        onConfirm={() => { if (confirmDelete) deleteMutation.mutate(confirmDelete.id) }}
        title={t('templates.deleteConfirmTitle')}
        message={t('templates.deleteConfirmMessage')}
        variant="danger"
      />
    </Modal>
  )
}
