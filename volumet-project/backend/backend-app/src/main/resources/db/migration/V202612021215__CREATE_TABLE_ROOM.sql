IF NOT EXISTS (
    SELECT 1
    FROM sys.objects
    WHERE object_id = OBJECT_ID(N'school.ROOM')
)
BEGIN
    CREATE TABLE school.ROOM (
        ID BIGINT PRIMARY KEY,
        ROOM_NAME NVARCHAR(20) NOT NULL,
        STATUS NVARCHAR(6) NOT NULL,
        SIZE INT NOT NULL,

        -- Auditable
        USR_LOG_I NVARCHAR(100) NOT NULL,
        DTE_LOG_I DATETIME2 NOT NULL,
        USR_LOG_U NVARCHAR(100) NULL,
        DTE_LOG_U DATETIME2 NULL,
        VERSION BIGINT NOT NULL
    );
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.sequences
    WHERE name = 'ROOM_SEQUENCE'
      AND SCHEMA_NAME(schema_id) = 'school'
)
BEGIN
    CREATE SEQUENCE school.ROOM_SEQUENCE
        AS INT
        START WITH 1
        INCREMENT BY 50
        NO CYCLE;
END;