module Test.Main where

import Prelude

import Effect (Effect)
import Effect.Class.Console (log)
import FFI.ECC (genKey)
import Model.CyberAccount (CyberAccount(..), generateSecureRandom)

testCyberAccountJson :: CyberAccount -> Effect Unit
testCyberAccountJson a = log $ "cyberAccount的json字符串表示: " <> show a

main :: Effect Unit
main = do
  log "测试cyberAccount的show"
  cyberAccount <- pure $ MakeCyberAccount
    { seed: 1
    , privateKey: ""
    , publicKey: ""
    , cyberAddress: ""
    }
  testCyberAccountJson (cyberAccount)

  log "测试生成随机数"
  randomNum <- generateSecureRandom
  log $ show randomNum

  log "测试生成ECC密钥"
  key <- genKey
  log $ show key

  log "测试完成"
