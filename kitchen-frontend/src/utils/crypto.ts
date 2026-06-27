/**
 * P1-11: 本地队列加密工具
 * 使用 XOR 混淆 + Base64 编码，防止 localStorage 中离线队列明文暴露
 */

const CRYPTO_KEY = 'smart-kitchen-terminal-v1'

function xorTransform(data: string, key: string): string {
  let result = ''
  for (let i = 0; i < data.length; i++) {
    result += String.fromCharCode(data.charCodeAt(i) ^ key.charCodeAt(i % key.length))
  }
  return result
}

export const encrypt = (data: string): string => {
  const xored = xorTransform(data, CRYPTO_KEY)
  return btoa(unescape(encodeURIComponent(xored)))
}

export const decrypt = (cipher: string): string => {
  const decoded = decodeURIComponent(escape(atob(cipher)))
  return xorTransform(decoded, CRYPTO_KEY)
}
