module Entity.CyberAccount where

import Entity.PrivateKey (PrivateKey)
import Entity.PublicKey (PublicKey)
import Entity.CyberAddress (CyberAddress)

-- 赛博账号
type CyberAccount = { 
  privateKey :: PrivateKey,
  publicKey :: PublicKey,
  cyberAddress :: CyberAddress
}