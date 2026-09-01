-- Insère les quatre patients de référence à chaque démarrage à froid.
-- Téléphones stockés en E.164 (+1... = États-Unis/Canada, PhoneCountry.US) : c'est la forme canonique que le front produit après normalisation. Les stocker au format national « 200-333-4444 » du sujet faisait échouer toute édition du patient, le formulaire ne pouvant pas re-valider un numéro qu'il n'avait pas normalisé lui-même.
-- schema.sql vient de faire un DROP sur la table patient, donc l'AUTO_INCREMENT repart de zéro et ces insertions donnent bien les ids 1..4 dans cet ordre — les tests de l'évaluation du risque comptent dessus (id 1 → TestNone, 2 → TestBorderline, 3 → TestInDanger, 4 → TestEarlyOnset).

INSERT INTO patient (first_name, last_name, date_of_birth, gender, address, phone) VALUES
('Test', 'TestNone',       '1966-12-31', 'F', '1 Brookside St', '+12002223333'),
('Test', 'TestBorderline', '1945-06-24', 'M', '2 High St',      '+12003334444'),
('Test', 'TestInDanger',   '2004-06-18', 'M', '3 Club Road',    '+13004445555'),
('Test', 'TestEarlyOnset', '2002-06-28', 'F', '4 Valley Dr',    '+14005556666');
