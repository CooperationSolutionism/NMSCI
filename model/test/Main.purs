module Test.Main where

import Prelude

import Effect (Effect)
import Effect.Class.Console (log)
import Model.CyberAccount (CyberAccount(..), generateCyberAddressByPrivateKey, generatePublicKey)

-- 生成重复n次的字符串
replicateString :: String -> Int -> String
replicateString _ 0 = ""
replicateString x 1 = x
replicateString x n = x <> replicateString x (n - 1)

main :: Effect Unit
main = do
  log "===== cyberAccount的show ====="
  cyberAccount <- pure $ MakeCyberAccount
    { privateKey: ""
    , publicKey: ""
    , cyberAddress: ""
    }
  log $ "cyberAccount的json字符串表示: " <> show cyberAccount

  log "===== 从私钥生成公钥 ====="
  pubKey <- pure $ generatePublicKey $ replicateString "a" 64
  log $ "生成的公钥: " <> show pubKey

  log "===== 从公钥生成地址 ====="
  addr1 <- pure $ generateCyberAddressByPrivateKey $ replicateString "a" 64
  log $ "生成的地址: " <> show addr1

  log "===== 从私钥生成地址 ====="
  addr2 <- pure $ generateCyberAddressByPrivateKey $ replicateString "a" 64
  log $ "生成的地址: " <> show addr2

  log "===== 测试完成 ====="
