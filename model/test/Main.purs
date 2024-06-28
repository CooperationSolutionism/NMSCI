module Test.Main where

import Prelude

import Effect (Effect)
import Effect.Class.Console (log)
import FFI.ECC (genKey)
import Model.CyberAccount (CyberAccount(..), generateSecureRandom)

main :: Effect Unit
main = do
  log "===== 测试cyberAccount的show ====="
  cyberAccount <- pure $ MakeCyberAccount
    { seed: 1
    , privateKey: ""
    , publicKey: ""
    , cyberAddress: ""
    }
  log $ "cyberAccount的json字符串表示: " <> show cyberAccount

  log "===== 测试生成随机数 ====="
  randomNum <- generateSecureRandom
  log $ "生成的随机数: " <> show randomNum

  log "===== 测试生成ECC密钥 ====="
  key <- genKey
  log $ "生成的密钥: " <> show key

  log "===== 测试完成 ====="
