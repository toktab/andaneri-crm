-- Where a flavor comes from: a bought bottle, fresh produce, or something the bar makes itself.
-- Everything written down until now was a bought product, which is what the column defaults to.
ALTER TABLE product_usages ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'BRAND';
