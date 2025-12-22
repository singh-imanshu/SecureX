# SecureX

A local-first, zero-knowledge password manager built with **Java 25** and **JavaFX**.

I built this because I wanted a lightweight vault that runs locally, handles clipboard security correctly on Windows, and uses modern Java features without the bloat of electron-based alternatives.

### 🔒 The Security Specs
Here is exactly how your data is handled:

* **Encryption:** AES-256 (GCM mode with NoPadding).
* **Key Derivation:** PBKDF2 with HMAC-SHA256 (65,536 iterations) using a unique per-user salt.
* **Memory Hygiene:** Passwords are stored in `char[]` arrays and explicitly zeroed out (`\0`) after use. We avoid `String` for sensitive data to bypass Java's string pool retention.
* **Clipboard Protection:** On Windows, it uses JNA (Native Access) to flag copied passwords with `ExcludeClipboardContentFromMonitorProcessing`, preventing them from sticking in your Windows Clipboard History.
* **Zero Knowledge:** The master password is never stored. We only store a salted hash to verify login.

### 🛠 Tech Stack
* **Language:** Java 25 (OpenJDK)
* **UI:** JavaFX 25
* **Build:** Maven
* **Native Access:** JNA 5.13 (for Windows kernel32/user32 calls)
* **Storage:** Gson (Encrypted JSON blobs)

### 🚀 How to Run

**Prerequisites:**
* JDK 25 installed and set as `JAVA_HOME`.

**Build & Run:**
The project includes a Maven Wrapper, so you don't need Maven installed globally.

```bash
# Clone the repo
git clone [https://github.com/singh-imanshu/SecureX.git](https://github.com/singh-imanshu/SecureX.git)
cd SecureX

# Linux/Mac
./mvnw javafx:run

# Windows (PowerShell)
.\mvnw.cmd javafx:run