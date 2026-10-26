declare module 'sm-crypto' {
  export const sm2: {
    doEncrypt(message: string | number[], publicKey: string, cipherMode?: 0 | 1): string
  }
}
