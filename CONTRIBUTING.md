# Contributing to Yumemi

Thank you for your interest in contributing to Yumemi! This document provides guidelines and instructions for contributing.

## Code of Conduct

Please be respectful and considerate in all interactions. We welcome contributors of all experience levels.

## Getting Started

### Prerequisites

- Android Studio Ladybug or later
- JDK 17 or later
- Android SDK 36
- Git

### Setting Up the Development Environment

1. **Fork the repository**
   ```bash
   # Click "Fork" on GitHub, then clone your fork
   git clone https://github.com/YOUR_USERNAME/Yumemi.git
   cd Yumemi
   ```

2. **Add upstream remote**
   ```bash
   git remote add upstream https://github.com/AgentKush/Yumemi.git
   ```

3. **Create a feature branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

4. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory

5. **Build the project**
   ```bash
   ./gradlew assembleDebug
   ```

## How to Contribute

### Reporting Bugs

Before creating a bug report, please check existing issues to avoid duplicates.

**When reporting a bug, include:**
- Device model and Android version
- App version (found in Settings > About)
- Steps to reproduce the issue
- Expected vs actual behavior
- Screenshots or screen recordings if applicable
- Logcat output for crashes

### Suggesting Features

Feature requests are welcome! Please provide:
- Clear description of the feature
- Use case and benefits
- Possible implementation approach (optional)

### Submitting Code

#### Commit Guidelines

We follow conventional commit messages:

```
type(scope): description

[optional body]

[optional footer]
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `perf`: Performance improvements
- `test`: Adding or updating tests
- `chore`: Maintenance tasks

**Examples:**
```
feat(reader): add double-tap zoom gesture
fix(parser): resolve CloudFlare bypass issue
docs(readme): update build instructions
refactor(database): simplify migration logic
```

#### Code Style

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add KDoc comments for public APIs
- Keep functions focused and concise
- Use `TODO:` comments sparingly and with issue references

#### Pull Request Process

1. **Ensure your code builds**
   ```bash
   ./gradlew assembleDebug
   ```

2. **Run tests**
   ```bash
   ./gradlew test
   ```

3. **Update documentation** if needed

4. **Create a Pull Request**
   - Use a clear, descriptive title
   - Reference any related issues
   - Describe what changes you made and why
   - Include screenshots for UI changes

5. **Respond to feedback** promptly

### Parser Contributions

Manga source parsers are maintained in a separate repository:
**[YakaTeam/kotatsu-parsers](https://github.com/YakaTeam/kotatsu-parsers)**

To fix or add parsers:
1. Fork the kotatsu-parsers repository
2. Make your changes following their contribution guidelines
3. Submit a PR to that repository

## Project Structure

```
app/src/main/kotlin/org/koitharu/kotatsu/
├── bookmarks/       # Bookmark management
├── browser/         # WebView browser
├── core/            # Core utilities, database, network
│   ├── db/          # Room database and DAOs
│   ├── network/     # OkHttp client and interceptors
│   ├── parser/      # Parser integration
│   └── ui/          # Base UI components
├── details/         # Manga details screen
├── download/        # Download management
├── explore/         # Source exploration
├── favourites/      # Favorites management
├── filter/          # Search filters
├── history/         # Reading history
├── local/           # Local manga handling
├── main/            # Main activity and navigation
├── reader/          # Manga reader
├── scrobbling/      # External tracker integration
├── search/          # Search functionality
├── settings/        # App settings
├── sync/            # Cloud sync
├── tracker/         # Update tracker
└── widget/          # Home screen widgets
```

## Building Variants

| Command | Output | Description |
|---------|--------|-------------|
| `./gradlew assembleDebug` | `app-debug.apk` | Development build |
| `./gradlew assembleRelease` | `app-release.apk` | Optimized build |
| `./gradlew assembleNightly` | `app-nightly.apk` | Daily build |
| `./gradlew test` | — | Run unit tests |

## Need Help?

- Check existing [Issues](https://github.com/AgentKush/Yumemi/issues)
- Review the [README](README.md)
- Look at recent commits for examples

## License

By contributing, you agree that your contributions will be licensed under the [GNU General Public License v3.0](LICENSE).

---

Thank you for contributing! 🎉
