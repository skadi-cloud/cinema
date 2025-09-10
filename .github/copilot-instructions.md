# Skadi Cloud Cinema

Skadi Cloud Cinema is a cloud-based development environment platform built with Kotlin and Ktor. The platform provides web-based IDEs running in Kubernetes containers, allowing developers to code from anywhere without local setup.

Always reference these instructions first and fallback to search or bash commands only when you encounter unexpected information that does not match the info here.

## Working Effectively

### Critical Prerequisites
- **JAVA VERSION**: Must use Java 11. Build FAILS with Java 17/21. Set up Java 11:
  ```bash
  sudo apt-get update && sudo apt-get install -y openjdk-11-jdk
  sudo update-alternatives --config java  # Select Java 11
  java -version  # Verify shows Java 11
  ```

### Bootstrap and Build Process
- **NEVER CANCEL BUILDS**: Gradle builds may take 10-45 minutes even when working. Set timeout to 60+ minutes.
- **NETWORK LIMITATIONS**: Full builds fail in restricted environments due to dependency access issues:
  - `artifacts.itemis.cloud` and `cache-redirector.jetbrains.com` may be unreachable
  - Use offline builds when possible: `./gradlew build --offline`
  
#### What Works vs What Doesn't
- **WORKS**: Web-js module builds successfully:
  ```bash
  ./gradlew :web-js:build --no-daemon  # Takes ~30 seconds - VALIDATED
  ```
- **FAILS**: Full project build due to network dependencies and Kotlin version conflicts
- **FAILS**: IDE plugin module due to unreachable artifact repositories
- **FAILS**: Shared module due to Kotlin 2.0.0 vs 1.7.20 version conflicts
- **OFFLINE BUILDS**: `./gradlew build --offline` fails with "No cached version" errors for dependencies
  - Use offline only after a successful online dependency resolution

### Database Requirements
- **REQUIRED**: PostgreSQL database with these environment variables:
  ```bash
  export SQL_PASSWORD=mysecretpassword
  export SQL_USER=postgres  
  export SQL_DB=skadi
  export SQL_HOST=localhost:5432
  ```
- Start PostgreSQL for development:
  ```bash
  # For testing/CI (VALIDATED setup from GitHub Actions workflow):
  docker run -d --name postgres -e POSTGRES_PASSWORD=mysecretpassword -e POSTGRES_DB=skadi -p 5432:5432 postgres
  # Takes ~30 seconds to download and start on first run
  ```

### Available Gradle Commands
- **Clean**: `./gradlew clean --no-daemon` (8 seconds - VALIDATED)
- **Build web-js**: `./gradlew :web-js:build --no-daemon` (30 seconds - VALIDATED)
- **List tasks**: `./gradlew tasks --no-daemon`
- **Check dependencies**: `./gradlew :web:dependencies --no-daemon`

### Node.js Frontend Development  
- **Location**: `/web-js` directory
- **Build**: Uses Webpack with Stimulus and Turbo frameworks
- **Dependencies**: 
  ```bash
  cd web-js
  npm install  # Installs ~288 packages, takes 1 second (when cached) - VALIDATED
  npx webpack  # Alternative direct build command - VALIDATED
  ```
- **Build output**: Generates `script.js` (~284 KiB) in build directory
- **Node.js version**: v20.19.5, npm version: 10.8.2

## Repository Structure

### Key Modules
- **`web/`**: Main Ktor-based web application (Kotlin)
- **`web-js/`**: Frontend JavaScript/TypeScript with Webpack
- **`ide-plugin/`**: IntelliJ IDEA plugin (builds require external repositories)  
- **`playground-image/`**: Docker images for IDE containers
- **`shared/`**: Common Kotlin libraries (has version conflicts)
- **`skadi-community-samples/`**: Sample projects for the platform

### Important Files
- **`build.gradle`**: Root build configuration
- **`gradle.properties`**: Kotlin version (1.7.20), target JVM (11)
- **`web/src/cloud/skadi/web/hosting/Application.kt`**: Main application entry point
- **`web/Dockerfile`**: Production container setup
- **`.github/workflows/gradle.yml`**: CI/CD pipeline

## Development Workflows

### Local Development (Limited)
Due to network restrictions, full local development is challenging. Recommended approaches:

