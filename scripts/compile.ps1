$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

javac -d out `
    src\graph\*.java `
    src\configs\*.java `
    src\server\*.java `
    src\servlets\*.java `
    src\views\*.java `
    src\Main.java `
    src\tests\Assignment6EdgeCaseTest.java
