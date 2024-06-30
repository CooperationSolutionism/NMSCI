import crypto from "crypto";
import bs58 from "bs58";

export function genAddrByPubKey(pubKey) {
  // 将公钥进行哈希，使用SHA-256 和 RIPEMD-160
  const pubKeyBuffer = Buffer.from(pubKey, "hex");
  const hash1 = crypto.createHash("sha256").update(pubKeyBuffer).digest();
  const hash2 = crypto.createHash("ripemd160").update(hash1).digest();

  // 添加版本前缀
  const version = Buffer.from("00", "hex");
  const extendedHash = Buffer.concat([version, hash2]);

  // 计算校验和
  const checksum = crypto.createHash("sha256").update(extendedHash).digest();
  const doubleChecksum = crypto
    .createHash("sha256")
    .update(checksum)
    .digest()
    .slice(0, 4);

  // 5. 添加校验和到扩展哈希
  const addressBytes = Buffer.concat([extendedHash, doubleChecksum]);

  // 6. 使用Base58Check编码生成比特币地址
  const bitcoinAddress = bs58.encode(addressBytes);

  return bitcoinAddress;
}
