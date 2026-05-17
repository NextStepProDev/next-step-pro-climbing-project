import { useState, useMemo } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Search, CheckSquare, Square, Send, Users, User, Mail } from 'lucide-react'
import { useToast } from '../../context/ToastContext'
import { adminApi } from '../../api/client'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { Button } from '../../components/ui/Button'
import { ConfirmModal } from '../../components/ui/ConfirmModal'
import { RichTextEditor } from '../../components/ui/RichTextEditor'

type RecipientType = 'ALL' | 'NEWSLETTER' | 'SELECTED'

export function AdminMailPanel() {
  const { t } = useTranslation('admin')
  const { showToast } = useToast()

  const [recipientType, setRecipientType] = useState<RecipientType>('ALL')
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [search, setSearch] = useState('')
  const [subject, setSubject] = useState('')
  const [body, setBody] = useState('')
  const [showConfirm, setShowConfirm] = useState(false)

  const { data: users, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['admin', 'users'],
    queryFn: adminApi.getAllUsers,
  })

  const filteredUsers = useMemo(() => {
    if (!users) return []
    const q = search.toLowerCase().trim()
    if (!q) return users
    return users.filter(
      (u) =>
        u.firstName.toLowerCase().includes(q) ||
        u.lastName.toLowerCase().includes(q) ||
        u.email.toLowerCase().includes(q) ||
        `${u.firstName} ${u.lastName}`.toLowerCase().includes(q),
    )
  }, [users, search])

  const sendMutation = useMutation({
    mutationFn: adminApi.sendMail,
    onSuccess: (data) => {
      showToast(t('mail.successMessage', { count: data.recipientCount }))
      setSubject('')
      setBody('')
      setSelectedIds(new Set())
      setRecipientType('ALL')
    },
    onError: () => {
      showToast(t('mail.errorMessage'), 'error')
    },
  })

  const toggleUser = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const canSend =
    subject.trim().length > 0 &&
    body.trim().length > 0 &&
    (recipientType === 'ALL' || recipientType === 'NEWSLETTER' || selectedIds.size > 0)

  const handleSend = () => {
    if (!canSend) return
    setShowConfirm(true)
  }

  const handleConfirm = () => {
    setShowConfirm(false)
    sendMutation.mutate({
      recipientType,
      userIds: recipientType === 'SELECTED' ? Array.from(selectedIds) : undefined,
      subject: subject.trim(),
      body: body.trim(),
    })
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold text-dark-100">{t('mail.title')}</h2>
        <p className="text-dark-400 text-sm mt-1">{t('mail.subtitle')}</p>
      </div>


      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Left: Recipients */}
        <div className="bg-dark-800 border border-dark-700 rounded-lg p-5 space-y-4">
          <h3 className="font-semibold text-dark-200">{t('mail.recipientType')}</h3>

          {/* Type selector */}
          <div className="flex flex-wrap gap-3">
            <button
              onClick={() => setRecipientType('ALL')}
              className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                recipientType === 'ALL'
                  ? 'bg-primary-500/20 text-primary-400 border border-primary-500/40'
                  : 'bg-dark-700 text-dark-300 border border-dark-600 hover:border-dark-500'
              }`}
            >
              <Users className="w-4 h-4" />
              {t('mail.allUsers')}
            </button>
            <button
              onClick={() => setRecipientType('NEWSLETTER')}
              className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                recipientType === 'NEWSLETTER'
                  ? 'bg-primary-500/20 text-primary-400 border border-primary-500/40'
                  : 'bg-dark-700 text-dark-300 border border-dark-600 hover:border-dark-500'
              }`}
            >
              <Mail className="w-4 h-4" />
              {t('mail.newsletterUsers')}
            </button>
            <button
              onClick={() => setRecipientType('SELECTED')}
              className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                recipientType === 'SELECTED'
                  ? 'bg-primary-500/20 text-primary-400 border border-primary-500/40'
                  : 'bg-dark-700 text-dark-300 border border-dark-600 hover:border-dark-500'
              }`}
            >
              <User className="w-4 h-4" />
              {t('mail.selectedUsers')}
            </button>
          </div>

          {/* User list (SELECTED mode) */}
          {recipientType === 'SELECTED' && (
            <div className="space-y-2">
              {selectedIds.size > 0 && (
                <p className="text-xs text-primary-400 font-medium">
                  {t('mail.selectedCount', { count: selectedIds.size })}
                </p>
              )}
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-dark-400" />
                <input
                  type="text"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder={t('mail.searchUsers')}
                  className="w-full pl-9 pr-3 py-2 bg-dark-700 border border-dark-600 rounded-lg text-sm text-dark-200 placeholder-dark-500 focus:outline-none focus:border-primary-500"
                />
              </div>

              {isLoading ? (
                <div className="flex justify-center py-4"><LoadingSpinner /></div>
              ) : isError ? (
                <QueryError error={error} onRetry={refetch} />
              ) : (
                <div className="max-h-64 overflow-y-auto space-y-1 pr-1">
                  {filteredUsers.length === 0 ? (
                    <p className="text-dark-500 text-sm text-center py-4">{t('mail.noUsersFound')}</p>
                  ) : (
                    filteredUsers.map((user) => {
                      const checked = selectedIds.has(user.id)
                      return (
                        <button
                          key={user.id}
                          onClick={() => toggleUser(user.id)}
                          className={`w-full flex items-center gap-3 px-3 py-2 rounded-lg text-left transition-colors ${
                            checked
                              ? 'bg-primary-500/10 border border-primary-500/30'
                              : 'hover:bg-dark-700 border border-transparent'
                          }`}
                        >
                          {checked ? (
                            <CheckSquare className="w-4 h-4 text-primary-400 shrink-0" />
                          ) : (
                            <Square className="w-4 h-4 text-dark-500 shrink-0" />
                          )}
                          <div className="min-w-0">
                            <p className="text-sm text-dark-200 truncate">
                              {user.firstName} {user.lastName}
                            </p>
                            <p className="text-xs text-dark-500 truncate">{user.email}</p>
                          </div>
                        </button>
                      )
                    })
                  )}
                </div>
              )}
            </div>
          )}
        </div>

        {/* Right: Compose */}
        <div className="bg-dark-800 border border-dark-700 rounded-lg p-5 space-y-4">
          <h3 className="font-semibold text-dark-200">{t('mail.title')}</h3>

          <div>
            <label className="block text-sm text-dark-400 mb-1">{t('mail.subject')}</label>
            <input
              type="text"
              value={subject}
              onChange={(e) => setSubject(e.target.value)}
              placeholder={t('mail.subjectPlaceholder')}
              className="w-full px-3 py-2 bg-dark-700 border border-dark-600 rounded-lg text-sm text-dark-200 placeholder-dark-500 focus:outline-none focus:border-primary-500"
            />
          </div>

          <div>
            <label className="block text-sm text-dark-400 mb-1">{t('mail.body')}</label>
            <RichTextEditor
              value={body}
              onChange={setBody}
              placeholder={t('mail.bodyPlaceholder')}
              rows={21}
            />
          </div>

          <Button
            onClick={handleSend}
            disabled={!canSend || sendMutation.isPending}
            className="w-full flex items-center justify-center gap-2"
          >
            <Send className="w-4 h-4" />
            {sendMutation.isPending ? t('mail.sending') : t('mail.send')}
          </Button>
        </div>
      </div>

      <ConfirmModal
        isOpen={showConfirm}
        onClose={() => setShowConfirm(false)}
        onConfirm={handleConfirm}
        title={t('mail.confirmTitle')}
        message={
          recipientType === 'ALL'
            ? t('mail.confirmAllMessage')
            : recipientType === 'NEWSLETTER'
              ? t('mail.confirmNewsletterMessage')
              : t('mail.confirmMessage', { count: selectedIds.size })
        }
      />
    </div>
  )
}
