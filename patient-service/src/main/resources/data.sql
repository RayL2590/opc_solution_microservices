-- Insère les quatre patients de référence à chaque démarrage à froid (NFR-D2).
-- schema.sql vient de faire un DROP sur la table patient, donc l'AUTO_INCREMENT repart de
-- zéro et ces insertions donnent bien les ids 1..4 dans cet ordre — les tests de l'évaluation
-- du risque comptent dessus (id 1 → TestNone, 2 → TestBorderline, 3 → TestInDanger,
-- 4 → TestEarlyOnset).

INSERT INTO patient (first_name, last_name, date_of_birth, gender, address, phone) VALUES
('Test', 'TestNone',       '1966-12-31', 'F', '1 Brookside St', '100-222-3333'),
('Test', 'TestBorderline', '1945-06-24', 'M', '2 High St',      '200-333-4444'),
('Test', 'TestInDanger',   '2004-06-18', 'M', '3 Club Road',    '300-444-5555'),
('Test', 'TestEarlyOnset', '2002-06-28', 'F', '4 Valley Dr',    '400-555-6666');
