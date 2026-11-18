/**
 * Splits the money received for one session evenly across the people who were there.
 *
 * The admin knows one number — what landed in his hand for the whole group — and the ledger wants
 * one charge and one payment per payer. This is the arithmetic between the two, kept apart from the
 * component so the rounding can be pinned by a test rather than eyeballed in a modal.
 *
 * - **Per head, not per booking.** A booking for two (`participants = 2`) takes two shares: the
 *   person who brought a friend paid for the friend. Decided with the owner 2026-11-18.
 * - **Whole cents, never floats.** 100 zł for three heads is 33.34 + 33.33 + 33.33, and the extra
 *   cent goes to the first heads in list order, so the shares always add up to exactly what was
 *   typed. Floats would hand back 99.99 and an `amount` the field cannot show.
 * - **Money already entered at this session counts towards the share.** Splitting twice — or after
 *   one person had paid on the spot — must not record that money a second time, so the payment is
 *   only what is still missing from the share (never negative: someone who already handed over more
 *   simply keeps the rest as credit, which the ledger shows).
 *
 * Who is left out (free, cancelled booking) is the caller's decision: this only splits what it gets.
 */
export interface SplitLine {
  key: string
  heads: number
  /** Payments already typed in at this session for this payer. */
  alreadyReceived: number
}

export interface SplitShare {
  key: string
  amount: number
  payment: number
}

export function splitReceived(total: number, lines: SplitLine[]): SplitShare[] | null {
  const totalCents = Math.round(total * 100)
  // A booking is never fewer than one person — a row with a zero headcount still stands for someone.
  const heads = lines.reduce((sum, line) => sum + Math.max(1, line.heads), 0)
  if (heads === 0 || totalCents <= 0) return null

  const base = Math.floor(totalCents / heads)
  let extraCents = totalCents - base * heads

  return lines.map((line) => {
    let cents = 0
    for (let i = 0; i < Math.max(1, line.heads); i++) {
      cents += base
      if (extraCents > 0) {
        cents += 1
        extraCents -= 1
      }
    }
    const missing = Math.max(cents - Math.round(line.alreadyReceived * 100), 0)
    return { key: line.key, amount: cents / 100, payment: missing / 100 }
  })
}
