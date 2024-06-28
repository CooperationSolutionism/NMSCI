module Test.Main where

import Prelude

import Effect (Effect)
import Effect.Class.Console (log)
import Model.CyberAccount (CyberAccount(..), generateSecureRandom)

testCyberAccountJson :: CyberAccount -> Effect Unit
testCyberAccountJson a = log $ "cyberAccount的json字符串表示: " <> show a

main :: Effect Unit
main = do
  log "测试cyberAccount的show"
  cyberAccount <- pure $ MakeCyberAccount
    { seed: 1
    , privateKey: 1
    , publicKey: 1
    , cyberAddress: "a"
    }
  testCyberAccountJson (cyberAccount)

  log "测试生成随机数"
  value <- generateSecureRandom
  log $ show value

  log "测试完成"
