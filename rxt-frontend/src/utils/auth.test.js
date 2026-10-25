import test from 'node:test'
import assert from 'node:assert/strict'

test('account state follows changes and logout from another tab', async () => {
  const data = new Map()
  globalThis.localStorage = {
    getItem: key => data.get(key) ?? null,
    setItem: (key, value) => data.set(key, String(value)),
    removeItem: key => data.delete(key)
  }
  globalThis.window = new EventTarget()
  const { authState, setAuth, clearAuth } = await import('./auth.js')
  setAuth('old-token', { id: 1, role: 'farmer' })
  localStorage.setItem('rxt_token', 'new-token')
  localStorage.setItem('rxt_user', JSON.stringify({ id: 2, role: 'admin' }))
  const event = new Event('storage')
  Object.defineProperty(event, 'key', { value: 'rxt_token' })
  window.dispatchEvent(event)
  assert.equal(authState.token, 'new-token')
  assert.equal(authState.user.id, 2)
  localStorage.removeItem('rxt_token')
  localStorage.removeItem('rxt_user')
  window.dispatchEvent(event)
  assert.equal(authState.token, '')
  assert.equal(authState.user, null)
  clearAuth()
})