1. **Frontend-only development**:
   ```bash
   cd web-js
   npm install
   npx webpack  # Build frontend assets
   ```

2. **Docker-based development**:
   ```bash
   cd playground-image
   ./build-container.sh skadi-local [IDE_URL] [PLUGIN_URL]
   ./run-container.sh skadi-local
   ```

### Testing Strategy
- **Unit tests**: `./gradlew test` (requires database and external dependencies)
- **Frontend**: No npm test scripts configured
- **Integration**: Uses JUnit 5 with Kubernetes server mocking

## Known Issues and Limitations

### Build Issues
- **Java 17 incompatibility**: "Unsupported class file major version 61" - use Java 11
- **Network dependencies**: Many external Maven repositories unreachable in restricted environments
- **Kotlin version conflicts**: Shared module uses stdlib 2.0.0 while project targets 1.7.20
- **IDE plugin dependencies**: Requires access to `artifacts.itemis.cloud` (often unreachable)

### Working Around Issues
- Use `--offline` flag when dependencies are cached
- Focus development on web-js module which builds reliably
- Use Docker for full development environment setup
- Reference GitHub Actions workflow for working CI setup

## Time Expectations
- **Java 11 setup**: 2-5 minutes
- **Dependency resolution**: 3-10 minutes (when network works)  
- **Clean build**: `./gradlew clean` takes 8 seconds - VALIDATED
- **Web-js build**: `./gradlew :web-js:build` takes 30 seconds - VALIDATED
- **NPM install**: 1 second when cached, 20+ seconds fresh - VALIDATED
- **Webpack build**: `npx webpack` takes ~1.2 seconds - VALIDATED
- **PostgreSQL setup**: 30+ seconds for Docker download and start - VALIDATED
- **Full build (when working)**: 10-45 minutes - NEVER CANCEL
- **Docker image build**: 15-60 minutes - NEVER CANCEL

## CI/CD Integration
The project uses GitHub Actions with:
- Java 11 setup
- PostgreSQL service container
- Gradle build with specific environment variables
- Test result archiving

Always check `.github/workflows/gradle.yml` for the most current CI setup.

## Common Debugging Steps
1. **Build fails**: Check Java version first (`java -version`)
2. **Network errors**: Try `--offline` flag or check network connectivity
3. **Database errors**: Verify PostgreSQL is running and environment variables are set
4. **Version conflicts**: Check `gradle.properties` and module-specific build files
5. **Frontend issues**: Verify npm and node versions, try rebuilding web-js module

## Validation Scenarios

When making changes, ALWAYS test these scenarios to ensure your modifications work:

### Basic Validation Steps
1. **Verify Java version**: `java -version` should show Java 11
2. **Clean build test**: `./gradlew clean --no-daemon` (should complete in ~8 seconds)
3. **Frontend build test**: `./gradlew :web-js:build --no-daemon` (should complete in ~30 seconds)
4. **Database connectivity**: If modifying database code, test PostgreSQL connection:
   ```bash
   docker run -d --name postgres-test -e POSTGRES_PASSWORD=mysecretpassword -e POSTGRES_DB=skadi -p 5433:5432 postgres
   # Should start successfully and show "database system is ready to accept connections" in logs
   docker logs postgres-test
   docker stop postgres-test && docker rm postgres-test
   ```

### Code Change Validation
- **Frontend changes**: Always rebuild web-js module and verify webpack output
- **Backend changes**: Test compilation with `./gradlew :web:compileKotlin --no-daemon` 
  - NOTE: Will fail offline with "No cached version" errors for Kotlin dependencies
- **Database schema changes**: Ensure environment variables are documented
- **Docker changes**: Test container builds in restricted environments

### Manual Testing Scenarios
Since full application testing is limited by network restrictions:
- **Frontend**: Inspect generated `script.js` file for expected changes
- **API changes**: Review Kotlin source code for syntax and logic correctness  
- **Configuration**: Verify environment variable requirements are documented

## Emergency Procedures
If builds are completely broken:
- Use the web-js module as the working reference
- Set up PostgreSQL database manually
- Use Docker development environment as fallback
- Reference GitHub Actions setup for known-working configuration
- Document any new network restrictions or dependency issues