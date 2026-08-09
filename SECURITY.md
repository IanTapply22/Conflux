# Security policy

## Supported versions

Security fixes are provided for the latest released version of Conflux. Upgrade before reporting behavior that may already have been corrected.

## Reporting a vulnerability

Do not open a public issue for a suspected vulnerability. Use GitHub's [private security-advisory reporting flow](https://github.com/IanTapply22/Conflux/security/advisories/new) and include affected Conflux, Relay, Paper, and Redis versions; impact; realistic attack conditions; minimal reproduction steps; and sanitized configuration.

Allow time for investigation and a coordinated fix before public disclosure. Do not access systems, Redis instances, accounts, or credentials that you do not own or have permission to test.

## Operational security

- Follow Relay's security guidance for Redis authentication, TLS, namespaces, payload limits, bounded dispatch, clocks, and credentials.
- Restrict Redis to trusted network participants. Anyone able to publish valid Relay traffic is effectively a trusted ghost-state producer.
- Install Conflux and Relay only on trusted Paper servers, and prevent clients from bypassing the proxy or connecting directly to backends.
- Keep `relay-api` provided at runtime. Duplicate or shaded API classes can break service lookup and dependency isolation.
- Preserve Conflux's maximum remote-frame size, distance radius, per-viewer cap, stale-frame timeout, and equipment decode failure handling.
- Treat signed skin properties and serialized equipment as untrusted display input even when they originate from another server.
- Do not log skin texture values, equipment payloads, Redis credentials, or private player information.
- Monitor Relay rejection/queue metrics and Conflux remote/rendered counts for unexpected load.
- Rotate Redis credentials and restart every Relay node after suspected exposure.

Conflux stores no authoritative player data. Ghost frames and animations are transient and should never be used for permissions, combat, inventory, identity proof, or persistence.
