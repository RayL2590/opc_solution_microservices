-- Seed the four canonical patients on every fresh boot (NFR-D2).
-- schema.sql DROPs the patient table just before this runs, so the inserts land on a fresh
-- AUTO_INCREMENT sequence and yield ids 1..4 in this exact order — Epic 4 Risk Assessment
-- fixtures depend on this mapping (id 1 → TestNone, 2 → TestBorderline, 3 → TestInDanger,
-- 4 → TestEarlyOnset).

INSERT INTO patient (first_name, last_name, date_of_birth, gender, address, phone) VALUES
('Test', 'TestNone',       '1966-12-31', 'F', '1 Brookside St', '100-222-3333'),
('Test', 'TestBorderline', '1945-06-24', 'M', '2 High St',      '200-333-4444'),
('Test', 'TestInDanger',   '2004-06-18', 'M', '3 Club Road',    '300-444-5555'),
('Test', 'TestEarlyOnset', '2002-06-28', 'F', '4 Valley Dr',    '400-555-6666');
