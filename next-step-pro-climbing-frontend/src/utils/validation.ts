import i18n from '../i18n'

export function validatePassword(password: string, confirmPassword: string): string | null {
  if (password !== confirmPassword) {
    return i18n.t('validation.passwordsMismatch', { ns: 'errors' })
  }
  if (password.length < 8) {
    return i18n.t('validation.passwordTooShort', { ns: 'errors' })
  }
  return null
}
