import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// Unmount anything rendered by a test so the next one starts on an empty document
afterEach(() => cleanup())
