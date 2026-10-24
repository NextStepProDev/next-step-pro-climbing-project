import { describe, it, expect, beforeEach } from 'vitest'
import { DRAFT_TTL_MS, clearAllDrafts, pruneDrafts, readDraft, removeDraft, writeDraft } from './draftStorage'

const NOW = 1_800_000_000_000

describe('draftStorage', () => {
  beforeEach(() => localStorage.clear())

  it('should read back what was written for the same owner and scope', () => {
    writeDraft('u1', 'news:1', { savedAt: NOW, baseline: 'v1', data: { title: 'Hej' } })

    expect(readDraft('u1', 'news:1', NOW)).toEqual({ savedAt: NOW, baseline: 'v1', data: { title: 'Hej' } })
  })

  it('should never hand one account the draft of another', () => {
    writeDraft('u1', 'mail', { savedAt: NOW, baseline: null, data: 'secret' })

    expect(readDraft('u2', 'mail', NOW)).toBeNull()
  })

  it('should drop a draft past its life instead of offering it', () => {
    writeDraft('u1', 'mail', { savedAt: NOW, baseline: null, data: 'old' })

    expect(readDraft('u1', 'mail', NOW + DRAFT_TTL_MS + 1)).toBeNull()
    expect(localStorage.length).toBe(0)
  })

  it('should treat unreadable storage content as no draft and remove it', () => {
    localStorage.setItem('nsp_draft:u1:mail', '{not json')

    expect(readDraft('u1', 'mail', NOW)).toBeNull()
    expect(localStorage.getItem('nsp_draft:u1:mail')).toBeNull()
  })

  it('should remove a single draft', () => {
    writeDraft('u1', 'mail', { savedAt: NOW, baseline: null, data: 'x' })
    removeDraft('u1', 'mail')

    expect(readDraft('u1', 'mail', NOW)).toBeNull()
  })

  it('should prune other accounts and expired drafts on login, keeping the current owner\'s', () => {
    writeDraft('u1', 'mail', { savedAt: NOW, baseline: null, data: 'mine' })
    writeDraft('u1', 'news:1', { savedAt: NOW - DRAFT_TTL_MS - 1, baseline: null, data: 'stale' })
    writeDraft('u2', 'mail', { savedAt: NOW, baseline: null, data: 'theirs' })
    localStorage.setItem('nsp_theme', 'dark')

    pruneDrafts('u1', NOW)

    expect(readDraft('u1', 'mail', NOW)?.data).toBe('mine')
    expect(localStorage.getItem('nsp_draft:u1:news:1')).toBeNull()
    expect(localStorage.getItem('nsp_draft:u2:mail')).toBeNull()
    expect(localStorage.getItem('nsp_theme')).toBe('dark')
  })

  it('should clear every draft on logout and leave other storage alone', () => {
    writeDraft('u1', 'mail', { savedAt: NOW, baseline: null, data: 'a' })
    writeDraft('u2', 'mail', { savedAt: NOW, baseline: null, data: 'b' })
    localStorage.setItem('nsp_access_token', 't')

    clearAllDrafts()

    expect(localStorage.getItem('nsp_draft:u1:mail')).toBeNull()
    expect(localStorage.getItem('nsp_draft:u2:mail')).toBeNull()
    expect(localStorage.getItem('nsp_access_token')).toBe('t')
  })
})
