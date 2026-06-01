ALTER TABLE categories ADD COLUMN transaction_type VARCHAR(10);

-- Categorias exclusivas de despesa
UPDATE categories SET transaction_type = 'EXPENSE'
WHERE is_default = TRUE
  AND name IN ('Alimentação', 'Transporte', 'Moradia', 'Saúde', 'Educação', 'Lazer', 'Vestuário');

-- Categorias exclusivas de receita
UPDATE categories SET transaction_type = 'INCOME'
WHERE is_default = TRUE
  AND name IN ('Salário', 'Freelance', 'Investimentos');

-- 'Outros' fica NULL: aparece tanto em despesas quanto em receitas
