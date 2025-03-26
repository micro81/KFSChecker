SET TERM !! ;

EXECUTE BLOCK RETURNS (
    "Printer name" VARCHAR(100),
    "Model name" VARCHAR(100),
    "Serial number" VARCHAR(100),
    "IP address" VARCHAR(50),
    "Asset number" VARCHAR(50),
    "Combined total" INTEGER,
    "Black & white total" INTEGER,
    "Color total" INTEGER,
    "Copier black & white" INTEGER,
    "Copier color total" INTEGER,
    "Printer black & white" INTEGER,
    "Printer color total" INTEGER
) AS
DECLARE StartDate TIMESTAMP;
DECLARE EndDate TIMESTAMP;
DECLARE SerialNumber VARCHAR(100);
BEGIN
    StartDate = CAST('2024-11-30 15:48:00' AS TIMESTAMP);
    EndDate = CAST('2025-01-13 17:24:21' AS TIMESTAMP);
	SerialNumber = CAST('RVF2931578' AS VARCHAR(100));
    
    FOR SELECT
        p.name,
        p.model,
        p.serialnumber,
        p.ipaddress,
        p.assetno,
        SUM(pc.printermono + pc.copiermono + pc.printercolor + pc.copiercolor),
        SUM(pc.printermono + pc.copiermono),
        SUM(pc.printercolor + pc.copiercolor),
        SUM(pc.copiermono),
        SUM(pc.copiercolor),
        SUM(pc.printermono),
        SUM(pc.printercolor)
    FROM
        usersession pc
        JOIN printer p ON pc.printer_id = p.id
    WHERE
        pc.id is not null
        AND (pc.finishdate BETWEEN :StartDate AND :EndDate)
        AND (
            pc.printermono > 0
            OR pc.printercolor > 0
            OR pc.copiermono > 0
            OR pc.copiercolor > 0
        ) AND p.serialnumber = :SerialNumber
        
    GROUP BY 1, 2, 3, 4, 5
    ORDER BY p.ipaddress ASC    
    INTO
        "Printer name", "Model name", "Serial number", "IP address", "Asset number",
        "Combined total", "Black & white total", "Color total", "Copier black & white", 
        "Copier color total", "Printer black & white", "Printer color total"
    DO
        SUSPEND;
END!!

SET TERM ; !!
