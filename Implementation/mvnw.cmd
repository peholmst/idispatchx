@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM
@REM Optional ENV vars
@REM   MVNW_REPOURL - repo url base for downloading maven distribution
@REM   MVNW_USERNAME/MVNW_PASSWORD - user and password for downloading maven
@REM   MVNW_VERBOSE - true: enable verbose log; others: silence the output
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET __MVNW_CMD__=
@SET __MVNW_ERROR__=
@SET __MVNW_PSMODULEP_SAVE__=%PSModulePath%
@SET PSModulePath=
@FOR /F "usebackq tokens=1* delims==" %%A IN (`powershell -noprofile "& {$scriptDir='%~dp0teleconfig'; $env:__MVNW_SCRIPT_DIR__=$scriptDir; Get-Content \"%~dp0.mvn\wrapper\maven-wrapper.properties\" | ForEach-Object { if ($_ -match '^([^=]+)=(.*)$') { $key = $matches[1].Trim(); $value = $matches[2].Trim(); Write-Output \"$key=$value\" } }}"`) DO @(
  IF "%%A"=="distributionUrl" SET "MVNW_DISTRIBUTIONURL=%%B"
  IF "%%A"=="distributionSha256Sum" SET "MVNW_SHA256SUM=%%B"
)
@SET PSModulePath=%__MVNW_PSMODULEP_SAVE__%
@IF "%MVNW_DISTRIBUTIONURL%"=="" (
  SET __MVNW_ERROR__=Cannot read distributionUrl property in .mvn\wrapper\maven-wrapper.properties
  GOTO error
)

@SET __MVNW_EXT__=zip
@IF NOT "%MVNW_DISTRIBUTIONURL:~-7%"=="-bin.zip" (
  IF NOT "%MVNW_DISTRIBUTIONURL:mvnd=%"=="%MVNW_DISTRIBUTIONURL%" (
    @REM mvnd distribution URL
  ) ELSE (
    SET __MVNW_ERROR__=distributionUrl is not valid, must match *-bin.zip: %MVNW_DISTRIBUTIONURL%
    GOTO error
  )
)

@REM parse version from distribution URL, e.g., apache-maven-3.9.9-bin.zip -> 3.9.9
@SET "__MVNW_DIST_NAME__=%MVNW_DISTRIBUTIONURL:*/=%"
@FOR /F "tokens=1,2,3,4 delims=-" %%A IN ("%__MVNW_DIST_NAME__%") DO @(
  IF "%%A"=="apache" (
    SET "__MVNW_VERSION__=%%C"
    SET "__MVNW_DIR__=apache-maven-%%C"
  ) ELSE (
    SET "__MVNW_VERSION__=%%B"
    SET "__MVNW_DIR__=%%A-%%B"
  )
)

@SET __MVNW_DIR__=%__MVNW_DIR__:-bin=%

@REM calculate hash
@SET "__MVNW_HASH__="
@FOR /F %%A IN ('powershell -noprofile -command "& { $s='%MVNW_DISTRIBUTIONURL%'; $h=0; foreach($c in $s.ToCharArray()){ $h = ($h * 31 + [int]$c) -band 0xFFFFFFFF }; '{0:x}' -f $h }"') DO @SET __MVNW_HASH__=%%A

@IF "%MAVEN_USER_HOME%"=="" SET "MAVEN_USER_HOME=%USERPROFILE%\.m2"
@SET "MAVEN_HOME=%MAVEN_USER_HOME%\wrapper\dists\%__MVNW_DIR__%\%__MVNW_HASH__%"

@IF EXIST "%MAVEN_HOME%\bin\mvn.cmd" GOTO execute
@IF EXIST "%MAVEN_HOME%\bin\mvnd.cmd" GOTO execute

@REM Download Maven
@SET "MVNW_TMPDIR=%TEMP%\mvnw%RANDOM%"
@MKDIR "%MVNW_TMPDIR%"
@IF NOT EXIST "%MVNW_TMPDIR%" (
  SET __MVNW_ERROR__=Cannot create temp directory %MVNW_TMPDIR%
  GOTO error
)

@SET "__MVNW_DIST_FILE__=%MVNW_TMPDIR%\%__MVNW_DIST_NAME__%"

@REM Download with PowerShell
@powershell -noprofile -command "& { param($url, $output); $ProgressPreference='SilentlyContinue'; if ($env:MVNW_USERNAME -and $env:MVNW_PASSWORD) { $cred = New-Object System.Management.Automation.PSCredential($env:MVNW_USERNAME, (ConvertTo-SecureString $env:MVNW_PASSWORD -AsPlainText -Force)); Invoke-WebRequest -Uri $url -OutFile $output -Credential $cred } else { Invoke-WebRequest -Uri $url -OutFile $output } }" "%MVNW_DISTRIBUTIONURL%" "%__MVNW_DIST_FILE__%"
@IF ERRORLEVEL 1 (
  SET __MVNW_ERROR__=Failed to download %MVNW_DISTRIBUTIONURL%
  GOTO error
)

@REM Validate SHA-256 if specified
@IF NOT "%MVNW_SHA256SUM%"=="" (
  @FOR /F "skip=1 tokens=*" %%A IN ('certutil -hashfile "%__MVNW_DIST_FILE__%" SHA256') DO @(
    @IF "%%A" NEQ "" @IF /I NOT "%%A"=="%MVNW_SHA256SUM%" (
      SET __MVNW_ERROR__=SHA-256 checksum mismatch
      GOTO error
    )
    GOTO sha256ok
  )
  :sha256ok
)

@REM Extract
@MKDIR "%MAVEN_HOME%\.."
@powershell -noprofile -command "& { Expand-Archive -Path '%__MVNW_DIST_FILE__%' -DestinationPath '%MAVEN_HOME%\..' -Force }"
@IF ERRORLEVEL 1 (
  SET __MVNW_ERROR__=Failed to extract %__MVNW_DIST_FILE__%
  GOTO error
)

@REM Cleanup
@RMDIR /S /Q "%MVNW_TMPDIR%"

:execute
@SET "MVNW_MVN_CMD=mvn"
@IF EXIST "%MAVEN_HOME%\bin\mvnd.cmd" SET "MVNW_MVN_CMD=mvnd"
@IF "%MVNW_VERBOSE%"=="true" ECHO Using Maven at %MAVEN_HOME%
"%MAVEN_HOME%\bin\%MVNW_MVN_CMD%.cmd" %*
@GOTO :eof

:error
@ECHO %__MVNW_ERROR__%
@RMDIR /S /Q "%MVNW_TMPDIR%" 2>NUL
@EXIT /B 1
