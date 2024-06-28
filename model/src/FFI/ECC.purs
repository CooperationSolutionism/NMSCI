module FFI.ECC where

import Effect (Effect)

foreign import genKey :: Effect { privateKey :: String, publicKey :: String, publicKeyCompressed :: String }
