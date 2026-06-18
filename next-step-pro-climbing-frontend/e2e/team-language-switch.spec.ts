import { test, expect } from '@playwright/test'

// Regression for the team (instructors/competitors) detail modal: switching the
// content language left a stale, language-specific member id in the URL, which
// (a) silently closed the open modal and (b) corrupted the next click because
// navigate(m.id) was route-relative and appended to the stale id, producing an
// invalid path so the modal never reopened. Fix: absolute navigation + re-map the
// open member to its translation-group counterpart on language change.

const GROUP = '11111111-1111-1111-1111-111111111111'
const PL_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
const EN_ID = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'

function instructor(id: string, language: string, firstName: string, lastName: string) {
  return {
    id,
    firstName,
    lastName,
    photoUrl: null,
    focalPointX: null,
    focalPointY: null,
    bio: `Bio ${language}`,
    certifications: 'Cert A\nCert B',
    badgeUrl: null,
    memberType: 'INSTRUCTOR',
    profile8aUrl: null,
    createdAt: '2026-01-01T00:00:00Z',
    language,
    translationGroupId: GROUP,
  }
}

test.describe('Team detail modal — language switch', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/instructors**', async (route) => {
      const lang = new URL(route.request().url()).searchParams.get('language') ?? 'pl'
      const body =
        lang === 'en'
          ? [instructor(EN_ID, 'en', 'John', 'Smith')]
          : lang === 'pl'
            ? [instructor(PL_ID, 'pl', 'Jan', 'Kowalski')]
            : []
      await route.fulfill({ json: body })
    })
  })

  test('clicking a member works even with a stale member id in the URL (the reported bug)', async ({ page }) => {
    // Reproduce the end-state of "switched language while a member was open":
    // EN content language but the URL still carries the Polish record id.
    await page.addInitScript(() => localStorage.setItem('i18nextLng', 'en'))
    await page.goto(`/team/instruktorzy/${PL_ID}`)

    // The stale PL id is not in the EN list → modal is silently closed.
    await expect(page.getByRole('dialog')).toHaveCount(0)

    // Clicking the (EN) member must open the modal — this was broken before.
    await page.getByRole('button', { name: 'John Smith' }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'John Smith' })).toBeVisible()
    await expect(page).toHaveURL(new RegExp(`/team/instruktorzy/${EN_ID}`))
  })

  test('pill happy path: open PL member, close, switch to EN, open EN member', async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('i18nextLng', 'pl'))
    await page.goto('/team/instruktorzy')

    await page.getByRole('button', { name: 'Jan Kowalski' }).click()
    await expect(page.getByRole('heading', { name: 'Jan Kowalski' })).toBeVisible()
    await expect(page).toHaveURL(new RegExp(PL_ID))

    await page.getByRole('button', { name: /close|zamknij/i }).click()
    await expect(page.getByRole('dialog')).toHaveCount(0)

    await page.getByRole('button', { name: 'EN', exact: true }).click()
    await page.getByRole('button', { name: 'John Smith' }).click()
    await expect(page.getByRole('heading', { name: 'John Smith' })).toBeVisible()
    await expect(page).toHaveURL(new RegExp(`/team/instruktorzy/${EN_ID}`))
  })
})
