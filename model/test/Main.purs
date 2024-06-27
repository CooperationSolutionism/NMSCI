module Test.Main where

import Prelude

import Effect (Effect)
import Effect.Class.Console (log)
import Model.CyberAccount (CyberAccount(..))

testCyberAccountJson :: CyberAccount -> Effect Unit
testCyberAccountJson a = log $ "cyberAccount的json字符串表示: " <> show a

main :: Effect Unit
main = do
  cyberAccount <- pure $ MakeCyberAccount
    { seed: 1
    , privateKey: 1
    , publicKey: 1
    , cyberAddress: "a"
    }
  testCyberAccountJson (cyberAccount)
  log "测试完成"
