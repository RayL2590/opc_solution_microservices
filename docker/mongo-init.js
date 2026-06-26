// Seed canonique Sprint 2 — 9 notes pour les 4 patients de test (NFR-D3).
// createdAt explicites et distincts pour que findByPatIdOrderByCreatedAtDesc soit prouvable
// sans démarrer l'application. Montage dans le conteneur Mongo : Epic 6.
db = db.getSiblingDB("notesdb");

db.note.insertOne({
    patId: 1,
    patient: "TestNone",
    note: "Le patient déclare qu'il 'se sent très bien' Poids égal ou inférieur au poids recommandé",
    createdAt: ISODate("2024-01-10T08:00:00Z")
});

db.note.insertOne({
    patId: 2,
    patient: "TestBorderline",
    note: "Le patient déclare qu'il ressent beaucoup de stress au travail Il se plaint également que son audition est anormale dernièrement",
    createdAt: ISODate("2024-01-10T09:00:00Z")
});

db.note.insertOne({
    patId: 2,
    patient: "TestBorderline",
    note: "Le patient déclare avoir fait une réaction aux médicaments au cours des 3 derniers mois Il remarque également que son audition continue d'être anormale",
    createdAt: ISODate("2024-01-10T10:00:00Z")
});

db.note.insertOne({
    patId: 3,
    patient: "TestInDanger",
    note: "Le patient déclare qu'il fume depuis peu",
    createdAt: ISODate("2024-01-10T09:00:00Z")
});

db.note.insertOne({
    patId: 3,
    patient: "TestInDanger",
    note: "Le patient déclare qu'il est fumeur et qu'il a cessé de fumer l'année dernière Il se plaint également de crises d'apnée respiratoire anormales Tests de laboratoire indiquant un taux de cholestérol LDL élevé",
    createdAt: ISODate("2024-01-10T10:00:00Z")
});

db.note.insertOne({
    patId: 4,
    patient: "TestEarlyOnset",
    note: "Le patient déclare qu'il lui est devenu difficile de monter les escaliers Il se plaint également d'être essoufflé Tests de laboratoire indiquant que les anticorps sont élevés Réaction aux médicaments",
    createdAt: ISODate("2024-01-10T09:00:00Z")
});

db.note.insertOne({
    patId: 4,
    patient: "TestEarlyOnset",
    note: "Le patient déclare qu'il a mal au dos lorsqu'il reste assis pendant longtemps",
    createdAt: ISODate("2024-01-10T10:00:00Z")
});

db.note.insertOne({
    patId: 4,
    patient: "TestEarlyOnset",
    note: "Le patient déclare avoir commencé à fumer depuis peu Hémoglobine A1C supérieure au niveau recommandé",
    createdAt: ISODate("2024-01-10T11:00:00Z")
});

db.note.insertOne({
    patId: 4,
    patient: "TestEarlyOnset",
    note: "Taille, Poids, Cholestérol, Vertige et Réaction",
    createdAt: ISODate("2024-01-10T12:00:00Z")
});
