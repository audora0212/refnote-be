ALTER TABLE explanations ADD COLUMN regenerated_at DATETIME NULL;
CREATE INDEX idx_explanations_regenerated_at ON explanations(regenerated_at);
