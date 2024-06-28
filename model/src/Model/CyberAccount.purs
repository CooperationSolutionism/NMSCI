module Model.CyberAccount where

import Prelude

import Data.Argonaut (stringify)
import Data.Argonaut.Encode.Encoders (encodeString)
import Data.Argonaut.Core (fromObject)
import Data.Tuple (Tuple(..))
import Foreign.Object as FO

-- 随机私钥
type PrivateKey = String

-- 公钥
type PublicKey = String

-- 地址
type CyberAddress = String

-- 账号
data CyberAccount = MakeCyberAccount
  { privateKey :: PrivateKey
  , publicKey :: PublicKey
  , cyberAddress :: CyberAddress
  }

-- 显示账号信息
instance showCyberAccount :: Show CyberAccount where
  show (MakeCyberAccount { privateKey, publicKey, cyberAddress }) = stringify $
    fromObject
      ( FO.fromFoldable
          [ Tuple "privateKey" (encodeString privateKey)
          , Tuple "publicKey" (encodeString publicKey)
          , Tuple "cyberAddress" (encodeString cyberAddress)
          ]
      )

-- // TODO: 使用比特币相同方案生成公钥
-- 生成公钥
generatePublicKey :: PrivateKey -> PublicKey
generatePublicKey _ = ""

-- // TODO: 使用比特币相同方案生成地址
-- 生成地址
generateCyberAddress :: PublicKey -> CyberAddress
generateCyberAddress _ = "ABCD"

-- 通过私钥生成地址
generateCyberAddressByPrivateKey :: PrivateKey -> CyberAddress
generateCyberAddressByPrivateKey = generateCyberAddress <<< generatePublicKey

-- 通过私钥创建账号
createCyberAccountByPrivateKey :: PrivateKey -> CyberAccount
createCyberAccountByPrivateKey privateKey = MakeCyberAccount
  { privateKey
  , publicKey: generatePublicKey privateKey
  , cyberAddress: generateCyberAddressByPrivateKey privateKey
  }

-- 获取地址
getCyberAddress :: CyberAccount -> CyberAddress
getCyberAddress (MakeCyberAccount { cyberAddress }) = cyberAddress

-- 获取公钥
getPublicKey :: CyberAccount -> PublicKey
getPublicKey (MakeCyberAccount { publicKey }) = publicKey

-- 获取私钥
getPrivateKey :: CyberAccount -> PrivateKey
getPrivateKey (MakeCyberAccount { privateKey }) = privateKey
