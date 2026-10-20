# Contributing to TouchDeck

Thanks for your interest in contributing to TouchDeck! This guide will help you get started.

---

## Getting Started

1. **Fork** this repository.
2. **Clone** your fork locally:
   ```bash
   git clone https://github.com/<your-username>/TouchDeck.git
   cd TouchDeck
   ```
3. Create a **feature branch**:
   ```bash
   git checkout -b feature/my-awesome-feature
   ```

---

## Development Setup

### Android Client

- Open `android-app/` in **Android Studio** (or use Gradle CLI).
- Minimum JDK: **17**
- Build: `./gradlew assembleDebug`
- The app requires a device on the **same local network** as the Windows companion.

### Windows Companion

- Open `windows-companion/` in **Visual Studio 2022+** or use the .NET CLI.
- Minimum SDK: **.NET 10**
- Run: `dotnet run` from the `windows-companion/` directory.

### Running Tests

```bash
cd tests
pip install websockets
python test_companion_client.py 127.0.0.1 9876
```

Start the Windows companion first, then run the test script.

---

## Code Style

- **Kotlin (Android)**: Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html). Use `ktlint` if available.
- **C# (Windows)**: Follow [.NET naming guidelines](https://learn.microsoft.com/en-us/dotnet/csharp/fundamentals/coding-style/coding-conventions). Use PascalCase for public members.
- **General**: Keep functions focused and small. Add comments for non-obvious logic.

---

## Submitting Changes

1. **Commit** your changes with clear, descriptive messages:
   ```
   feat: add volume slider to media remote
   fix: prevent reconnect loop when server is unreachable
   ```
2. **Push** to your fork:
   ```bash
   git push origin feature/my-awesome-feature
   ```
3. Open a **Pull Request** against `main` with:
   - A clear title and description of what changed and why.
   - Screenshots or recordings for UI changes.
   - Confirmation that the test suite passes.

---

## Security

If you discover a security vulnerability, **do not open a public issue**. Instead, please report it privately by emailing the maintainers or using GitHub's [private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing/privately-reporting-a-security-vulnerability).

---

## What to Contribute

Here are areas where contributions are especially welcome:

- 🐛 **Bug fixes** — especially around edge cases in reconnection, multi-client handling, or input accuracy.
- 🎨 **UI/UX improvements** — both on the Android and Windows side.
- 📝 **Documentation** — better setup guides, screenshots, video demos.
- 🧪 **Tests** — expanding the automated test suite with more protocol scenarios.
- 🌐 **Localization** — translations for the Android UI.
- 🔒 **Security hardening** — TLS/WSS support, certificate pinning, etc.

---

## Code of Conduct

Be kind, respectful, and constructive. We're all here to build something cool together.

---

Thank you for helping make TouchDeck better! 🎉
