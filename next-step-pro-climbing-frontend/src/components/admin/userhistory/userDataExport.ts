import { adminSettlementsApi, adminUserHistoryApi } from '../../../api/client'
import { slugify } from '../../../utils/slugify'
import { todayInWarsaw } from '../../../utils/calendarDate'

/**
 * One person's data as a single JSON file, for a GDPR access / portability request.
 *
 * Two requests, one file: money comes from the settlement endpoint because the settlement types do
 * not leave their package on the server — the same split the user card's money block makes.
 *
 * ⚠️ Both halves or nothing. `Promise.all` rejects on the first failure and no file is written:
 * a file missing its money section looks exactly like a complete answer to a legal request, and
 * nobody opening it would know otherwise.
 */
export async function buildUserDataExport(userId: string): Promise<Record<string, unknown>> {
  const [data, money] = await Promise.all([
    adminUserHistoryApi.exportData(userId),
    adminSettlementsApi.getPayerExport(userId),
  ])
  return { ...data, money }
}

/** `nsp-dane-ola-nowak-2026-10-12.json` — whose and when, readable in a mail attachment list. */
export function userDataExportFileName(fullName: string): string {
  return `nsp-dane-${slugify(fullName) || 'uzytkownik'}-${todayInWarsaw()}.json`
}

export function downloadJson(content: unknown, fileName: string): void {
  const blob = new Blob([JSON.stringify(content, null, 2)], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  a.click()
  URL.revokeObjectURL(url)
}
