import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from './testing/server'

// jsdom does not implement scrollIntoView; stub it for components that call it.
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {}
}

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
