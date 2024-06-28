module FFI.Random where

import Effect (Effect)

foreign import generateSecureRandom :: Int -> Effect Int
