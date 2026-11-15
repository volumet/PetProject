IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = 'school')
BEGIN
    EXEC('CREATE SCHEMA school AUTHORIZATION dbo;');
END;

IF NOT EXISTS (SELECT * FROM sys.database_principals WHERE name = 'school_app_role')
BEGIN
    CREATE ROLE school_app_role;
END;

GRANT SELECT, INSERT, UPDATE, DELETE ON SCHEMA::school TO school_app_role;