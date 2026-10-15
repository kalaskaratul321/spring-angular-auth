# Windows development setup

`scripts/windows/setup-dev.ps1` prepares a complete local environment
(default folder `D:\Legal_KnowlageGraph`). Run it from PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\setup-dev.ps1                 # default D:\Legal_KnowlageGraph
powershell -ExecutionPolicy Bypass -File .\setup-dev.ps1 -Root E:\kg     # another folder
```

It is safe to re-run. It:

1. installs missing tools with `winget`: Git, **Temurin JDK 11** (Gradle 5.4.1 /
   Spring Boot 2.1 do not run on newer JDKs), Node.js LTS, Docker Desktop;
2. clones the repository into `<Root>\spring-angular-auth` (or fast-forwards it);
3. starts **MongoDB 4.4** in Docker (`mahalegal-mongo`, volume `mahalegal-mongo-data`) -
   the version CI tests against;
4. downloads the Gradle dependencies and runs `npm ci` for the frontend;
5. writes helper scripts into `<Root>`: `start-all.ps1`, `start-mongo.ps1`,
   `start-backend.ps1`, `start-frontend.ps1`, `load-sample-data.ps1`, `run-tests.ps1`
   and `README-DEV.md`.

Then:

```powershell
cd D:\Legal_KnowlageGraph
powershell -ExecutionPolicy Bypass -File .\start-all.ps1 -LoadSampleData
```

and sign in at http://localhost:4200 as `admin@sandbox.com` / `password`.

Options: `-NoDocker` (use your own MongoDB 4.4 on localhost:27017), `-SkipInstall`
(check tools only), `-SkipBuild`, `-Branch <name>`.

If Docker Desktop was installed by the script, start it (Windows may ask for a
restart to enable WSL 2) and run the setup again to start MongoDB.
