-- 30: Recipe conditional unique index for soft-delete compatibility
-- Problem: uk_recipe_code(recipe_code) blocks re-insertion when a soft-deleted record
--          with the same recipe_code exists, because the unique key doesn't consider deleted status.
--
-- Solution: Use a stored generated column that is NULL for soft-deleted records.
-- MySQL unique indexes allow multiple NULL values, so soft-deleted records don't conflict.
-- Active records (deleted=0) get the recipe_code value, guaranteeing uniqueness.
--
-- Note: MySQL 8.0 function index approach (CASE WHEN deleted=0 THEN 0 ELSE id END) fails
-- because functional indexes cannot reference AUTO_INCREMENT columns. The stored generated
-- column approach avoids this limitation.

-- Step 1: Drop old simple unique key
ALTER TABLE recipe DROP INDEX uk_recipe_code;

-- Step 2: Add stored generated column
-- Active records: recipe_code_unique = recipe_code (enforces uniqueness)
-- Soft-deleted records: recipe_code_unique = NULL (multiple NULLs allowed in unique index)
ALTER TABLE recipe ADD COLUMN recipe_code_unique VARCHAR(100) GENERATED ALWAYS AS (IF(deleted=0, recipe_code, NULL)) STORED COMMENT '活跃编码（用于条件唯一索引）';

-- Step 3: Create unique index on the generated column
CREATE UNIQUE INDEX uk_recipe_code ON recipe(recipe_code_unique);

-- Also allow NULL on recipe_ingredient.material_id for import without WMS material IDs
ALTER TABLE recipe_ingredient MODIFY COLUMN material_id bigint NULL COMMENT '物料ID（导入时可为空）';