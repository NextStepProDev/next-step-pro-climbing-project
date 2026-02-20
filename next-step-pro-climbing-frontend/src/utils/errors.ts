import i18n from '../i18n'

export function getErrorMessage(error: unknown): string {
  if (error instanceof TypeError && error.message === 'Failed to fetch') {
    return i18n.t('network', { ns: 'errors' })
  }
  if (error instanceof Error) return error.message
  if (typeof error === 'string') return error
  return i18n.t('unexpected', { ns: 'errors' })
}
