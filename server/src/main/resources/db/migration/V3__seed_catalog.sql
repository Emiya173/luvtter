-- V3: 默认邮票 / 信纸 / 贴纸 / 虚拟锚点种子数据

INSERT INTO stamps (id, code, name, tier, image_url, weight_capacity, speed_factor, is_default) VALUES
    ('00000000-0000-0000-0000-000000000101', 'plain',   '平信', 1, '/static/stamps/plain.png',   100, 1.00, TRUE),
    ('00000000-0000-0000-0000-000000000102', 'air',     '航空', 2, '/static/stamps/air.png',     150, 0.60, TRUE),
    ('00000000-0000-0000-0000-000000000103', 'express', '特快', 3, '/static/stamps/express.png', 200, 0.35, TRUE),
    ('00000000-0000-0000-0000-000000000104', 'rush',    '限时', 4, '/static/stamps/rush.png',    250, 0.15, TRUE);

INSERT INTO stationeries (id, code, name, background_url, thumbnail_url, is_default) VALUES
    ('00000000-0000-0000-0000-000000000201', 'classic', '经典素白', '/static/stationery/classic.png', '/static/stationery/classic_thumb.png', TRUE),
    ('00000000-0000-0000-0000-000000000202', 'linen',   '亚麻纹理', '/static/stationery/linen.png',   '/static/stationery/linen_thumb.png',   TRUE),
    ('00000000-0000-0000-0000-000000000203', 'kraft',   '牛皮纸',   '/static/stationery/kraft.png',   '/static/stationery/kraft_thumb.png',   TRUE);

INSERT INTO stickers (id, code, name, image_url, weight, is_default) VALUES
    ('00000000-0000-0000-0000-000000000301', 'wax_seal_red', '红色蜡封', '/static/stickers/wax_seal_red.png', 5, TRUE),
    ('00000000-0000-0000-0000-000000000302', 'flower_dried', '干花',     '/static/stickers/flower_dried.png', 3, TRUE);

INSERT INTO virtual_anchors (id, code, name, description, latitude, longitude, order_index) VALUES
    ('00000000-0000-0000-0000-000000000401', 'moon_far_side', '月球背面', '寂静无声的另一侧', 0.0,    180.0, 1),
    ('00000000-0000-0000-0000-000000000402', 'sahara',        '撒哈拉沙漠', '黄沙之上的星空',  23.4162, 25.6628, 2),
    ('00000000-0000-0000-0000-000000000403', 'mariana',       '马里亚纳海沟', '深海寂静',     11.3733, 142.5917, 3),
    ('00000000-0000-0000-0000-000000000404', 'antarctica',    '南极',       '永夜与极光',     -82.8628, 135.0,    4);
