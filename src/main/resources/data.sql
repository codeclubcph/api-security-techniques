-- Seed users
-- Passwords stored as plain text ⚠️ VULNERABILITY
INSERT INTO app_user (id, username, email, password, role) VALUES
  (1, 'alice',   'alice@example.com',   'password123',  'USER'),
  (2, 'bob',     'bob@example.com',     'hunter2',      'USER'),
  (3, 'charlie', 'charlie@example.com', 'ch@rlie!pass', 'USER'),
  (4, 'admin',   'admin@example.com',   'admin123',     'ADMIN');

-- Seed accounts
INSERT INTO account (id, owner_id, account_number, balance) VALUES
  (1, 1, 'ACC-ALICE-001',  5000.00),
  (2, 2, 'ACC-BOB-001',    3200.00),
  (3, 3, 'ACC-CHARLIE-001',1800.00),
  (4, 4, 'ACC-ADMIN-001',  99999.00);

-- Seed transactions
INSERT INTO transaction (id, account_id, description, amount, created_at) VALUES
  (1,  1, 'Coffee shop',     -4.50,   '2024-01-10 09:00:00'),
  (2,  1, 'Salary deposit',  3000.00, '2024-01-01 08:00:00'),
  (3,  2, 'Groceries',       -120.00, '2024-01-12 14:30:00'),
  (4,  2, 'Freelance pay',   800.00,  '2024-01-15 10:00:00'),
  (5,  3, 'Electric bill',   -90.00,  '2024-01-11 18:00:00'),
  (6,  4, 'Bonus',           5000.00, '2024-01-01 00:00:00');
