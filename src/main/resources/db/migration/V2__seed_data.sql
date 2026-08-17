-- =========================================================
-- Seed data
-- Default admin password is "ChangeMe123!" (BCrypt hash below).
-- CHANGE THIS PASSWORD IMMEDIATELY AFTER FIRST LOGIN IN PRODUCTION.
-- =========================================================

INSERT INTO users (username, email, password, role_type, is_active)
VALUES (
    'admin',
    'admin@streamhub.local',
    '$2b$10$A.u7m24ToSlT7N36LyqPVezUckU9gWPjhpMjYY.1wP9lw5NWoGOv6',
    'ADMIN',
    TRUE
);

INSERT INTO users (username, email, password, role_type, is_active)
VALUES (
    'analytics',
    'analytics@streamhub.local',
    '$2b$10$A.u7m24ToSlT7N36LyqPVezUckU9gWPjhpMjYY.1wP9lw5NWoGOv6',
    'ANALYTIC',
    TRUE
);

INSERT INTO categories (id, name) VALUES
    (gen_random_uuid(), 'Music'),
    (gen_random_uuid(), 'Education'),
    (gen_random_uuid(), 'Entertainment'),
    (gen_random_uuid(), 'Sports'),
    (gen_random_uuid(), 'Technology'),
    (gen_random_uuid(), 'Gaming');
