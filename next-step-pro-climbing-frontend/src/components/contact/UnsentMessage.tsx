import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Eraser } from 'lucide-react'
import { Button } from '../ui/Button'
import { ConfirmModal } from '../ui/ConfirmModal'

/** The question {@link useLeaveGuard} asks when a chat message is still unsent. */
export function UnsentLeaveDialog({ isOpen, onStay, onLeave }: {
  isOpen: boolean
  onStay: () => void
  onLeave: () => void
}) {
  const { t } = useTranslation('common')
  return (
    <ConfirmModal
      isOpen={isOpen}
      onClose={onStay}
      onConfirm={onLeave}
      title={t('unsentMessage.leaveTitle')}
      message={t('unsentMessage.leaveMessage')}
      confirmText={t('unsentMessage.leave')}
      cancelText={t('unsentMessage.stay')}
      variant="danger"
    />
  )
}

/**
 * Empties the composer — text and staged files alike. Asks first: it sits next to Send, and a long
 * message lost to a slipped tap has no way back.
 */
export function ClearMessageButton({ onClear, disabled }: { onClear: () => void; disabled?: boolean }) {
  const { t } = useTranslation('common')
  const [confirming, setConfirming] = useState(false)
  return (
    <>
      <Button type="button" variant="ghost" onClick={() => setConfirming(true)} disabled={disabled}>
        <Eraser className="w-4 h-4 mr-1.5" aria-hidden="true" />
        {t('unsentMessage.clear')}
      </Button>
      <ConfirmModal
        isOpen={confirming}
        onClose={() => setConfirming(false)}
        onConfirm={onClear}
        title={t('unsentMessage.clearTitle')}
        message={t('unsentMessage.clearMessage')}
        confirmText={t('unsentMessage.clear')}
        variant="danger"
      />
    </>
  )
}
