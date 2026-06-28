$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

javadoc -notimestamp -d docs\api `
    src\server\HTTPServer.java `
    src\server\MyHTTPServer.java `
    src\server\RequestParser.java `
    src\servlets\Servlet.java
