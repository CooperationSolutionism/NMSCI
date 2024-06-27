module Model.CyberAccount where

import Prelude

import BaseType.Uint32 (Uint32)

-- 随机数种子
type Seed = Uint32

-- 私钥
type PrivateKey = Uint32

-- 公钥
type PublicKey = Uint32

-- 地址
type CyberAddress = String

-- 账号
data CyberAccount = MakeCyberAccount {
  seed :: Seed,
  privateKey :: PrivateKey,
  publicKey :: PublicKey,
  cyberAddress :: CyberAddress
}

-- // TODO: 有没有现成的JSON序列化库？
-- 显示账号信息
instance showCyberAccount :: Show CyberAccount where
  show (MakeCyberAccount {seed, privateKey, publicKey, cyberAddress}) = "CyberAccount {"
    <> "seed: "
    <> show seed
    <> ", privateKey: "
    <> show privateKey
    <> ", publicKey: "
    <> show publicKey
    <> ", cyberAddress: "
    <> cyberAddress
    <> "}"

-- // TODO: 使用伪随机数生成器(CSPNG)生成随机数
-- 生成密码学安全的伪随机数
generateSecureRandom :: Seed -> Uint32
generateSecureRandom _ = 1

-- 生成私钥
generatePrivateKey ∷ Seed → PrivateKey
generatePrivateKey = generateSecureRandom

-- // TODO: 使用比特币相同方案生成公钥
-- 生成公钥
generatePublicKey :: PrivateKey -> PublicKey
generatePublicKey _ = 1

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
createCyberAccountBySeed seed = MakeCyberAccount {
  seed,
  privateKey: generatePrivateKey seed,
  publicKey: generatePublicKeyBySeed seed,
  cyberAddress: generateCyberAddressBySeed seed
}

-- 获取种子
getSeed :: CyberAccount -> Seed
getSeed (MakeCyberAccount {seed}) = seed

-- 获取地址
getCyberAddress :: CyberAccount -> CyberAddress
getCyberAddress (MakeCyberAccount {cyberAddress}) = cyberAddress

-- 获取公钥
getPublicKey :: CyberAccount -> PublicKey
getPublicKey (MakeCyberAccount {publicKey}) = publicKey

-- 获取私钥
getPrivateKey :: CyberAccount -> PrivateKey
getPrivateKey (MakeCyberAccount {privateKey}) = privateKey
