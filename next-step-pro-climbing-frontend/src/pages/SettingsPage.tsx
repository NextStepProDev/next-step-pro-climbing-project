import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { useAuth } from '../context/AuthContext'
import { authApi } from '../api/client'
import { getErrorMessage } from '../utils/errors'
import { Button } from '../components/ui/Button'
import { Modal } from '../components/ui/Modal'

export function SettingsPage() {
  const { user, logout, refreshUser } = useAuth()

  return (
    <div className="max-w-2xl mx-auto space-y-8">
      <h1 className="text-2xl font-bold text-dark-100">Ustawienia</h1>

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
      setValidationError('Hasło musi mieć co najmniej 8 znaków')
      return
    }
    if (newPassword !== confirmPassword) {
      setValidationError('Hasła nie są zgodne')
      return
    }

    mutation.mutate()
  }

  return (
    <section className="bg-dark-900 rounded-lg border border-dark-800 p-6">
      <h2 className="text-lg font-semibold text-dark-100 mb-4">Zmiana hasła</h2>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm text-dark-400 mb-1">Aktualne hasło</label>
          <input
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            required
            className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
        </div>
        <div>
          <label className="block text-sm text-dark-400 mb-1">Nowe hasło</label>
          <input
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            required
            minLength={8}
            className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
        </div>
        <div>
          <label className="block text-sm text-dark-400 mb-1">Potwierdź nowe hasło</label>
          <input
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            required
            minLength={8}
            className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
        </div>

        <Button type="submit" loading={mutation.isPending}>
          Zmień hasło
        </Button>

        {validationError && (
          <p className="text-sm text-red-400">{validationError}</p>
        )}
        {mutation.isError && (
          <p className="text-sm text-red-400">
            {getErrorMessage(mutation.error)}
          </p>
        )}
        {success && (
          <p className="text-sm text-green-400">Hasło zostało zmienione</p>
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
  const mutation = useMutation({
    mutationFn: (newEnabled: boolean) => authApi.updateNotifications(newEnabled),
    onSuccess: () => onUpdated(),
  })

  return (
    <section className="bg-dark-900 rounded-lg border border-dark-800 p-6">
      <h2 className="text-lg font-semibold text-dark-100 mb-4">Powiadomienia email</h2>
      <div className="flex items-center justify-between">
        <div>
          <p className="text-dark-200">Powiadomienia o rezerwacjach</p>
          <p className="text-sm text-dark-400">
            Potwierdzenia rezerwacji, anulowania i powiadomienia z listy rezerwowej
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
        <p className="text-sm text-red-400 mt-2">
          {getErrorMessage(mutation.error)}
        </p>
      )}
    </section>
  )
}

function DeleteAccountSection({ onDeleted }: { onDeleted: () => void }) {
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
    <section className="bg-dark-900 rounded-lg border border-red-900/30 p-6">
      <h2 className="text-lg font-semibold text-red-400 mb-2">Usuwanie konta</h2>
      <p className="text-sm text-dark-400 mb-4">
        Ta operacja jest nieodwracalna. Wszystkie Twoje rezerwacje zostaną anulowane,
        a dane konta trwale usunięte.
      </p>
      <Button variant="danger" onClick={() => setShowModal(true)}>
        Usuń konto
      </Button>

      <Modal
        isOpen={showModal}
        onClose={() => { setShowModal(false); setPassword('') }}
        title="Potwierdzenie usuwania konta"
      >
        <div className="space-y-4">
          <p className="text-dark-300">
            Aby potwierdzić usunięcie konta, wpisz swoje hasło:
          </p>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Twoje hasło"
            className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100 focus:outline-none focus:ring-2 focus:ring-red-500"
          />
          <div className="flex gap-3">
            <Button
              variant="danger"
              className="flex-1"
              loading={mutation.isPending}
              onClick={() => mutation.mutate()}
              disabled={!password}
            >
              Usuń konto na stałe
            </Button>
            <Button
              variant="ghost"
              onClick={() => { setShowModal(false); setPassword('') }}
            >
              Anuluj
            </Button>
          </div>
          {mutation.isError && (
            <p className="text-sm text-red-400">
              {getErrorMessage(mutation.error)}
            </p>
          )}
        </div>
      </Modal>
    </section>
  )
}
