import { useEffect } from 'react'
import { createPortal } from 'react-dom'
import { AlertTriangle } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Button } from './Button'

interface ConfirmModalProps {
  isOpen: boolean
  onClose: () => void
  onConfirm: () => void
  onSave?: () => void
  title: string
  message: string
  confirmText?: string
  cancelText?: string
  saveText?: string
  variant?: 'danger' | 'primary'
}

export function ConfirmModal({
  isOpen,
  onClose,
  onConfirm,
  onSave,
  title,
  message,
  confirmText,
  cancelText,
  saveText,
  variant = 'danger',
}: ConfirmModalProps) {
  const { t } = useTranslation('common')

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
      if (e.key === 'Escape') onClose()
    }
    if (isOpen) {
      document.addEventListener('keydown', handleEscape)
    }
    return () => document.removeEventListener('keydown', handleEscape)
  }, [isOpen, onClose])

  if (!isOpen) return null

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div
        className="absolute inset-0 bg-black/60 backdrop-blur-sm"
        onClick={onClose}
      />
      <div className="relative bg-surface-900 rounded-xl border border-surface-800 shadow-xl max-w-sm w-full mx-4">
        <div className="p-5">
          <div className="flex items-start gap-3 mb-4">
            <div className={`p-2 rounded-lg shrink-0 ${
              variant === 'danger'
                ? 'bg-rose-500/10 text-rose-400'
                : 'bg-primary-500/10 text-primary-400'
            }`}>
              <AlertTriangle className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-lg font-semibold text-surface-100">{title}</h3>
              <p className="text-sm text-surface-400 mt-1">{message}</p>
            </div>
          </div>
          <div className="flex gap-3 justify-end">
            <Button variant="ghost" size="sm" onClick={onClose}>
              {cancelText ?? t('cancel')}
            </Button>
            <Button
              variant={variant === 'danger' ? 'danger' : 'primary'}
              size="sm"
              onClick={() => {
                onConfirm()
                onClose()
              }}
            >
              {confirmText ?? t('confirm')}
            </Button>
            {onSave && (
              <Button
                variant="primary"
                size="sm"
                onClick={() => {
                  onSave()
                  onClose()
                }}
              >
                {saveText ?? t('save')}
              </Button>
            )}
          </div>
        </div>
      </div>
    </div>,
    document.body
  )
}
