import { describe, it, expect } from 'vitest'
import { inviteStatus, invitesAwaitingMail, invitesAlreadyMailed, canOfferSaveAndSend } from './inviteStatus'
import type { InvitedUser } from '../types'

function invitee(overrides: Partial<InvitedUser> = {}): InvitedUser {
  return {
    userId: 'u1',
    fullName: 'Anna Nowak',
    email: 'anna@example.com',
    notifiedAt: null,
    emailNotificationsEnabled: true,
    alreadyBooked: false,
    ...overrides,
  }
}

describe('inviteStatus', () => {
  it('should report someone still waiting for an invitation as pending', () => {
    expect(inviteStatus(invitee())).toBe('pending')
  })

  it('should report a mailed invitation as sent', () => {
    expect(inviteStatus(invitee({ notifiedAt: '2026-09-01T10:00:00Z' }))).toBe('sent')
  })

  it('should report someone who booked as booked even when the invitation was mailed', () => {
    // Booking makes the invitation moot, so this beats every other label — the send loop
    // skips them because they already got the ordinary confirmation.
    expect(inviteStatus(invitee({ notifiedAt: '2026-09-01T10:00:00Z', alreadyBooked: true })))
      .toBe('booked')
  })

  it('should name switched-off emails as the reason rather than reporting a bare "not sent"', () => {
    expect(inviteStatus(invitee({ emailNotificationsEnabled: false }))).toBe('notificationsOff')
  })

  it('should treat a freshly picked invitee with no server answer as mailable', () => {
    // An unsaved pick carries neither flag. Reading that silence as "notifications off" would
    // hide the send button for exactly the person the admin has just added.
    const justPicked = { userId: 'u9', fullName: 'Nowy', email: 'nowy@example.com' } as InvitedUser
    expect(inviteStatus(justPicked)).toBe('pending')
  })
})

describe('invitesAwaitingMail', () => {
  it('should count only the people a send would actually write to', () => {
    // The whole point: the button used to count by notifiedAt alone, so it offered "send to 3"
    // and then reported "sent 1" — the other two were skipped server-side every time.
    const list = [
      invitee({ userId: 'waiting' }),
      invitee({ userId: 'booked', alreadyBooked: true }),
      invitee({ userId: 'off', emailNotificationsEnabled: false }),
      invitee({ userId: 'mailed', notifiedAt: '2026-09-01T10:00:00Z' }),
    ]
    expect(invitesAwaitingMail(list).map((u) => u.userId)).toEqual(['waiting'])
  })
})

describe('invitesAlreadyMailed', () => {
  it('should list the people holding a mailed invitation for a term being cancelled', () => {
    const list = [
      invitee({ userId: 'mailed', notifiedAt: '2026-09-01T10:00:00Z' }),
      invitee({ userId: 'waiting' }),
      // Booked: the cancellation path does mail them, so they are not the ones at risk.
      invitee({ userId: 'booked', notifiedAt: '2026-09-01T10:00:00Z', alreadyBooked: true }),
    ]
    expect(invitesAlreadyMailed(list).map((u) => u.userId)).toEqual(['mailed'])
  })
})

describe('canOfferSaveAndSend', () => {
  const mailed = invitee({ notifiedAt: '2026-09-01T10:00:00Z' })

  it('should offer the action while somebody is waiting for an invitation', () => {
    expect(canOfferSaveAndSend([invitee()], false)).toBe(true)
  })

  it('should offer it for already-invited people when the term is being moved', () => {
    // Saving a moved term clears their "sent" flag server-side, so this very click turns them
    // back into recipients. Without this the button hid itself in exactly the case the reset
    // creates, sending the admin back for a second trip through the form.
    expect(canOfferSaveAndSend([mailed], true)).toBe(true)
  })

  it('should stay hidden for already-invited people when the term is untouched', () => {
    expect(canOfferSaveAndSend([mailed], false)).toBe(false)
  })

  it('should stay hidden when the only invitee can never be mailed', () => {
    const optedOut = invitee({ emailNotificationsEnabled: false })
    expect(canOfferSaveAndSend([optedOut], true)).toBe(false)
  })

  it('should stay hidden with no invitees at all', () => {
    expect(canOfferSaveAndSend([], true)).toBe(false)
  })
})
