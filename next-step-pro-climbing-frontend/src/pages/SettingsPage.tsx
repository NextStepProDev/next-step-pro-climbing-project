import { useRef, useState, type ChangeEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext'
import { useTheme } from '../context/ThemeContext'
import { useToast } from '../context/ToastContext'
import { authApi } from '../api/client'
import { getErrorMessage } from '../utils/errors'
import { validatePhone, validateName } from '../utils/validation'
import { Camera, Moon, Sun, Monitor, Trash2 } from 'lucide-react'
import { Button } from '../components/ui/Button'
import { Modal } from '../components/ui/Modal'
import { Avatar } from '../components/ui/Avatar'
import { AvatarCropper } from '../components/ui/AvatarCropper'

const THEME_OPTIONS = [
  { value: 'dark' as const, labelKey: 'theme.dark', Icon: Moon },
  { value: 'light' as const, labelKey: 'theme.light', Icon: Sun },
  { value: 'system' as const, labelKey: 'theme.system', Icon: Monitor },
]

const LANGUAGES = [
  { code: 'pl', label: 'Polski' },
  { code: 'en', label: 'English' },
  { code: 'es', label: 'Español' },
] as const

export function SettingsPage() {
  const { t } = useTranslation('settings')
  const { user, logout, refreshUser } = useAuth()

  return (
    <div className="max-w-2xl mx-auto space-y-8">
      <h1 className="text-2xl font-bold text-surface-100">{t('title')}</h1>

      <AvatarSection user={user} onUpdated={refreshUser} />
      <ProfileSection key={user?.id ?? ''} user={user} onUpdated={refreshUser} />
      <ThemeSection />
      <LanguageSection />
      <ChangePasswordSection />
      <NotificationsSection
        enabled={user?.emailNotificationsEnabled ?? true}
        onUpdated={refreshUser}
      />
      <NewsletterSection
        subscribed={user?.newsletterSubscribed ?? false}
        onUpdated={refreshUser}
      />
      <DeleteAccountSection onDeleted={logout} hasPassword={user?.hasPassword ?? true} />
    </div>
  )
}

function AvatarSection({
  user,
  onUpdated,
}: {
  user: ReturnType<typeof useAuth>['user']
  onUpdated: () => Promise<void>
}) {
  const { t } = useTranslation('settings')
  const { showToast } = useToast()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [cropSrc, setCropSrc] = useState<string | null>(null)
  const [removing, setRemoving] = useState(false)

  const handleFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = '' // pozwól wybrać ten sam plik ponownie
    if (!file) return
    if (!file.type.startsWith('image/')) {
      showToast(t('avatar.invalidType'), 'error')
      return
    }
    setCropSrc(URL.createObjectURL(file))
  }

  const closeCropper = () => {
    if (cropSrc) URL.revokeObjectURL(cropSrc)
    setCropSrc(null)
  }

  const handleSave = async (blob: Blob) => {
    try {
      await authApi.uploadAvatar(blob)
      await onUpdated()
      showToast(t('avatar.saved'))
      closeCropper()
    } catch (err) {
      showToast(getErrorMessage(err), 'error')
      throw err
    }
  }

  const handleRemove = async () => {
    setRemoving(true)
    try {
      await authApi.deleteAvatar()
      await onUpdated()
      showToast(t('avatar.removed'))
    } catch (err) {
      showToast(getErrorMessage(err), 'error')
    } finally {
      setRemoving(false)
    }
  }

  return (
    <section className="bg-surface-900 rounded-lg border border-surface-800 p-6">
      <h2 className="text-lg font-semibold text-surface-100 mb-4">{t('avatar.title')}</h2>
      <div className="flex items-center gap-5">
        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          aria-label={t('avatar.change')}
          className="relative group rounded-full focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 focus:ring-offset-surface-900"
        >
          <Avatar
            src={user?.avatarUrl}
            name={user?.firstName}
            className="w-24 h-24"
            textClassName="text-3xl"
          />
          <span className="absolute inset-0 flex items-center justify-center rounded-full bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity">
            <Camera className="w-7 h-7 text-white" />
          </span>
        </button>
        <div className="flex flex-col gap-2">
          <Button type="button" variant="secondary" onClick={() => fileInputRef.current?.click()}>
            <Camera className="w-4 h-4 mr-1.5" />
            {user?.avatarUrl ? t('avatar.change') : t('avatar.upload')}
          </Button>
          {user?.avatarUrl && (
            <Button type="button" variant="secondary" onClick={handleRemove} loading={removing}>
              <Trash2 className="w-4 h-4 mr-1.5" />
              {t('avatar.remove')}
            </Button>
          )}
          <p className="text-xs text-surface-500">{t('avatar.formatHint')}</p>
        </div>
      </div>
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        onChange={handleFileChange}
        className="hidden"
      />
      {cropSrc && (
        <AvatarCropper imageSrc={cropSrc} onCancel={closeCropper} onSave={handleSave} />
      )}
    </section>
  )
}

