SELECT
    p.name AS "Printer name",
    p.model AS "Model name",
    p.serialnumber AS "Serial number",
    p.ipaddress AS "IP address",
    p.assetno AS "Asset number",
    SUM(pc.printermono+pc.copiermono+pc.printercolor+pc.copiercolor) AS "Combined total",
    SUM(pc.printermono+pc.copiermono) AS "Black & white total",
    SUM(pc.printercolor+pc.copiercolor) AS "Color total",
    SUM(pc.copiermono) AS "Copier black & white",
    SUM(pc.copiercolor) AS "Copier color total",
    SUM(pc.printermono) AS "Printer black & white",
    SUM(pc.printercolor) AS "Printer color total"
        

FROM
    usersession pc
    JOIN printer p ON pc.printer_id = p.id

WHERE
    pc.id is not null
    AND (pc.finishdate BETWEEN @StartDate AND @EndDate)
    AND (
    pc.printermono > 0
        OR pc.printercolor > 0
        OR pc.copiermono > 0
        OR pc.copiercolor > 0
    ) AND p.serialnumber LIKE @SerialNumber

GROUP BY 1, 2, 3, 4, 5
ORDER BY p.ipaddress ASC