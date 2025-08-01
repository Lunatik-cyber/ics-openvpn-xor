/*
 * Copyright (c) 2024 AstroVPN Development Team
 * Distributed under the GNU GPL v2 with additional terms.
 */

package de.blinkt.openvpn.astrovpn;

import android.util.Base64;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

/**
 * Test class for AstroVPN ProfileParser
 */
@RunWith(RobolectricTestRunner.class)
public class ProfileParserTest {

    @Test
    public void testParseValidAstroVpnUrl() throws Exception {
        // Create test profile JSON
        JSONObject profileJson = new JSONObject();
        profileJson.put("name", "Test Server");
        profileJson.put("server", "test.example.com");
        profileJson.put("domain_service", "https://api.example.com/domain");
        profileJson.put("key_url", "https://keys.example.com/config.ovpn");
        profileJson.put("description", "Test VPN Profile");

        // Encode to base64
        String jsonString = profileJson.toString();
        String base64 = Base64.encodeToString(jsonString.getBytes("UTF-8"), Base64.NO_WRAP);
        String astrovpnUrl = "astrovpn://" + base64;

        // Parse the URL
        ProfileParser.AstroVPNProfile profile = ProfileParser.parseAstroVpnUrl(astrovpnUrl);

        // Verify results
        assertNotNull(profile);
        assertEquals("Test Server", profile.name);
        assertEquals("test.example.com", profile.server);
        assertEquals("https://api.example.com/domain", profile.domainService);
        assertEquals("https://keys.example.com/config.ovpn", profile.keyUrl);
        assertEquals("Test VPN Profile", profile.description);
    }

    @Test
    public void testParseInvalidScheme() {
        try {
            ProfileParser.parseAstroVpnUrl("http://example.com");
            fail("Should have thrown ParseException");
        } catch (ProfileParser.ParseException e) {
            assertTrue(e.getMessage().contains("astrovpn://"));
        }
    }

    @Test
    public void testParseEmptyUrl() {
        try {
            ProfileParser.parseAstroVpnUrl("");
            fail("Should have thrown ParseException");
        } catch (ProfileParser.ParseException e) {
            assertTrue(e.getMessage().contains("empty"));
        }
    }

    @Test
    public void testParseMissingRequiredField() throws Exception {
        // Create JSON missing required field
        JSONObject profileJson = new JSONObject();
        profileJson.put("name", "Test Server");
        // Missing server field

        String jsonString = profileJson.toString();
        String base64 = Base64.encodeToString(jsonString.getBytes("UTF-8"), Base64.NO_WRAP);
        String astrovpnUrl = "astrovpn://" + base64;

        try {
            ProfileParser.parseAstroVpnUrl(astrovpnUrl);
            fail("Should have thrown ParseException for missing field");
        } catch (ProfileParser.ParseException e) {
            assertTrue(e.getMessage().contains("server"));
        }
    }

    @Test
    public void testGenerateAstroVpnUrl() throws Exception {
        // Create test profile JSON
        JSONObject profileJson = new JSONObject();
        profileJson.put("name", "Test Server");
        profileJson.put("server", "test.example.com");
        profileJson.put("domain_service", "https://api.example.com/domain");
        profileJson.put("key_url", "https://keys.example.com/config.ovpn");
        profileJson.put("description", "Test VPN Profile");

        ProfileParser.AstroVPNProfile profile = new ProfileParser.AstroVPNProfile(
                "Test Server",
                "test.example.com",
                "https://api.example.com/domain",
                "https://keys.example.com/config.ovpn",
                "Test VPN Profile",
                profileJson
        );

        // Generate URL
        String generatedUrl = ProfileParser.generateAstroVpnUrl(profile);

        // Verify URL can be parsed back
        ProfileParser.AstroVPNProfile parsedProfile = ProfileParser.parseAstroVpnUrl(generatedUrl);
        assertEquals(profile.name, parsedProfile.name);
        assertEquals(profile.server, parsedProfile.server);
        assertEquals(profile.domainService, parsedProfile.domainService);
        assertEquals(profile.keyUrl, parsedProfile.keyUrl);
    }

    @Test
    public void testParseRequirementsTestProfile() throws Exception {
        // Test the exact profile from requirements
        String testProfileUrl = "astrovpn://eyJrZXlfdXJsIjoiaHR0cHM6Ly9wYW5lbC5hc3RyYWwtc3RlcC5zcGFjZS9hcGkva2V5cy9kb3dubG9hZC9hbWFscGMxIiwiZG9tYWluX3NlcnZpY2UiOiJjaGFpbi5hbG9wbXgub25saW5lOjgwMDAiLCJkb21haW5faXAiOiJ0ZXN0LmFzZXZjLm9ubGluZSJ9";
        
        // Parse the URL
        ProfileParser.AstroVPNProfile profile = ProfileParser.parseAstroVpnUrl(testProfileUrl);
        
        // Verify results match the expected structure
        assertNotNull(profile);
        assertEquals("AstroVPN (test.asevc.online)", profile.name); // Generated name
        assertEquals("test.asevc.online", profile.server); // From domain_ip field
        assertEquals("chain.alopmx.online:8000", profile.domainService); // hostname:port format
        assertEquals("https://panel.astral-step.space/api/keys/download/amalpc1", profile.keyUrl);
        assertEquals("", profile.description); // No description provided
    }

    @Test
    public void testParseHostnamePortDomainService() throws Exception {
        // Test hostname:port format for domain_service
        JSONObject profileJson = new JSONObject();
        profileJson.put("name", "Test Server");
        profileJson.put("domain_ip", "test.example.com");
        profileJson.put("domain_service", "api.example.com:8080");
        profileJson.put("key_url", "https://keys.example.com/config.ovpn");

        String jsonString = profileJson.toString();
        String base64 = Base64.encodeToString(jsonString.getBytes("UTF-8"), Base64.NO_WRAP);
        String astrovpnUrl = "astrovpn://" + base64;

        // Parse the URL
        ProfileParser.AstroVPNProfile profile = ProfileParser.parseAstroVpnUrl(astrovpnUrl);

        // Verify results
        assertNotNull(profile);
        assertEquals("Test Server", profile.name);
        assertEquals("test.example.com", profile.server);
        assertEquals("api.example.com:8080", profile.domainService);
        assertEquals("https://keys.example.com/config.ovpn", profile.keyUrl);
    }

    @Test
    public void testParseWithDomainIpField() throws Exception {
        // Test using domain_ip instead of server
        JSONObject profileJson = new JSONObject();
        profileJson.put("domain_ip", "192.168.1.1");
        profileJson.put("domain_service", "https://api.example.com/domain");
        profileJson.put("key_url", "https://keys.example.com/config.ovpn");
        // No name provided - should be generated

        String jsonString = profileJson.toString();
        String base64 = Base64.encodeToString(jsonString.getBytes("UTF-8"), Base64.NO_WRAP);
        String astrovpnUrl = "astrovpn://" + base64;

        // Parse the URL
        ProfileParser.AstroVPNProfile profile = ProfileParser.parseAstroVpnUrl(astrovpnUrl);

        // Verify results
        assertNotNull(profile);
        assertEquals("AstroVPN (192.168.1.1)", profile.name); // Generated name
        assertEquals("192.168.1.1", profile.server);
        assertEquals("https://api.example.com/domain", profile.domainService);
        assertEquals("https://keys.example.com/config.ovpn", profile.keyUrl);
    }
}