-- Demo catalog, loaded only when the "demo" seed location is enabled (docker profile).
INSERT INTO categories (name) VALUES ('Electronics'), ('Books'), ('Home & Kitchen'), ('Sports');

INSERT INTO products (name, description, price, category_id) VALUES
 ('Noise-Cancelling Headphones', 'Over-ear Bluetooth headphones with 30h battery.', 199.99, (SELECT id FROM categories WHERE name = 'Electronics')),
 ('Mechanical Keyboard', 'Hot-swappable 75% keyboard with tactile switches.', 129.00, (SELECT id FROM categories WHERE name = 'Electronics')),
 ('4K Monitor 27"', 'IPS panel, USB-C 65W power delivery.', 349.50, (SELECT id FROM categories WHERE name = 'Electronics')),
 ('Limited Edition Console', 'Only one unit in stock — great for testing concurrency.', 499.00, (SELECT id FROM categories WHERE name = 'Electronics')),
 ('Designing Data-Intensive Applications', 'Martin Kleppmann. The big ideas behind reliable systems.', 42.00, (SELECT id FROM categories WHERE name = 'Books')),
 ('Effective Java', 'Joshua Bloch, 3rd edition.', 38.75, (SELECT id FROM categories WHERE name = 'Books')),
 ('Pour-Over Coffee Set', 'Glass dripper, kettle and filters.', 59.90, (SELECT id FROM categories WHERE name = 'Home & Kitchen')),
 ('Cast Iron Skillet', 'Pre-seasoned 12-inch skillet.', 34.99, (SELECT id FROM categories WHERE name = 'Home & Kitchen')),
 ('Yoga Mat', 'Non-slip 6mm mat.', 24.50, (SELECT id FROM categories WHERE name = 'Sports')),
 ('Running Shoes', 'Lightweight neutral trainers.', 89.00, (SELECT id FROM categories WHERE name = 'Sports'));

INSERT INTO inventory (product_id, available_quantity)
SELECT id, CASE WHEN name = 'Limited Edition Console' THEN 1 ELSE 25 END FROM products;
