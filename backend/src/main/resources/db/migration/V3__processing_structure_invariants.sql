-- Corrección aditiva: no reescribe V1/V2 ni transforma datos históricos.
-- Si existen filas incompatibles, la migración falla para exigir revisión explícita.
ALTER TABLE incident_reviews DROP CONSTRAINT incident_reviews_rejection_reason;
ALTER TABLE incident_reviews ADD CONSTRAINT incident_reviews_reason CHECK (
    (decision = 'VALIDATE' OR reason IS NOT NULL)
    AND (reason IS NULL OR (char_length(reason) BETWEEN 3 AND 2000
        AND reason !~ U&'^[\0001-\0020]|[\0001-\0020]$'
        AND reason !~ U&'^[\0009-\000D\001C-\0020\1680\2000-\2006\2008-\200A\2028\2029\205F\3000]+$'))
);
ALTER TABLE priority_decisions DROP CONSTRAINT priority_decisions_justification;
ALTER TABLE priority_decisions ADD CONSTRAINT priority_decisions_justification CHECK (
    char_length(justification) BETWEEN 3 AND 2000
    AND justification !~ U&'^[\0001-\0020]|[\0001-\0020]$'
    AND justification !~ U&'^[\0009-\000D\001C-\0020\1680\2000-\2006\2008-\200A\2028\2029\205F\3000]+$'
);
-- StatusChange conserva null/blancos; varchar(2000/5000) ya limita puntos Unicode.
-- Motivos según transición, autorización y coherencia con Incident son del futuro servicio.
