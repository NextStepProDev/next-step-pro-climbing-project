import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext'
import { authApi } from '../api/client'
import { getErrorMessage } from '../utils/errors'
import { Button } from '../components/ui/Button'
import { Modal } from '../components/ui/Modal'

export function SettingsPage() {
  const { t } = useTranslation('settings')
  const { user, logout, refreshUser } = useAuth()

  return (
    <div className="max-w-2xl mx-auto space-y-8">
      <h1 className="text-2xl font-bold text-dark-100">{t('title')}</h1>

      <ChangePasswordSection />
      <NotificationsSection
        enabled={user?.emailNotificationsEnabled ?? true}
        onUpdated={refreshUser}
      />
      <DeleteAccountSection onDeleted={logout} />
    </div>
  )
}

function ChangePasswordSection() {
  const { t } = useTranslation('settings')
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [validationError, setValidationError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)
  const successTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    return () => {
      if (successTimerRef.current) clearTimeout(successTimerRef.current)
    }
  }, [])

  const mutation = useMutation({
    mutationFn: () => authApi.changePassword(currentPassword, newPassword),
    onSuccess: () => {
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      setValidationError(null)
      setSuccess(true)
      if (successTimerRef.current) clearTimeout(successTimerRef.current)
      successTimerRef.current = setTimeout(() => setSuccess(false), 3000)
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setValidationError(null)
    setSuccess(false)

    if (newPassword.length < 8) {
      setValidationError(t('changePassword.tooShort'))
      return
    }
    if (newPassword !== confirmPassword) {
      setValidationError(t('changePassword.mismatch'))
      return
    }

    mutation.mutate()
  }

  return (
    <section className="bg-dark-900 rounded-lg border border-dark-800 p-6">
      <h2 className="text-lg font-semibold text-dark-100 mb-4">{t('changePassword.title')}</h2>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm text-dark-400 mb-1">{t('changePassword.currentPassword')}</label>
          <input
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            required
            className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
        </div>
        <div>
          <label className="block text-sm text-dark-400 mb-1">{t('changePassword.newPassword')}</label>
          <input
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            required
            minLength={4}
            className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
        </div>
        <div>
          <label className="block text-sm text-dark-400 mb-1">{t('changePassword.confirmPassword')}</label>
          <input
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            required
            minLength={4}
            className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
        </div>

        <Button type="submit" loading={mutation.isPending}>
          {t('changePassword.submit')}
        </Button>

        {validationError && (
          <p className="text-sm text-rose-400/80">{validationError}</p>
        )}
        {mutation.isError && (
          <p className="text-sm text-rose-400/80">
            {getErrorMessage(mutation.error)}
          </p>
        )}
        {success && (
          <p className="text-sm text-green-400">{t('changePassword.success')}</p>
        )}
      </form>
    </section>
  )
}

function NotificationsSection({
  enabled,
  onUpdated,
}: {
  enabled: boolean
  onUpdated: () => Promise<void>
}) {
  const { t } = useTranslation('settings')
  const mutation = useMutation({
    mutationFn: (newEnabled: boolean) => authApi.updateNotifications(newEnabled),
    onSuccess: () => onUpdated(),
  })

  return (
    <section className="bg-dark-900 rounded-lg border border-dark-800 p-6">
      <h2 className="text-lg font-semibold text-dark-100 mb-4">{t('notifications.title')}</h2>
      <div className="flex items-center justify-between">
        <div>
          <p className="text-dark-200">{t('notifications.reservations')}</p>
          <p className="text-sm text-dark-400">
            {t('notifications.description')}
          </p>
        </div>
        <label className="relative inline-flex items-center cursor-pointer">
          <input
            type="checkbox"
            checked={enabled}
            onChange={(e) => mutation.mutate(e.target.checked)}
            disabled={mutation.isPending}
            className="sr-only peer"
          />
          <div className="w-11 h-6 bg-dark-700 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-primary-500 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary-500"></div>
        </label>
      </div>
      {mutation.isError && (
        <p className="text-sm text-rose-400/80 mt-2">
          {getErrorMessage(mutation.error)}
        </p>
      )}
    </section>
  )
}

function DeleteAccountSection({ onDeleted }: { onDeleted: () => void }) {
  const { t } = useTranslation('settings')
  const navigate = useNavigate()
  const [showModal, setShowModal] = useState(false)
  const [password, setPassword] = useState('')

  const mutation = useMutation({
    mutationFn: () => authApi.deleteAccount(password),
    onSuccess: () => {
      setShowModal(false)
      onDeleted()
      navigate('/')
    },
  })

  return (
    <section className="bg-dark-900 rounded-lg border border-rose-900/20 p-6">
      <h2 className="text-lg font-semibold text-rose-400/80 mb-2">{t('deleteAccount.title')}</h2>
      <p className="text-sm text-dark-400 mb-4">
        {t('deleteAccount.description')}
      </p>
      <Button variant="danger" onClick={() => setShowModal(true)}>
        {t('deleteAccount.button')}
      </Button>

      <Modal
        isOpen={showModal}
        onClose={() => { setShowModal(false); setPassword('') }}
        title={t('deleteAccount.modalTitle')}
      >
        <div className="space-y-4">
          <p className="text-dark-300">
            {t('deleteAccount.modalMessage')}
          </p>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder={t('deleteAccount.passwordPlaceholder')}
            className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100 focus:outline-none focus:ring-2 focus:ring-rose-500/40"
          />
          <div className="flex gap-3">
            <Button
              variant="danger"
              className="flex-1"
              loading={mutation.isPending}
              onClick={() => mutation.mutate()}
              disabled={!password}
            >
              {t('deleteAccount.confirmDelete')}
            </Button>
            <Button
              variant="ghost"
              onClick={() => { setShowModal(false); setPassword('') }}
            >
              {t('deleteAccount.cancel')}
            </Button>
          </div>
          {mutation.isError && (
            <p className="text-sm text-rose-400/80">
              {getErrorMessage(mutation.error)}
            </p>
          )}
        </div>
      </Modal>
    </section>
  )
}
