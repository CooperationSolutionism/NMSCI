module Model.CyberAccount where

import Prelude

import Data.Argonaut (stringify)
import Data.Argonaut.Encode.Encoders (encodeInt, encodeString)
import Data.Argonaut.Core (fromObject)
import Effect (Effect)
import FFI.Random as FFI
import Data.Tuple (Tuple(..))
import Foreign.Object as FO

-- 随机数种子
type Seed = Int

-- 私钥
type PrivateKey = String

-- 公钥
type PublicKey = String

-- 地址
type CyberAddress = String

-- 账号
data CyberAccount = MakeCyberAccount
  { seed :: Seed
  , privateKey :: PrivateKey
  , publicKey :: PublicKey
  , cyberAddress :: CyberAddress
  }

-- 显示账号信息
instance showCyberAccount :: Show CyberAccount where
  show (MakeCyberAccount { seed, privateKey, publicKey, cyberAddress }) = stringify $
    fromObject
      ( FO.fromFoldable
          [ Tuple "seed" (encodeInt seed)
          , Tuple "privateKey" (encodeString privateKey)
          , Tuple "publicKey" (encodeString publicKey)
          , Tuple "cyberAddress" (encodeString cyberAddress)
          ]
      )

-- 生成密码学安全的伪随机数
generateSecureRandom :: Effect Int
generateSecureRandom = FFI.generateSecureRandom 4

-- 生成私钥
generatePrivateKey ∷ Seed → PrivateKey
generatePrivateKey _ = ""

-- // TODO: 使用比特币相同方案生成公钥
-- 生成公钥
generatePublicKey :: PrivateKey -> PublicKey
generatePublicKey _ = ""

-- // TODO: 使用比特币相同方案生成地址
-- 生成地址
generateCyberAddress :: PublicKey -> CyberAddress
generateCyberAddress _ = "ABCD"

-- 通过种子生成公钥
generatePublicKeyBySeed :: Seed -> PublicKey
generatePublicKeyBySeed = generatePublicKey <<< generatePrivateKey

-- 通过种子生成地址
generateCyberAddressBySeed :: Seed -> CyberAddress
generateCyberAddressBySeed = generateCyberAddress <<< generatePublicKey <<< generatePrivateKey

-- 通过种子创建账号
createCyberAccountBySeed :: Seed -> CyberAccount
createCyberAccountBySeed seed = MakeCyberAccount
  { seed
  , privateKey: generatePrivateKey seed
  , publicKey: generatePublicKeyBySeed seed
  , cyberAddress: generateCyberAddressBySeed seed
  }

-- 获取种子
getSeed :: CyberAccount -> Seed
getSeed (MakeCyberAccount { seed }) = seed

-- 获取地址
getCyberAddress :: CyberAccount -> CyberAddress
getCyberAddress (MakeCyberAccount { cyberAddress }) = cyberAddress

-- 获取公钥
getPublicKey :: CyberAccount -> PublicKey
getPublicKey (MakeCyberAccount { publicKey }) = publicKey

-- 获取私钥
getPrivateKey :: CyberAccount -> PrivateKey
getPrivateKey (MakeCyberAccount { privateKey }) = privateKey
