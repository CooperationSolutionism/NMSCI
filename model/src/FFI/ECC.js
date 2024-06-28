import crypto from "crypto";
import secp256k1 from "secp256k1";

export function genKey() {
  var privateKey;
  do {
    privateKey = crypto.randomBytes(32);
  } while (!secp256k1.privateKeyVerify(privateKey));

  const publicKey = secp256k1.publicKeyCreate(privateKey, false);
  const publicKeyCompressed = secp256k1.publicKeyCreate(privateKey, true);

  return {
    privateKey: privateKey.toString("hex"),
    publicKey: Buffer.from(publicKey).toString("hex"),
    publicKeyCompressed: Buffer.from(publicKeyCompressed).toString("hex"),
  };
}
