import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { Check, X, AlertTriangle } from 'lucide-react'
import type { UserDetail } from '../../../types'

interface UserAccountTabProps {
  user: UserDetail
}

/**
 * The state of the account itself: verification, lockout, newsletter, consents, language, sign-in
 * method.
 *
 * <p>Read-only, deliberately. Every switch here already has a home — the athlete flag and the role
 * in the user list, the newsletter and logbook visibility in the person's own settings. A second
 * place to change them is a second place to keep in step, and this screen exists to answer
 * questions, not to become an alternative control panel.
 *
 * <p>Note what is missing: WHO changed a flag and WHEN. Admin actions on an account are logged
 * against the acting admin, not the account, so they cannot be listed here without a target-user
 * column. What is shown is the resulting state, which is what the question usually is.
 */
export function UserAccountTab({ user }: UserAccountTabProps) {
  const { t } = useTranslation('admin')

  // Instants, so they render in the viewer's own zone — these carry a timezone, unlike the
  // Warsaw wall-clock date labels elsewhere on this card.
  const stamp = (value: string | null) =>
    value ? format(new Date(value), 'dd.MM.yyyy HH:mm') : null

  return (
    <div className="grid gap-4 md:grid-cols-2">
      <Card title={t('users.detail.account.access')}>
        <Row label={t('users.detail.account.emailVerified')}>
          <Flag on={user.emailVerified} />
          {stamp(user.emailVerifiedAt) && (
            <span className="text-xs text-surface-500">{stamp(user.emailVerifiedAt)}</span>
          )}
        </Row>
        <Row label={t('users.detail.account.signInMethod')}>
          <span className="text-sm text-surface-200">
            {user.oauthProvider
              ? t('users.detail.account.oauth', { provider: user.oauthProvider })
              : user.hasPassword
                ? t('users.detail.account.password')
                : '—'}
          </span>
        </Row>
        <Row label={t('users.detail.account.role')}>
          <span className="text-sm text-surface-200">{user.role}</span>
        </Row>
        <Row label={t('users.detail.account.athlete')}>
          <Flag on={user.athlete} />
        </Row>
        <Row label={t('users.detail.account.failedLogins')}>
          <span className="text-sm text-surface-200">{user.failedLoginAttempts}</span>
        </Row>
        {user.accountLocked && (
          <Row label={t('users.detail.account.lockedUntil')}>
            <span className="inline-flex items-center gap-1 text-sm text-amber-300">
              <AlertTriangle className="w-3.5 h-3.5" />
              {stamp(user.lockedUntil)}
            </span>
          </Row>
        )}
      </Card>

      <Card title={t('users.detail.account.communication')}>
        <Row label={t('users.detail.account.emailNotifications')}>
          <Flag on={user.emailNotificationsEnabled} />
        </Row>
        <Row label={t('users.detail.account.newsletter')}>
          <Flag on={user.newsletterSubscribed} />
          {stamp(user.newsletterSubscribedAt) && (
            <span className="text-xs text-surface-500">{stamp(user.newsletterSubscribedAt)}</span>
          )}
        </Row>
        <Row label={t('users.detail.account.newsletterChoice')}>
          {/* Whether they ever answered the question at all — distinct from answering "no",
              and the difference matters for the consent trail. */}
          <Flag on={user.newsletterChoiceMade} />
        </Row>
        <Row label={t('users.detail.account.language')}>
          <span className="text-sm text-surface-200 uppercase">{user.preferredLanguage}</span>
        </Row>
      </Card>

      <Card title={t('users.detail.account.privacy')}>
        <Row label={t('users.detail.account.ascentsPublic')}>
          <Flag on={user.ascentsPublic} />
        </Row>
        <Row label={t('users.detail.account.trainingConsent')}>
          <Flag on={!!user.trainingConsentAt} />
          {stamp(user.trainingConsentAt) && (
            <span className="text-xs text-surface-500">{stamp(user.trainingConsentAt)}</span>
          )}
        </Row>
      </Card>

      <Card title={t('users.detail.account.details')}>
        <Row label={t('users.detail.account.nickname')}>
          <span className="text-sm text-surface-200">{user.nickname}</span>
        </Row>
        <Row label={t('users.detail.account.phone')}>
          <span className="text-sm text-surface-200">{user.phone || '—'}</span>
        </Row>
        <Row label={t('users.detail.account.createdAt')}>
          <span className="text-sm text-surface-200">{stamp(user.createdAt)}</span>
        </Row>
        <Row label={t('users.detail.account.updatedAt')}>
          <span className="text-sm text-surface-200">{stamp(user.updatedAt)}</span>
        </Row>
      </Card>
    </div>
  )
}

function Card({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="bg-surface-900 border border-surface-800 rounded-xl p-4">
      <h3 className="text-sm font-semibold text-surface-300 mb-3">{title}</h3>
      <dl className="space-y-2">{children}</dl>
    </div>
  )
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3 flex-wrap">
      <dt className="text-sm text-surface-400">{label}</dt>
      <dd className="flex items-center gap-2">{children}</dd>
    </div>
  )
}

function Flag({ on }: { on: boolean }) {
  const { t } = useTranslation('admin')
  return on
    ? <Check className="w-4 h-4 text-green-400" aria-label={t('users.detail.account.yes')} />
    : <X className="w-4 h-4 text-surface-600" aria-label={t('users.detail.account.no')} />
}
