module Func.GenerateCyberAddress where

import Entity.CyberAddress (CyberAddress)
import Entity.PublicKey (PublicKey)

-- // TODO: 使用比特币相同方案生成地址
-- 生成地址
generateCyberAddress :: PublicKey -> CyberAddress
generateCyberAddress _ = "ABCD"
