module Func.GeneratePrivateKey where

import Entity.PrivateKey (PrivateKey)
import Entity.Seed (Seed)
import Func.GenerateSecureRandom (generateSecureRandom)

-- 创建私钥
generatePrivateKey ∷ Seed → PrivateKey
generatePrivateKey = generateSecureRandom