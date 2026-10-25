import test from 'node:test'
import assert from 'node:assert/strict'
import { productImage } from './images.js'

test('only known demo image seeds use local illustrative assets', () => {
  assert.equal(productImage('https://picsum.photos/seed/apple/600/400'), '/images/apples.jpg')
  assert.equal(productImage('https://picsum.photos/seed/crab2/600/400'), '/images/crab.jpg')
  assert.equal(productImage('https://picsum.photos/seed/unknown/600/400'), '')
})

test('real uploaded image URLs and empty values are preserved safely', () => {
  const url = 'https://example.org/uploads/farm-apples.jpg'
  assert.equal(productImage(url), url)
  assert.equal(productImage(null), '')
  assert.equal(productImage(''), '')
})
