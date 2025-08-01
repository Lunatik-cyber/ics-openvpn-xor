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
}