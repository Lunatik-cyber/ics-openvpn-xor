# AstroVPN Implementation Documentation

## Overview

This document describes the complete modernization of the OpenVPN Android application to work exclusively with `astrovpn://` profiles, featuring a minimalist Material Design 3 interface and advanced connection optimization.

## Architecture Components

### 1. ProfileParser (`de.blinkt.openvpn.astrovpn.ProfileParser`)

**Purpose**: Handles validation and parsing of `astrovpn://` URLs

**Key Features**:
- Base64 decoding of JSON profile data
- Comprehensive field validation
- Support for multiple URL formats
- Bidirectional URL generation

**Usage Example**:
```java
try {
    ProfileParser.AstroVPNProfile profile = ProfileParser.parseAstroVpnUrl("astrovpn://eyJ...base64...==");
    System.out.println("Profile: " + profile.name);
    System.out.println("Server: " + profile.server);
} catch (ProfileParser.ParseException e) {
    // Handle parsing error
}
```

### 2. AstroVPNProfileManager (`de.blinkt.openvpn.astrovpn.AstroVPNProfileManager`)

**Purpose**: Manages encrypted profile storage and CRUD operations

**Key Features**:
- Encrypted storage using `EncryptedSharedPreferences`
- Profile lifecycle management
- Active profile protection
- Thread-safe operations

**Security Considerations**:
- All profiles stored with AES-256 encryption
- Master key protection using Android Keystore
- Prevention of active profile deletion
- Secure profile metadata handling

### 3. KeyDownloader (`de.blinkt.openvpn.astrovpn.KeyDownloader`)

**Purpose**: Downloads OpenVPN configurations with IP substitution

**Workflow**:
1. Query domain service for IP resolution
2. Substitute domain with resolved IP in key URL
3. Download `.ovpn` configuration via HTTP
4. Validate downloaded content

**Features**:
- Async download operations
- Content size validation (1MB limit)
- Basic OpenVPN config validation
- Timeout handling (15s connect, 30s read)

### 4. RemoteSelector (`de.blinkt.openvpn.astrovpn.RemoteSelector`)

**Purpose**: Optimizes remote server selection via ping testing

**Algorithm**:
1. Parse all `remote` directives from OpenVPN config
2. Ping each server concurrently (max 10 concurrent)
3. Sort servers by response time
4. Rebuild config with optimized server order

**Performance**:
- 5-second ping timeout per server
- Concurrent ping operations
- TCP socket-based connectivity testing
- Intelligent config reconstruction

### 5. SimpleVPNManager (`de.blinkt.openvpn.astrovpn.SimpleVPNManager`)

**Purpose**: Orchestrates the complete AstroVPN connection workflow

**Connection Process**:
1. Profile validation and selection
2. Configuration download with IP substitution
3. Remote server optimization
4. OpenVPN profile creation
5. VPN service startup
6. State monitoring and callbacks

**State Management**:
- `DISCONNECTED` - Initial state
- `CONNECTING` - Connection initiation
- `DOWNLOADING_CONFIG` - Fetching configuration
- `OPTIMIZING_REMOTES` - Server selection
- `STARTING_VPN` - OpenVPN startup
- `CONNECTED` - Active VPN connection
- `DISCONNECTING` - Shutdown process
- `ERROR` - Error state

### 6. AstroVPNMainActivity (`de.blinkt.openvpn.astrovpn.AstroVPNMainActivity`)

**Purpose**: Modern Material Design 3 user interface

**UI Components**:
- Status card with visual connection indicators
- Profile selection dropdown
- Material Design buttons and dialogs
- Theme-aware color system
- Permission handling

**Features**:
- `astrovpn://` URL intent handling
- Clipboard integration for profile URLs
- Profile management dialogs
- Real-time status updates
- Error display and handling

## Integration Notes

### Android Manifest Changes

The manifest has been updated to:
- Set `AstroVPNMainActivity` as the main launcher
- Handle `astrovpn://` URL scheme
- Maintain backward compatibility with existing activities
- Request necessary permissions

### Dependencies Required

The implementation uses:
- `androidx.security:security-crypto` for encrypted storage
- Material Design 3 components
- Existing OpenVPN core libraries
- Standard Android networking APIs

