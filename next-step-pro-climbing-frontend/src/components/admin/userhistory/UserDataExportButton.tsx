import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Download, Loader2 } from 'lucide-react'
import { useToast } from '../../../context/ToastContext'
import { buildUserDataExport, downloadJson, userDataExportFileName } from './userDataExport'

/**
 * "Download data (JSON)" on the user card — the answer to a GDPR access request that arrived by
 * e-mail. A plain button, not a query: each click is logged on the server as handing somebody's
 * data out, so nothing may fetch it ahead of a deliberate click.
 */
export function UserDataExportButton({ userId, fullName }: { userId: string; fullName: string }) {
  const { t } = useTranslation('admin')
  const { showToast } = useToast()
  const [busy, setBusy] = useState(false)

  const handleClick = async () => {
    setBusy(true)
    try {
      const content = await buildUserDataExport(userId)
      downloadJson(content, userDataExportFileName(fullName))
    } catch {
      showToast(t('users.detail.exportFailed'), 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <button
      type="button"
      onClick={handleClick}
      disabled={busy}
      title={t('users.detail.exportHint')}
      className="inline-flex items-center gap-1 px-2 py-1 rounded text-xs border border-surface-700 text-surface-300 hover:text-surface-100 hover:bg-surface-800 transition-colors disabled:opacity-60"
    >
      {busy ? <Loader2 className="w-3 h-3 animate-spin" /> : <Download className="w-3 h-3" />}
      {t('users.detail.exportData')}
    </button>
  )
}
