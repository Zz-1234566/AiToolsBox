-- =====================================================================
-- AI 模型系统级种子数据 (BYOK 测试1)
-- 适用版本：v1 (2026-10-12)
-- 说明：
--   1) 共 5 条 sys_ai_model 系统级配置 (3 个 minimax + 2 个 tencent)
--   2) api_key / api_key_encrypted / api_key_prefix 全部 NULL
--      —— 用户从 UI 自行配置 (BYOK)；本脚本不写明文 Key
--   3) capability 字段必填，resolver 用作三级优先级
--   4) on duplicate key update: 已存在的 model_code 仅刷新元数据，
--      绝不触碰任何 Key 字段（保护用户已配的 Key）
-- =====================================================================

INSERT INTO sys_ai_model
    (model_code, model_name, provider, capability, api_url, api_model,
     api_key, api_key_encrypted, api_key_prefix, is_default, status, sort_no)
VALUES
    ('minimax-text',  'MiniMax 文本 M2.7',  'minimax', 'text',
     'https://api.minimaxi.com/v1/text/chatcompletion_v2', 'MiniMax-M2.7',
     NULL, NULL, NULL, 1, 1, 10),

    ('minimax-vision', 'MiniMax 多模态 M3', 'minimax', 'vision',
     'https://api.minimaxi.com/v1/chat/completions', 'MiniMax-M3',
     NULL, NULL, NULL, 1, 1, 20),

    ('asr-minimax',   'MiniMax ASR',         'minimax', 'asr',
     'https://api.minimaxi.com/v1/speech_to_text', 'asr-1.0',
     NULL, NULL, NULL, 1, 1, 30),

    ('asr-tencent',   '腾讯云 ASR',          'tencent', 'asr',
     NULL, '16k_zh',
     NULL, NULL, NULL, 0, 1, 40),

    ('tencent-ocr',   '腾讯云 OCR',          'tencent', 'ocr',
     NULL, 'GeneralAccurateOCR',
     NULL, NULL, NULL, 0, 1, 50)
ON DUPLICATE KEY UPDATE
    model_name = VALUES(model_name),
    provider   = VALUES(provider),
    capability = VALUES(capability),
    api_url    = VALUES(api_url),
    api_model  = VALUES(api_model),
    is_default = VALUES(is_default),
    status     = VALUES(status),
    sort_no    = VALUES(sort_no);
-- 注意：on duplicate key update 故意不更新 api_key / api_key_encrypted / api_key_prefix
-- 防止覆盖任何已配置/将来通过 UI 写入的 Key
