import { useEffect, useState, type ReactNode } from 'react'
import { X } from 'lucide-react'
import { createPortal } from 'react-dom'
import { useTranslation } from 'react-i18next'
import { useFocusTrap } from '../../utils/useFocusTrap'
import { Button } from './Button'

interface ModalProps {
  isOpen: boolean
  onClose: () => void
  title: string
  children: ReactNode
  size?: 'md' | 'lg' | 'xl'
  /**
   * When true, closing via backdrop click, the X button or Escape first asks
   * the user to confirm — used to guard against losing unsaved form input.
   * Pass the form's dirty flag so a pristine form still closes instantly.
   */
  confirmClose?: boolean
}

export function Modal({ isOpen, onClose, title, children, size = 'md', confirmClose = false }: ModalProps) {
  const { t } = useTranslation('common')
  const trapRef = useFocusTrap(isOpen)
  const [confirmingClose, setConfirmingClose] = useState(false)

  const requestClose = () => {
    if (confirmClose) setConfirmingClose(true)
    else onClose()
  }

  // Drop a pending confirmation whenever the open state flips, so it never
  // lingers into the next time the modal is shown (render-phase reset — the
  // supported pattern for adjusting state when a prop changes).
  const [prevOpen, setPrevOpen] = useState(isOpen)
  if (isOpen !== prevOpen) {
    setPrevOpen(isOpen)
    setConfirmingClose(false)
  }

  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden'
    } else {
      document.body.style.overflow = ''
    }
    return () => {
      document.body.style.overflow = ''
    }
  }, [isOpen])

  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return
      // While confirming, Escape backs out of the confirmation, not the modal.
      if (confirmingClose) setConfirmingClose(false)
      else requestClose()
    }
    if (isOpen) {
      document.addEventListener('keydown', handleEscape)
    }
    return () => document.removeEventListener('keydown', handleEscape)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, onClose, confirmClose, confirmingClose])

  if (!isOpen) return null

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black/60 backdrop-blur-sm"
        onClick={requestClose}
      />

      {/* Modal */}
      <div
        ref={trapRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-title"
        className={`relative bg-surface-900 rounded-xl border border-surface-800 shadow-xl w-full mx-4 max-h-[90vh] overflow-y-auto ${size === 'xl' ? 'max-w-4xl' : size === 'lg' ? 'max-w-3xl' : 'max-w-lg'}`}
      >
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-surface-800">
          <h2 id="modal-title" className="text-lg font-semibold text-surface-100">{title}</h2>
          <button
            onClick={requestClose}
            aria-label={title ? `Close ${title}` : 'Close'}
            className="text-surface-400 hover:text-surface-200 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="p-4">{children}</div>

        {/* Unsaved-changes confirmation */}
        {confirmingClose && (
          <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
            <div
              className="absolute inset-0 bg-black/60"
              onClick={() => setConfirmingClose(false)}
            />
            <div className="relative bg-surface-900 rounded-xl border border-surface-800 shadow-xl w-full max-w-sm p-5">
              <h3 className="text-base font-semibold text-surface-100 mb-2">{t('unsaved.title')}</h3>
              <p className="text-sm text-surface-400 mb-5">{t('unsaved.message')}</p>
              <div className="flex gap-3 justify-end">
                <Button variant="ghost" onClick={() => setConfirmingClose(false)}>
                  {t('unsaved.keep')}
                </Button>
                <Button variant="danger" onClick={onClose}>
                  {t('unsaved.discard')}
                </Button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>,
    document.body
  )
}
