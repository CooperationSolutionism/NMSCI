module Logic.CreateCyberAccount where

import Prelude

import Entity.CyberAccount (CyberAccount)
import Entity.CyberAddress (CyberAddress)
import Entity.PublicKey (PublicKey)
import Entity.Seed (Seed)
import Func.GenerateCyberAddress (generateCyberAddress)
import Func.GeneratePrivateKey (generatePrivateKey)
import Func.GeneratePublicKey (generatePublicKey)

generatePublicKeyBySeed :: Seed -> PublicKey
generatePublicKeyBySeed = generatePublicKey <<< generatePrivateKey 

generateCyberAddressBySeed :: Seed -> CyberAddress
generateCyberAddressBySeed = generateCyberAddress <<< generatePublicKey <<< generatePrivateKey

-- 创建一个赛博账号
createCyberAccount :: Seed -> CyberAccount
createCyberAccount seed = {
  privateKey: generatePrivateKey seed,
  publicKey: generatePublicKeyBySeed seed,
  cyberAddress: generateCyberAddressBySeed seed
}