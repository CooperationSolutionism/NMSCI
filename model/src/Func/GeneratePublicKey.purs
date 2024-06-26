module Func.GeneratePublicKey where

import Entity.PrivateKey (PrivateKey)
import Entity.PublicKey (PublicKey)

-- // TODO: 使用比特币相同方案生成公钥
-- 创建公钥
generatePublicKey :: PrivateKey -> PublicKey
generatePublicKey _ = 1