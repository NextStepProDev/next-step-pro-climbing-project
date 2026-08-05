import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Modal } from '../ui/Modal'
import { useToast } from '../../context/ToastContext'
import { TrainingTemplateForm, type TemplateDraft } from './TrainingTemplateForm'

/**
 * "Save as template" from an entry's detail view: the library form, opened with the entry's
 * content already in it. Everything stays editable before saving, because the good name for a
 * library entry is rarely the name of the day it came from ("Siła — blok 1", not "Siła — wtorek").
 *
 * The result lands in a screen the coach is not looking at, so a toast confirms it — closing the
 * modal on its own would be indistinguishable from cancelling.
 */
export function SaveAsTemplateModal({ draft, onClose }: {
  draft: TemplateDraft | null
  onClose: () => void
}) {
  const { t } = useTranslation('training')
  const queryClient = useQueryClient()
  const { showToast } = useToast()

  if (!draft) return null

  return (
    <Modal isOpen onClose={onClose} title={t('templates.saveAs')} size="lg">
      <TrainingTemplateForm
        draft={draft}
        onDone={() => {
          queryClient.invalidateQueries({ queryKey: ['admin', 'trainingTemplates'] })
          showToast(t('templates.savedAsTemplate'))
          onClose()
        }}
        onCancel={onClose}
      />
    </Modal>
  )
}
