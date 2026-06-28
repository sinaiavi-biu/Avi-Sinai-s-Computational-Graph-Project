$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

& (Join-Path $PSScriptRoot "compile.ps1")
java -cp out tests.Assignment6EdgeCaseTest
