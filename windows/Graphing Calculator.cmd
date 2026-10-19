@echo off
rem The graphing calculator, double-clicked. Needs clotran installed
rem (install.ps1) and this folder as it comes in graphing-calculator.zip:
rem clotran-plot\ and clotran-ui\ side by side. No git, no compiler: the
rem windows' native library is fetched prebuilt the first time.
rem With arguments, runs clotran on them with the same libraries instead.
set "HERE=%~dp0"
set "CLOTRAN_PATH=%HERE%..\..\clotran-ui\src;%HERE%..\src;%HERE%..\resources"
rem started elsewhere than a project, so no deps.edn is read
cd /d "%USERPROFILE%"
if "%~1"=="" (
  call clotran -m clotran.plot.calc
) else (
  call clotran %*
)
if errorlevel 1 pause
