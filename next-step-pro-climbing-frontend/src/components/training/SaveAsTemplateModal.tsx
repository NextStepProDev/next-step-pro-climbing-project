import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Modal } from '../ui/Modal'
import { useModalClose } from '../ui/modalClose'
import { useToast } from '../../context/ToastContext'
import { TrainingTemplateForm, type TemplateDraft } from './TrainingTemplateForm'
import { useChildDirty } from '../../hooks/useChildDirty'

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
  // Before the early return: hooks cannot be skipped, and `draft` flipping back to a value is
  // what marks a fresh open here — this modal has no `isOpen` of its own.
  const [isDirty, reportDirty] = useChildDirty(!!draft)

  if (!draft) return null

  return (
    <Modal isOpen onClose={onClose} title={t('templates.saveAs')} size="lg" confirmClose={isDirty}>
      <GuardedTemplateForm
        onDirtyChange={reportDirty}
        draft={draft}
        onDone={() => {
          queryClient.invalidateQueries({ queryKey: ['admin', 'trainingTemplates'] })
          showToast(t('templates.savedAsTemplate'))
          onClose()
        }}
        onClose={onClose}
      />
    </Modal>
  )
}

/**
 * Routes the form's own "Cancel" through the modal's guard.
 *
 * ⚠️ Done HERE and not inside `TrainingTemplateForm`, even though that is where the button lives:
 * the same form is also the editor in `TrainingTemplatesModal`, and there "Cancel" means *go back
 * to the list*, not *close the modal*. Wiring `useModalClose()` into the form itself once shut the
 * whole library instead of stepping back one screen. In this modal the two meanings coincide, so
 * only this caller may make the swap — and the hook has to be called from INSIDE `Modal`, which is
 * where the context lives.
 */
function GuardedTemplateForm({ onClose, ...props }: {
  onDirtyChange: (dirty: boolean) => void
  draft: TemplateDraft
  onDone: () => void
  onClose: () => void
}) {
  const guardedClose = useModalClose()
  return <TrainingTemplateForm {...props} onCancel={guardedClose ?? onClose} />
}