function ProfileSection({
  user,
  onUpdated,
}: {
  user: ReturnType<typeof useAuth>['user']
  onUpdated: () => Promise<void>
}) {
  const { t } = useTranslation('settings')
  const { showToast } = useToast()
  const [firstName, setFirstName] = useState(user?.firstName ?? '')
  const [lastName, setLastName] = useState(user?.lastName ?? '')
  const [phone, setPhone] = useState(user?.phone ?? '')
  const [nickname, setNickname] = useState(user?.nickname ?? '')
  const [firstNameError, setFirstNameError] = useState<string | null>(null)
  const [lastNameError, setLastNameError] = useState<string | null>(null)
  const [phoneError, setPhoneError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: () => authApi.updateProfile(firstName, lastName, phone, nickname),
    onSuccess: async () => {
      await onUpdated()
      showToast(t('profile.success'))
    },
  })

  const missingData = !user?.firstName || !user?.lastName || !user?.phone

  return (
    <section className="bg-surface-900 rounded-lg border border-surface-800 p-6">
      <h2 className="text-lg font-semibold text-surface-100 mb-4">{t('profile.title')}</h2>

      {missingData && (
        <div className="mb-4 flex items-start gap-2 rounded-lg bg-amber-500/10 border border-amber-500/30 px-4 py-3 text-sm text-amber-400">
          <span>⚠</span>
          <span>{t('profile.missingData')}</span>
        </div>
      )}

      <form
        onSubmit={(e) => {
          e.preventDefault()
          const fnErr = validateName(firstName)
          if (fnErr) { setFirstNameError(fnErr); return }
          setFirstNameError(null)
          const lnErr = validateName(lastName)
          if (lnErr) { setLastNameError(lnErr); return }
          setLastNameError(null)
          const phoneErr = validatePhone(phone)
          if (phoneErr) { setPhoneError(phoneErr); return }
          setPhoneError(null)
          mutation.mutate()
        }}
        className="space-y-4"
      >
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm text-surface-400 mb-1">{t('profile.firstName')}</label>
            <input
              type="text"
              value={firstName}
              onChange={(e) => { setFirstName(e.target.value); setFirstNameError(null) }}
              placeholder={t('profile.firstNamePlaceholder')}
              className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
            {firstNameError && <p className="text-xs text-rose-400/80 mt-1">{firstNameError}</p>}
          </div>
          <div>
            <label className="block text-sm text-surface-400 mb-1">{t('profile.lastName')}</label>
            <input
              type="text"
              value={lastName}
              onChange={(e) => { setLastName(e.target.value); setLastNameError(null) }}
              placeholder={t('profile.lastNamePlaceholder')}
              className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
            {lastNameError && <p className="text-xs text-rose-400/80 mt-1">{lastNameError}</p>}
          </div>
        </div>
        <div>
          <label className="block text-sm text-surface-400 mb-1">{t('profile.phone')}</label>
          <input
            type="tel"
            value={phone}
            onChange={(e) => { setPhone(e.target.value); setPhoneError(null) }}
            placeholder={t('profile.phonePlaceholder')}
            className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
          {phoneError && <p className="text-xs text-rose-400/80 mt-1">{phoneError}</p>}
        </div>
        <div>
          <label className="block text-sm text-surface-400 mb-1">{t('profile.nickname')}</label>
          <input
            type="text"
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            placeholder={t('profile.nicknamePlaceholder')}
            className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
        </div>

        <Button type="submit" loading={mutation.isPending}>
          {t('profile.submit')}
        </Button>

        {mutation.isError && (
          <p className="text-sm text-rose-400/80">{getErrorMessage(mutation.error)}</p>
        )}
      </form>
    </section>
  )
}

