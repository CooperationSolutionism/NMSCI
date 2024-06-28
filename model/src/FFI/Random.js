import crypto from "crypto";

export function generateSecureRandom(byteLength) {
  return function () {
    const randomBytes = crypto.randomBytes(byteLength);
    let randomNumber = 0n;
    for (let i = 0; i < randomBytes.length; i++) {
      randomNumber = (randomNumber << 8n) | BigInt(randomBytes[i]);
    }
    return Number(randomNumber);
  };
}
