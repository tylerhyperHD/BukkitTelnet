# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BukkitTelnet is a Telnet server plugin for Minecraft (Spigot/Bukkit) that allows remote administration via any Telnet client. It's designed for servers with multiple administrators who share full permissions but authenticate with a common password or IP-based authentication.

## Build and Development Commands

**Building the project:**
```bash
mvn clean package
```

**Run checkstyle (enforced during build):**
```bash
mvn checkstyle:check
```

**Generate checkstyle report:**
```bash
mvn checkstyle:checkstyle
```

**Compile only:**
```bash
mvn compile
```

The built JAR will be in `target/` directory.

## Code Style Requirements

This project uses strict checkstyle enforcement (failures will break the build):

- **Braces**: Opening curly brace on new line (`nl` style)
- **Line length**: Maximum 200 characters
- **Indentation**: Tabs (FileTabCharacter enforced)
- **Naming conventions**:
  - Package names: lowercase
  - Class names: PascalCase, pattern `(^[A-Z][0-9]?)$|(^[A-Z][_a-zA-Z0-9]*$)`
  - Method names: camelCase, pattern `^[a-z][a-z0-9][a-zA-Z0-9_]*$`
  - Variables/parameters: camelCase with underscores allowed, pattern `^[a-z][_a-zA-Z0-9]*$`
- **Imports**: No star imports
- **Other**: One statement per line, proper whitespace around operators

Checkstyle config is in `checkstyle.xml` with suppressions in `supressions.xml`.

## Architecture

### Core Components

The plugin follows a multi-threaded architecture with these main components:

1. **BukkitTelnet** (main plugin class)
   - Entry point that initializes all components
   - Lifecycle: onLoad() → onEnable() → onDisable()
   - Manages TelnetServer, TelnetLogAppender, TelnetConfigLoader, and PlayerEventListener

2. **TelnetServer**
   - Manages the server socket lifecycle
   - Creates and starts SocketListener thread
   - Implements the public API (`me.totalfreedom.bukkittelnet.api.Server`)
   - Registered as a Bukkit service for external plugin access

3. **SocketListener** (extends Thread)
   - Accepts incoming TCP connections on a background thread
   - Implements connection throttling (10 second threshold per IP)
   - Creates and manages ClientSession threads for each connection
   - Cleans up disconnected sessions

4. **ClientSession** (extends Thread)
   - One thread per connected client
   - Handles authentication (username/password or IP-based bypass)
   - Executes commands via Bukkit's main thread (using BukkitRunnable)
   - Manages client-specific log filtering (chat-only, non-chat, all)
   - Supports "enhanced mode" for player list updates

5. **TelnetLogAppender** (extends Log4j AbstractAppender)
   - Attached to Log4j root logger during onEnable()
   - Broadcasts all server logs to connected, authenticated sessions
   - Respects per-session FilterMode settings
   - Formats logs with timestamp and level

### Threading Model

**Critical**: The plugin uses multiple threading contexts:

- **SocketListener thread**: Accepts connections, manages session list
- **ClientSession threads**: One per client, reads input, sends output
- **Bukkit main thread**: All command execution happens here via `BukkitRunnable.runTask()`

Commands typed in telnet are read on the ClientSession thread but executed on the main thread via `syncExecuteCommand()`. This ensures thread safety with Bukkit's single-threaded API.

Methods prefixed with `sync` indicate they may be called from multiple threads and use synchronization (typically on the client socket).

### Event API

External plugins can hook into telnet operations via these events:

- **TelnetPreLoginEvent**: Fired before authentication, allows:
  - Cancelling login attempts
  - Bypassing password authentication
  - Pre-setting username
  - Access to client IP address

- **TelnetCommandEvent**: Fired before command execution, allows:
  - Cancelling commands
  - Modifying the command string
  - Changing the CommandSender

- **TelnetRequestDataTagsEvent**: For requesting custom data tags

All events extend `TelnetEvent` (which extends Bukkit's Event).

### Authentication Flow

1. Client connects → IP checked against `recentIPs` map (throttling)
2. If IP matches any admin's IP list (fuzzy match with 3 octets), bypass password
3. Fire `TelnetPreLoginEvent` (plugins can bypass or cancel)
4. If not bypassed, prompt for username (3 tries)
5. Prompt for password (3 tries, 2 second delay after each failure)
6. Input filtered via `AUTH_INPUT_FILTER` pattern (only alphanumeric + underscore)
7. On success, add session to TelnetLogAppender and start main loop

### Configuration

`config.yml` structure:
- `address`: Bind address (empty = all interfaces)
- `port`: Port number (default 8765)
- `password`: Shared password for all users
- `admins`: Map of username → list of IP addresses (for password bypass)

Loaded via `TelnetConfigLoader` which uses `YamlConfig` from thirdparty package.

### Special Telnet Commands

Built-in commands (not sent to Bukkit):
- `telnet.help`: Show telnet commands
- `telnet.stop`: Shutdown server (calls `Bukkit.shutdown()` and `System.exit(0)`)
- `telnet.log`: Cycle through log filter modes (NONE → CHAT_ONLY → NONCHAT_ONLY)
- `telnet.exit`: Disconnect session
- `telnet.enhanced`: Toggle enhanced mode (player list updates)

### Input Filtering

- **NONASCII_FILTER**: Removes all non-ASCII printable characters (keeps 0x20-0x7E)
- **AUTH_INPUT_FILTER**: Only allows alphanumeric and underscore
- **COMMAND_INPUT_FILTER**: Allows commands starting with alphanumeric, `/`, `?`, `!`, `.`

## Dependencies

- **Spigot API**: 1.21.10-R0.1-SNAPSHOT (compile scope)
- **Lombok**: 1.16.16 (provided scope)
- **Log4j Core**: 2.25.2 (compile scope)

Java version: 17 (source and target compatibility)

## Important Implementation Notes

- All server log output is stripped of color codes before sending to telnet clients (via `ChatColor.stripColor()`)
- Log appender must be attached AFTER plugin enable and detached BEFORE disable
- SessionCommandSender implements CommandSender to allow commands to be dispatched as if from a console
- Connection throttling prevents rapid reconnection spam (10 second window per IP)
- Fuzzy IP matching compares first 3 octets for admin IP authentication
- Socket operations are synchronized on the clientSocket object to prevent race conditions
