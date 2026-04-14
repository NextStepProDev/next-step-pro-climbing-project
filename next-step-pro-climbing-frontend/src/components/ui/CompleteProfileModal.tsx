import { useState } from 'react'
import { createPortal } from 'react-dom'
import { useTranslation } from 'react-i18next'
import { UserCircle } from 'lucide-react'
import { useMutation } from '@tanstack/react-query'
import { authApi } from '../../api/client'
import { useAuth } from '../../context/AuthContext'
import { getErrorMessage } from '../../utils/errors'
import { Button } from './Button'

interface Props {
  onCompleted: () => void
  onClose: () => void
}

export function CompleteProfileModal({ onCompleted, onClose }: Props) {
  const { t } = useTranslation('settings')
  const { user, refreshUser } = useAuth()

  const [firstName, setFirstName] = useState(user?.firstName ?? '')
  const [lastName, setLastName] = useState(user?.lastName ?? '')
  const [phone, setPhone] = useState(user?.phone ?? '')

  const canSubmit = firstName.trim() !== '' && lastName.trim() !== '' && phone.trim() !== ''

  const mutation = useMutation({
    mutationFn: () =>
      authApi.updateProfile(firstName.trim(), lastName.trim(), phone.trim(), user?.nickname ?? ''),
    onSuccess: async () => {
      await refreshUser()
      onCompleted()
    },
  })

  return createPortal(
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
      <div className="relative bg-dark-900 border border-dark-700 rounded-xl p-6 max-w-sm w-full shadow-2xl">
        <div className="flex items-center justify-center w-12 h-12 bg-primary-500/15 rounded-full mx-auto mb-4">
          <UserCircle className="w-6 h-6 text-primary-400" />
        </div>

        <h2 className="text-lg font-semibold text-dark-100 text-center mb-2">
          {t('completeProfile.title')}
        </h2>
        <p className="text-sm text-dark-400 text-center mb-6 leading-relaxed">
          {t('completeProfile.description')}
        </p>

        <div className="space-y-3">
          <div>
            <label className="block text-sm text-dark-400 mb-1">{t('profile.firstName')}</label>
            <input
              type="text"
              value={firstName}
              onChange={(e) => setFirstName(e.target.value)}
              placeholder={t('profile.firstNamePlaceholder')}
              className="w-full bg-dark-800 border border-dark-700 rounded-lg px-3 py-2 text-dark-100 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 placeholder:text-dark-500"
            />
          </div>
          <div>
            <label className="block text-sm text-dark-400 mb-1">{t('profile.lastName')}</label>
            <input
              type="text"
              value={lastName}
              onChange={(e) => setLastName(e.target.value)}
              placeholder={t('profile.lastNamePlaceholder')}
              className="w-full bg-dark-800 border border-dark-700 rounded-lg px-3 py-2 text-dark-100 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 placeholder:text-dark-500"
            />
          </div>
          <div>
            <label className="block text-sm text-dark-400 mb-1">{t('profile.phone')}</label>
            <input
              type="tel"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder={t('profile.phonePlaceholder')}
              className="w-full bg-dark-800 border border-dark-700 rounded-lg px-3 py-2 text-dark-100 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 placeholder:text-dark-500"
            />
          </div>
        </div>

        {mutation.isError && (
          <p className="text-sm text-rose-400 mt-3">{getErrorMessage(mutation.error)}</p>
        )}

        <div className="flex flex-col gap-2 mt-5">
          <Button
            variant="primary"
            className="w-full"
            loading={mutation.isPending}
            disabled={!canSubmit}
            onClick={() => mutation.mutate()}
          >
            {t('completeProfile.submit')}
          </Button>
          <button
            onClick={onClose}
            disabled={mutation.isPending}
            className="w-full py-2 text-sm text-dark-400 hover:text-dark-200 transition-colors disabled:opacity-50"
          >
            {t('completeProfile.cancel')}
          </button>
        </div>
      </div>
    </div>,
    document.body
  )
}
