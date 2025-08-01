#!/usr/bin/env python3
"""
AstroVPN Profile Generator
Generates test astrovpn:// URLs for development and testing
"""

import json
import base64
import sys

def generate_astrovpn_url(name, server, domain_service, key_url, description=""):
    """Generate an astrovpn:// URL from profile parameters"""
    
    profile = {
        "name": name,
        "server": server,
        "domain_service": domain_service,
        "key_url": key_url,
        "description": description
    }
    
    # Convert to JSON and encode
    json_str = json.dumps(profile, separators=(',', ':'))
    encoded = base64.b64encode(json_str.encode('utf-8')).decode('ascii')
    
    return f"astrovpn://{encoded}"

def decode_astrovpn_url(url):
    """Decode an astrovpn:// URL to show profile data"""
    
    if not url.startswith('astrovpn://'):
        raise ValueError("URL must start with astrovpn://")
    
    encoded = url[len('astrovpn://'):]
    decoded = base64.b64decode(encoded).decode('utf-8')
    profile = json.loads(decoded)
    
    return profile

def main():
    if len(sys.argv) < 2:
        print("AstroVPN Profile Generator")
        print("\nUsage:")
        print("  python3 generate_profile.py generate <name> <server> <domain_service> <key_url> [description]")
        print("  python3 generate_profile.py decode <astrovpn_url>")
        print("\nExamples:")
        print("  python3 generate_profile.py generate \"Test Server\" \"vpn.example.com\" \"https://api.example.com/domain\" \"https://keys.example.com/test.ovpn\" \"Test profile\"")
        print("  python3 generate_profile.py decode \"astrovpn://eyJ...\"")
        sys.exit(1)
    
    command = sys.argv[1]
    
    if command == "generate":
        if len(sys.argv) < 6:
            print("Error: generate requires at least 4 parameters")
            sys.exit(1)
        
        name = sys.argv[2]
        server = sys.argv[3]
        domain_service = sys.argv[4]
        key_url = sys.argv[5]
        description = sys.argv[6] if len(sys.argv) > 6 else ""
        
        try:
            url = generate_astrovpn_url(name, server, domain_service, key_url, description)
            print(f"Generated AstroVPN URL:")
            print(url)
            print(f"\nLength: {len(url)} characters")
        except Exception as e:
            print(f"Error generating URL: {e}")
            sys.exit(1)
    
    elif command == "decode":
        if len(sys.argv) < 3:
            print("Error: decode requires an astrovpn:// URL")
            sys.exit(1)
        
        url = sys.argv[2]
        
        try:
            profile = decode_astrovpn_url(url)
            print("Decoded AstroVPN Profile:")
            print(json.dumps(profile, indent=2))
        except Exception as e:
            print(f"Error decoding URL: {e}")
            sys.exit(1)
    
    else:
        print(f"Unknown command: {command}")
        sys.exit(1)

if __name__ == "__main__":
    main()