### Security Implementation

**Encryption**:
- AES-256-GCM for profile data
- AES-256-SIV for preference keys
- Android Keystore for master key protection

**Network Security**:
- HTTPS requirement for all URLs
- Content validation for downloads
- Size limits and timeouts
- Input sanitization

**Permission Handling**:
- VPN service permission via `VpnService.prepare()`
- Notification permission for Android 13+
- Network state access for connectivity

## Testing

### Unit Tests

`ProfileParserTest.java` provides comprehensive testing for:
- Valid URL parsing
- Invalid input handling
- Missing field validation
- Bidirectional URL conversion

### Manual Testing Workflow

1. **Profile Addition**:
   - Launch AstroVPN app
   - Tap "Add Profile"
   - Enter valid `astrovpn://` URL
   - Verify profile appears in dropdown

2. **Connection Process**:
   - Select profile from dropdown
   - Tap "Connect"
   - Grant VPN permission if prompted
   - Monitor status progression
   - Verify successful connection

3. **Profile Management**:
   - Tap "Manage" button
   - Test profile deletion
   - Verify active profile protection
   - Test URL copying feature

## AstroVPN URL Format

### Structure
```
astrovpn://base64-encoded-json
```

### JSON Schema
```json
{
  "name": "Server Name",
  "server": "server.example.com",
  "domain_service": "https://api.example.com/domain",
  "key_url": "https://keys.example.com/config.ovpn",
  "description": "Optional description"
}
```

### Example
```
astrovpn://eyJuYW1lIjoiVGVzdCBTZXJ2ZXIiLCJzZXJ2ZXIiOiJ0ZXN0LmV4YW1wbGUuY29tIiwiZG9tYWluX3NlcnZpY2UiOiJodHRwczovL2FwaS5leGFtcGxlLmNvbS9kb21haW4iLCJrZXlfdXJsIjoiaHR0cHM6Ly9rZXlzLmV4YW1wbGUuY29tL2NvbmZpZy5vdnBuIiwiZGVzY3JpcHRpb24iOiJUZXN0IFZQTiBQcm9maWxlIn0=
```

## Error Handling

### Common Error Scenarios

1. **Invalid URL Format**:
   - Non-astrovpn:// scheme
   - Invalid base64 encoding
   - Malformed JSON

2. **Network Errors**:
   - Domain service unreachable
   - Key server timeout
   - Invalid IP resolution

3. **Configuration Issues**:
   - Invalid OpenVPN config
   - Missing remote servers
   - Unsupported directives

4. **Permission Errors**:
   - VPN permission denied
   - Network access restricted

### Error Recovery

The implementation provides automatic recovery for:
- Network timeouts with retry logic
- Server selection fallbacks
- Graceful connection failures
- State restoration on app restart

## Performance Considerations

### Optimization Features

1. **Concurrent Operations**:
   - Parallel server pinging
   - Async configuration download
   - Background processing

2. **Caching Strategy**:
   - Encrypted profile storage
   - Connection state persistence
   - Last connected profile memory

3. **Resource Management**:
   - Automatic executor shutdown
   - Memory-efficient operations
   - Temporary file cleanup

### Scalability

The architecture supports:
- Unlimited profile storage
- Multiple concurrent ping tests
- Large OpenVPN configurations
- Extensive server lists

## Future Enhancements

### Planned Features

1. **Advanced Server Selection**:
   - Bandwidth testing
   - Geographic optimization
   - Load balancing

2. **Enhanced Security**:
   - Certificate pinning
   - Biometric profile protection
   - Audit logging

3. **User Experience**:
   - Widget support
   - Shortcuts integration
   - Advanced statistics

4. **Protocol Support**:
   - WireGuard integration
   - Custom protocols
   - Plugin architecture

## Conclusion

The AstroVPN implementation represents a complete modernization of the OpenVPN Android client, providing:

- **Security**: Enterprise-grade encryption and validation
- **Performance**: Optimized server selection and concurrent operations
- **Usability**: Modern Material Design 3 interface
- **Reliability**: Comprehensive error handling and recovery
- **Maintainability**: Clean architecture with separation of concerns

The modular design ensures easy extensibility while maintaining backward compatibility with the existing OpenVPN infrastructure.