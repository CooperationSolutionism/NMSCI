module Main where

import Prelude

import Effect (Effect)
import Effect.Console (log)
import Model.CyberAccount (createCyberAccountBySeed)

main :: Effect Unit
main = do
  log $ show $ createCyberAccountBySeed 1
