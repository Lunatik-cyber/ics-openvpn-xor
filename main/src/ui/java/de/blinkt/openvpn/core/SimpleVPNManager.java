package de.blinkt.openvpn.core;

import android.content.Context;
import android.content.Intent;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.ProfileManager;

public class SimpleVPNManager {
    private Context context;
    private VpnProfile currentProfile;
    
    public SimpleVPNManager(Context context) {
        this.context = context;
    }
    
    public boolean isConnected() {
        return VpnStatus.isVPNActive();
    }
    
    public void connectWithConfig(String ovpnConfig) {
        try {
            // Парсим конфигурацию
            ConfigParser cp = new ConfigParser();
            cp.parseConfig(new StringReader(ovpnConfig));
            currentProfile = cp.convertProfile();
            currentProfile.mName = "AutoDownloaded";
            
            // Запускаем VPN
            Intent intent = new Intent(context, LaunchVPN.class);
            intent.putExtra(LaunchVPN.EXTRA_KEY, currentProfile.getUUID().toString());
            intent.setAction(Intent.ACTION_MAIN);
            context.startActivity(intent);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void disconnect() {
        if (isConnected()) {
            ProfileManager.setConntectedVpnProfileDisconnected(context);
        }
    }
}