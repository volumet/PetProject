IF NOT EXISTS (SELECT * FROM sys.database_principals WHERE name = 'school_app_role')
BEGIN
    CREATE ROLE school_app_role;
END