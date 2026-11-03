CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_categories_name_length CHECK (char_length(name) BETWEEN 2 AND 100),
    -- Java Character.isWhitespace, including Unicode spaces.
    CONSTRAINT chk_categories_name_not_blank CHECK (
        name !~ U&'^[\0009-\000D\001C-\0020\1680\2000-\2006\2008-\200A\2028\2029\205F\3000]+$'
    ),
    -- Java trim() removes U+0000..U+0020; PostgreSQL text cannot contain U+0000.
    CONSTRAINT chk_categories_name_trimmed CHECK (
        name = btrim(name, E'\001\002\003\004\005\006\007\010\011\012\013\014\015\016\017\020\021\022\023\024\025\026\027\030\031\032\033\034\035\036\037\040')
    )
);

-- Inactive rows retain their names and identifiers; no partial index or deletion.
CREATE UNIQUE INDEX uk_categories_name_normalized ON categories (lower(name));
