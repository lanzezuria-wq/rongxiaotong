// 只替换仓库自带的随机演示图片；真实商品上传的地址保持原样。
const demoImages = { rice: 'rice', apple: 'apples', veg: 'vegetables', egg: 'eggs', babycabbage: 'vegetables', orange: 'oranges', coffee: 'coffee', pear: 'pears', soybean: 'soybeans', crab: 'crab', crab2: 'crab', lamb: 'lamb', hairtail: 'fish', goji: 'goji', millet: 'millet', mango: 'mango', yam: 'yam', chicken: 'chicken' }
export function productImage(url) {
  if (!url) return ''
  const match = /^https:\/\/picsum\.photos\/seed\/([^/]+)/.exec(url)
  if (!match) return url
  return demoImages[match[1]] ? `/images/${demoImages[match[1]]}.jpg` : ''
}
