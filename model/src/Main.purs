module Main where

import Prelude

import Effect (Effect)
import Effect.Console (log)
import Model.CyberAccount (createCyberAccountByPrivateKey)

main :: Effect Unit
main = do
  log $ show $ createCyberAccountByPrivateKey "1"
