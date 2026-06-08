import { test, expect } from '@playwright/test'

// Smoke-level "golden path": exercises routing, navbar, footer and the public
// pages without needing the backend (no assertions on fetched data). Mirrors
// the fire-academy golden-path, adapted to climbing's routes and behaviour.
test.describe('Golden Path', () => {
  test('should load home with correct title', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveTitle(/Next Step Pro Climbing/)
  })

  test('should navigate from home to courses page', async ({ page }) => {
    await page.goto('/')
    const coursesLink = page.locator('a[href="/kursy"]').first()
    if (await coursesLink.isVisible()) {
      await coursesLink.click()
      await expect(page).toHaveURL(/\/kursy/)
    }
  })

  test('should load courses page', async ({ page }) => {
    await page.goto('/kursy')
    await expect(page).toHaveURL(/\/kursy/)
  })

  test('should load news page', async ({ page }) => {
    await page.goto('/aktualnosci')
    await expect(page).toHaveURL(/\/aktualnosci/)
  })

  test('should load calendar page', async ({ page }) => {
    await page.goto('/calendar')
    await expect(page).toHaveURL(/\/calendar/)
  })

  test('should load contact page', async ({ page }) => {
    await page.goto('/kontakt')
    await expect(page).toHaveURL(/\/kontakt/)
  })

  test('should navigate via navbar', async ({ page }) => {
    await page.goto('/')
    const navLinks = page.locator('nav a')
    const count = await navLinks.count()
    expect(count).toBeGreaterThanOrEqual(3)
  })

  test('should show footer', async ({ page }) => {
    await page.goto('/')
    await expect(page.locator('footer')).toBeVisible()
  })

  test('should show privacy policy page', async ({ page }) => {
    await page.goto('/polityka-prywatnosci')
    await expect(page.locator('h1, h2').first()).toBeVisible()
  })

  // climbing's AdminRoute redirects unauthenticated users to "/" (home),
  // unlike fire-academy which goes to /admin/login.
  test('should redirect /admin to home when not authenticated', async ({ page }) => {
    await page.goto('/admin')
    await expect(page).toHaveURL(/\/$/)
  })
})
