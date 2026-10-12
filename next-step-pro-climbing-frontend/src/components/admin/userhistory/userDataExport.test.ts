import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

vi.mock('../../../api/client', () => ({
  adminUserHistoryApi: { exportData: vi.fn() },
  adminSettlementsApi: { getPayerExport: vi.fn() },
}))

import { adminSettlementsApi, adminUserHistoryApi } from '../../../api/client'
import { buildUserDataExport, userDataExportFileName } from './userDataExport'

const exportData = vi.mocked(adminUserHistoryApi.exportData)
const getPayerExport = vi.mocked(adminSettlementsApi.getPayerExport)

describe('buildUserDataExport', () => {
  beforeEach(() => {
    exportData.mockReset()
    getPayerExport.mockReset()
  })

  it('joins the money half into the same file under its own key', async () => {
    exportData.mockResolvedValue({ schemaVersion: 1, account: { email: 'ola@example.com' } })
    getPayerExport.mockResolvedValue({ payments: [{ amount: 120 }] })

    const file = await buildUserDataExport('u1')

    expect(file).toEqual({
      schemaVersion: 1,
      account: { email: 'ola@example.com' },
      money: { payments: [{ amount: 120 }] },
    })
    expect(exportData).toHaveBeenCalledWith('u1')
    expect(getPayerExport).toHaveBeenCalledWith('u1')
  })

  // A file without its money section reads as a complete answer to a legal request.
  it('rejects instead of producing a partial file when either half fails', async () => {
    exportData.mockResolvedValue({ schemaVersion: 1 })
    getPayerExport.mockRejectedValue(new Error('500'))

    await expect(buildUserDataExport('u1')).rejects.toThrow('500')
  })
})

describe('userDataExportFileName', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-12T10:00:00Z'))
  })
  afterEach(() => vi.useRealTimers())

  it('names whose data it is and when, in ASCII', () => {
    expect(userDataExportFileName('Łucja Wójcik')).toBe('nsp-dane-lucja-wojcik-2026-10-12.json')
  })

  it('still produces a usable name when the person has no name to slug', () => {
    expect(userDataExportFileName('')).toBe('nsp-dane-uzytkownik-2026-10-12.json')
  })
})
