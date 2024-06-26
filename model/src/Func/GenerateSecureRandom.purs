module Func.GenerateSecureRandom where

import BaseType.Uint32 (Uint32)
import Entity.Seed (Seed)

-- // TODO: 使用伪随机数生成器(CSPNG)生成随机数
-- 生成密码学安全的伪随机数
generateSecureRandom :: Seed -> Uint32
generateSecureRandom _ = 1
