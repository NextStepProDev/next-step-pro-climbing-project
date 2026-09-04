import type { InvitedUser } from '../types'

/**
 * Why a given invitee will or will not be written to.
 *
 * The send loop on the server skips three of these four, so this is the one place that decides
 * who counts as a recipient — the button label, the per-person line and the "save and send"
 * affordance all read it. Deriving the count from `notifiedAt` alone (as the button used to)
 * offers "send to 1" for someone the backend has always skipped and then answers "sent 0".
 *
 * Order matters: booking makes the invitation moot regardless of anything else, and a mail that
 * genuinely went out stays reported as sent even if the person later switched emails off.
 */
export type InviteStatus = 'booked' | 'sent' | 'notificationsOff' | 'pending'

export function inviteStatus(user: InvitedUser): InviteStatus {
  if (user.alreadyBooked) return 'booked'
  if (user.notifiedAt) return 'sent'
  // Explicit `false` only: a freshly picked invitee carries no server answer yet, and treating
  // that silence as "notifications off" would hide the send button for the very person the admin
  // has just added. The server has the final say and reports what it actually did.
  if (user.emailNotificationsEnabled === false) return 'notificationsOff'
  return 'pending'
}

/** Exactly the people a send would write to — the honest count for any "send" button. */
export function invitesAwaitingMail(invites: InvitedUser[]): InvitedUser[] {
  return invites.filter((u) => inviteStatus(u) === 'pending')
}

/**
 * People holding a mailed invitation for a term that is about to disappear. Cancelling a slot or
 * an event mails only confirmed reservations, so these are the ones whose calendar entry (the ICS
 * attached to the invitation) will outlive the term without a word from us.
 */
export function invitesAlreadyMailed(invites: InvitedUser[]): InvitedUser[] {
  return invites.filter((u) => inviteStatus(u) === 'sent')
}

/**
 * Whether "save and send invitations" is worth offering.
 *
 * Two ways to end up with recipients, and the second is easy to miss: somebody is waiting
 * already, OR the save is about to move the term — which clears the "sent" flag on every mailed
 * invitation server-side and turns those people back into recipients. Counting only the first
 * hides the button in precisely the situation the reset creates: an admin moving a slot whose
 * invitations had all gone out, who then has to reopen the form to re-send.
 */
export function canOfferSaveAndSend(invites: InvitedUser[], termChanged: boolean): boolean {
  if (invitesAwaitingMail(invites).length > 0) return true
  return termChanged && invitesAlreadyMailed(invites).length > 0
}
