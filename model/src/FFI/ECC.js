import secp256k1 from "secp256k1";
import { createHash } from "crypto";

export function genPubKeyByPrvKey(prvKeyStr) {
  var u8Data = strToU8(prvKeyStr);
  while (!secp256k1.privateKeyVerify(u8Data)) {
    var oldStr = u8ToStr(u8Data);
    var newStr = createHash("sha256").update(oldStr).digest("hex");
    u8Data = strToU8(newStr);
  }
  const key = secp256k1.publicKeyCreate(u8Data, true);
  return u8ToStr(key);
}

function u8ToStr(u) {
  return Buffer.from(u).toString("hex");
}
function strToU8(s) {
  return Uint8Array.from(Buffer.from(s, "hex"));
}
