module Main where

import Prelude

import Effect (Effect)
import Effect.Console (log)
import Func.GenerateCyberAddress (generateCyberAddress)
import Func.GeneratePrivateKey (generatePrivateKey)
import Func.GeneratePublicKey (generatePublicKey)

main :: Effect Unit
main = do
  log $ generateCyberAddress $ generatePublicKey $ generatePrivateKey 9
