# Security Policy

## Supported versions

PadX is a personal project maintained on a best-effort basis. Only the latest
commit on the default branch receives security fixes.

| Version | Supported |
|---------|-----------|
| latest  | Yes       |
| older   | No        |

## Reporting a vulnerability

**Please do not open a public issue for security problems.**

Report privately through one of these channels:

1. **GitHub private vulnerability reporting**: on the repository, open the
   *Security* tab and choose *Report a vulnerability*.
2. **Direct message** to [@Tech4Aditya](https://github.com/Tech4Aditya) on
   GitHub if the option above is unavailable.

Include:
- a clear description of the issue and its impact
- steps to reproduce, or a proof of concept
- affected component (Android app, Windows server, or protocol)
- your environment (Windows version and architecture, Android version)

You can expect an acknowledgement within 7 days and a status update within 30
days. Fixes are shipped on a best-effort basis. Please give me reasonable time
to fix the issue before disclosing it publicly, and I will credit you in the
fix unless you prefer to stay anonymous.

## Scope

In scope:
- the PC server (`server/`): packet parsing, memory safety, denial of service
- the Android app (`android/`): data handling, permissions, stored settings
- the network protocol: spoofing, injection, discovery abuse

Out of scope:
- vulnerabilities in third-party components (ViGEmBus, ViGEmClient, Android,
  Windows); please report those to their maintainers
- attacks that require prior full control of the PC or phone
- issues caused by running the software on an untrusted network, which are
  documented below

## Known security limitations

These are design limitations of the current version, not bugs:

- **No authentication.** The server accepts controller packets from any device
  that can reach UDP port 26760. Anyone on the same network can send input to
  your PC while the server runs.
- **No encryption.** Traffic is plaintext UDP. Input data can be observed or
  spoofed on the local network.
- **Open discovery.** The server answers every discovery broadcast, which
  reveals that a PadX server is running at that IP.

## Safe usage recommendations

- Use PadX only on networks you trust (home Wi-Fi). Avoid public or shared
  networks such as campus, hostel, cafe, or hotel Wi-Fi.
- Allow the server through Windows Firewall on **Private** networks only.
- Close `padx_server.exe` when you are not playing.
- Keep your router's client isolation setting in mind: it blocks PadX on
  purpose, and that is a sign it is protecting you.
- Keep ViGEmBus installed from its official releases only.

## Planned hardening

- pairing code or QR-based pairing
- per-session authentication key on every packet
- optional allow-list of device addresses