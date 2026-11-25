-- Регистрация закрыта: создаём готовые демо-аккаунты с BCrypt-хешами.
-- Пароли: demo@example.com / demopass123, student@example.com / qwerty123.
INSERT INTO users (id, email, password_hash)
VALUES
    ('00000000-0000-0000-0000-000000000001',
     'demo@example.com',
     '$2a$10$LKIV81hcrUXhjgZcfArYW..cFYaWHaXmTX7vh726/wp1JEkup4nfu'),
    ('00000000-0000-0000-0000-000000000002',
     'student@example.com',
     '$2a$10$JAslKCi0B6KXw/6YDOsmsOxBrANS8.CbGzNxkibF5nveJElIlCzwS')
ON CONFLICT (email) DO NOTHING;
