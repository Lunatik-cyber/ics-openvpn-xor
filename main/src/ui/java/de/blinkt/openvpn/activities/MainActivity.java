/*
 * Упрощенная MainActivity для VPN приложения
 */
package de.blinkt.openvpn.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.widget.EditText;

import de.blinkt.openvpn.R;
import de.blinkt.openvpn.core.KeyDownloader;
import de.blinkt.openvpn.core.SimpleVPNManager;

public class MainActivity extends BaseActivity {
    
    private static final String PREFS_NAME = "VpnSettings";
    private static final String KEY_URL = "key_download_url";
    private static final String DEFAULT_URL = "https://panel.astral-step.space/api/keys/download/test";
    
    private Button connectButton;
    private Button changeUrlButton;
    private TextView statusText;
    private SimpleVPNManager vpnManager;
    private KeyDownloader keyDownloader;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_main);
        
        initViews();
        initManagers();
        checkFirstRun();
        updateUI();
    }
    
    private void initViews() {
        connectButton = findViewById(R.id.btn_connect);
        changeUrlButton = findViewById(R.id.btn_change_url);
        statusText = findViewById(R.id.text_status);
        
        connectButton.setOnClickListener(v -> toggleConnection());
        changeUrlButton.setOnClickListener(v -> showChangeUrlDialog());
    }
    
    private void initManagers() {
        vpnManager = new SimpleVPNManager(this);
        keyDownloader = new KeyDownloader();
    }
    
    private void checkFirstRun() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedUrl = prefs.getString(KEY_URL, null);
        
        if (savedUrl == null) {
            showFirstTimeSetupDialog();
        }
    }
    
    private void showFirstTimeSetupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Настройка VPN");
        builder.setMessage("Введите ссылку для загрузки ключа:");
        
        final EditText input = new EditText(this);
        input.setText(DEFAULT_URL);
        builder.setView(input);
        
        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            String url = input.getText().toString().trim();
            if (!url.isEmpty()) {
                saveKeyUrl(url);
            }
        });
        
        builder.setCancelable(false);
        builder.show();
    }
    
    private void showChangeUrlDialog() {
        // Аналогично showFirstTimeSetupDialog, но с возможностью отмены
    }
    
    private void saveKeyUrl(String url) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_URL, url).apply();
    }
    
    private void toggleConnection() {
        if (vpnManager.isConnected()) {
            vpnManager.disconnect();
        } else {
            connectToVPN();
        }
        updateUI();
    }
    
    private void connectToVPN() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String keyUrl = prefs.getString(KEY_URL, DEFAULT_URL);
        
        // Показать прогресс
        statusText.setText("Загрузка ключа...");
        connectButton.setEnabled(false);
        
        keyDownloader.downloadKey(keyUrl, new KeyDownloader.DownloadCallback() {
            @Override
            public void onSuccess(String ovpnConfig) {
                runOnUiThread(() -> {
                    vpnManager.connectWithConfig(ovpnConfig);
                    updateUI();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Ошибка: " + error, Toast.LENGTH_LONG).show();
                    updateUI();
                });
            }
        });
    }
    
    private void updateUI() {
        boolean isConnected = vpnManager.isConnected();
        
        connectButton.setText(isConnected ? "Отключиться" : "Подключиться");
        connectButton.setEnabled(true);
        
        statusText.setText(isConnected ? "Подключено" : "Отключено");
        
        // Обновить цвет статуса
        int color = isConnected ? getColor(R.color.connected) : getColor(R.color.disconnected);
        statusText.setTextColor(color);
    }
}