function ThemeSection() {
  const { t } = useTranslation('settings')
  const { choice, setTheme } = useTheme()

  return (
    <section className="bg-surface-900 rounded-lg border border-surface-800 p-6">
      <h2 className="text-lg font-semibold text-surface-100 mb-1">{t('theme.title')}</h2>
      <p className="text-sm text-surface-400 mb-4">{t('theme.description')}</p>
      <div className="flex gap-3">
        {THEME_OPTIONS.map(({ value, labelKey, Icon }) => (
          <button
            key={value}
            onClick={() => setTheme(value)}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium border transition-colors ${
              choice === value
                ? 'bg-primary-500/15 border-primary-500/40 text-primary-400'
                : 'border-surface-700 text-surface-300 hover:bg-surface-800 hover:text-surface-100'
            }`}
          >
            <Icon className="w-4 h-4" />
            {t(labelKey)}
          </button>
        ))}
      </div>
    </section>
  )
}

function LanguageSection() {
  const { t, i18n } = useTranslation('settings')
  const { isAuthenticated } = useAuth()
  const { showToast } = useToast()

  const handleChange = (langCode: string) => {
    i18n.changeLanguage(langCode)
    if (isAuthenticated) {
      authApi.updateLanguage(langCode).catch(() => {})
    }
    showToast(t('language.success'))
  }

  return (
    <section className="bg-surface-900 rounded-lg border border-surface-800 p-6">
      <h2 className="text-lg font-semibold text-surface-100 mb-1">{t('language.title')}</h2>
      <p className="text-sm text-surface-400 mb-4">{t('language.description')}</p>
      <div className="flex gap-3">
        {LANGUAGES.map((lang) => (
          <button
            key={lang.code}
            onClick={() => handleChange(lang.code)}
            className={`px-4 py-2 rounded-lg text-sm font-medium border transition-colors ${
              i18n.language === lang.code
                ? 'bg-primary-500/15 border-primary-500/40 text-primary-400'
                : 'border-surface-700 text-surface-300 hover:bg-surface-800 hover:text-surface-100'
            }`}
          >
            {lang.label}
          </button>
        ))}
      </div>
    </section>
  )
}

function ChangePasswordSection() {
  const { t } = useTranslation('settings')
  const { showToast } = useToast()
  const [expanded, setExpanded] = useState(false)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [validationError, setValidationError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: () => authApi.changePassword(currentPassword, newPassword),
    onSuccess: () => {
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      setValidationError(null)
      setExpanded(false)
      showToast(t('changePassword.success'))
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setValidationError(null)

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

  const handleToggle = () => {
    setExpanded((prev) => {
      if (prev) {
        setCurrentPassword('')
        setNewPassword('')
        setConfirmPassword('')
        setValidationError(null)
      }
      return !prev
    })
  }

  return (
    <section className="bg-surface-900 rounded-lg border border-surface-800 p-6">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-surface-100">{t('changePassword.title')}</h2>
        <button
          type="button"
          onClick={handleToggle}
          className="text-sm text-primary-400 hover:text-primary-300 transition-colors"
        >
          {expanded ? t('changePassword.cancel') : t('changePassword.expand')}
        </button>
      </div>

      {expanded && (
        <form onSubmit={handleSubmit} className="space-y-4 mt-4">
          <div>
            <label className="block text-sm text-surface-400 mb-1">{t('changePassword.currentPassword')}</label>
            <input
              type="password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              required
              className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>
          <div>
            <label className="block text-sm text-surface-400 mb-1">{t('changePassword.newPassword')}</label>
            <input
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              required
              minLength={8}
              className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>
          <div>
            <label className="block text-sm text-surface-400 mb-1">{t('changePassword.confirmPassword')}</label>
            <input
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              required
              minLength={8}
              className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
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
        </form>
      )}
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
  const { showToast } = useToast()
  const mutation = useMutation({
    mutationFn: (newEnabled: boolean) => authApi.updateNotifications(newEnabled),
    onSuccess: async () => {
      await onUpdated()
      showToast(t('notifications.success'))
    },
  })

  return (
    <section className="bg-surface-900 rounded-lg border border-surface-800 p-6">
      <h2 className="text-lg font-semibold text-surface-100 mb-4">{t('notifications.title')}</h2>
      <div className="flex items-center justify-between">
        <div>
          <p className="text-surface-200">{t('notifications.reservations')}</p>
          <p className="text-sm text-surface-400">
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
          <div className="w-11 h-6 bg-surface-700 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-primary-500 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary-500"></div>
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

function NewsletterSection({
  subscribed,
  onUpdated,
}: {
  subscribed: boolean
  onUpdated: () => Promise<void>
}) {
  const { t } = useTranslation('settings')
  const { showToast } = useToast()
  const mutation = useMutation({
    mutationFn: (newSubscribed: boolean) => authApi.updateNewsletter(newSubscribed),
    onSuccess: async () => {
      await onUpdated()
      showToast(t('newsletter.success'))
    },
  })

  return (
    <section className="bg-surface-900 rounded-lg border border-surface-800 p-6">
      <h2 className="text-lg font-semibold text-surface-100 mb-4">{t('newsletter.title')}</h2>
      <div className="flex items-center justify-between">
        <div>
          <p className="text-surface-200">{t('newsletter.label')}</p>
          <p className="text-sm text-surface-400">{t('newsletter.description')}</p>
        </div>
        <label className="relative inline-flex items-center cursor-pointer">
          <input
            type="checkbox"
            checked={subscribed}
            onChange={(e) => mutation.mutate(e.target.checked)}
            disabled={mutation.isPending}
            className="sr-only peer"
          />
          <div className="w-11 h-6 bg-surface-700 peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-primary-500 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary-500"></div>
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

function DeleteAccountSection({ onDeleted, hasPassword }: { onDeleted: () => void; hasPassword: boolean }) {
  const { t } = useTranslation('settings')
  const navigate = useNavigate()
  const [showModal, setShowModal] = useState(false)
  const [password, setPassword] = useState('')

  const mutation = useMutation({
    mutationFn: () => authApi.deleteAccount(hasPassword ? password : null),
    onSuccess: () => {
      setShowModal(false)
      onDeleted()
      navigate('/')
    },
  })

  return (
    <section className="bg-surface-900 rounded-lg border border-rose-900/20 p-6">
      <h2 className="text-lg font-semibold text-rose-400/80 mb-2">{t('deleteAccount.title')}</h2>
      <p className="text-sm text-surface-400 mb-4">
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
          <p className="text-surface-300">
            {t('deleteAccount.modalMessage')}
          </p>
          {hasPassword && (
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder={t('deleteAccount.passwordPlaceholder')}
              className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 focus:outline-none focus:ring-2 focus:ring-rose-500/40"
            />
          )}
          <div className="flex gap-3">
            <Button
              variant="danger"
              className="flex-1"
              loading={mutation.isPending}
              onClick={() => mutation.mutate()}
              disabled={hasPassword && !password}
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
