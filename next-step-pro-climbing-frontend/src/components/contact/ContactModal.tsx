import { useTranslation } from 'react-i18next'
import { Modal } from '../ui/Modal'
import { useChildDirty } from '../../hooks/useChildDirty'
import { ContactForm } from './ContactForm'
import type { ContactContext } from '../../types'

interface ContactModalProps {
  isOpen: boolean
  onClose: () => void
  context: ContactContext
}

/**
 * The contact form in a modal, for the course page and the calendar. Shell + `{isOpen && <Form/>}`:
 * the form remounts on every open, so a sent form or a half-written message never comes back over
 * a different course or day, and the dirty flag travels up through useChildDirty.
 */
export function ContactModal({ isOpen, onClose, context }: ContactModalProps) {
  const { t } = useTranslation('common')
  const [isDirty, reportDirty] = useChildDirty(isOpen)

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('contactForm.modalTitle')} confirmClose={isDirty}>
      {isOpen && <ContactForm context={context} onDirtyChange={reportDirty} />}
    </Modal>
  )
}
