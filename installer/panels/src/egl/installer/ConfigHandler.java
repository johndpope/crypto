
package egl.installer;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class ConfigHandler {

    private static final String FILE_HEADER =
            "# This file contains customized settings.\n" +
            "# You can modify this file and add more settings.\n" +
            "# See conf/egl-default.properties for a full list\n#\n\n";
    private static final String JAR = "egl.jar";
    private static final String SERVER = "http://localhost";
    private static final int[] PORTS = { 6876, 7876 };   // try to detect both testnet and real servers
    private static final String VAR_PREFIX = "egl.installer.";

    public static final String FILE_PATH = "conf/egl-installer.properties";
    public static final String VAR_CLEAN_INSTALL_DIR = VAR_PREFIX + "cleanInstallDir";
    public static final String VAR_SHUTDOWN_SERVER = VAR_PREFIX + "shutdownServer";
    public static final String VAR_FILE_CONTENTS = "settings";

    public boolean isServerRunning() {
        for (int port: PORTS) {
            try {
                URL url = new URL(SERVER + ':' + port + "/test");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    return true;
                }
            } catch (IOException e) {
                // try next port
            }
        }
        return false;
    }

    public boolean shutdownServer() {
        boolean done = false;
        for (int port: PORTS) {
            try {
                URL url = new URL(SERVER + ':' + port + "/egl?requestType=shutdown");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                done |= (conn.getResponseCode() == HttpURLConnection.HTTP_OK);
            } catch (IOException e) {
                // try next port
            }
        }
        return done;
    }

    public boolean isEagleInstallDir(String path) {
        return path != null && Files.exists(Paths.get(path, JAR));
    }

    public boolean cleanEagleInstallDir(String installPath, boolean retry) {
        if (isEagleInstallDir(installPath)) {
            if (rmdir(installPath)) {
                return true;
            }
            if (retry) {
                for (int tries = 3; tries > 0; tries--) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        break;
                    }
                    if (rmdir(installPath)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean rmdir(String path) {
        try {
            for (Path p: Files.walk(Paths.get(path))
                    .sorted((a, b) -> b.compareTo(a))
                    .toArray(Path[]::new))
            {
                Files.delete(p);
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public static class Setting {
        String description;
        Map<String, String> properties = new HashMap<>();
        List<String> lines = new LinkedList<>();

        Setting(String description) {
            this.description = description;
        }

        String getName() {
            return description.split("\\. ", 2)[0];
        }
    }

    public List<Setting> readSettings() {
        try (InputStream is = getClass().getResourceAsStream("resources/settings.txt");
             BufferedReader in = new BufferedReader(new InputStreamReader(is)))
        {
            return in.lines()
                    .map(this::readSetting)
                    .filter(s -> s != null)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private Setting readSetting(String settingFile) {
        Setting setting = null;
        try (InputStream is = getClass().getResourceAsStream("resources/" + settingFile);
             BufferedReader in = new BufferedReader(new InputStreamReader(is)))
        {
            String line;
            do {
                while ((line = in.readLine()) != null && ! line.startsWith("#")) ;
                List<String> lines = new LinkedList<>();
                StringBuilder description = new StringBuilder();
                while (line != null && line.startsWith("#")) {
                    description.append(line.substring(1).trim()).append('\n');
                    lines.add(line);
                    line = in.readLine();
                }
                if (line != null && line.contains("=")) {
                    lines.add(line);
                    String[] parts = line.split("=");
                    String name = parts[0];
                    StringBuilder value = new StringBuilder();
                    line = parts[1];
                    while (line != null) {
                        line = line.trim();
                        if (line.endsWith("\\")) {
                            value.append(line.substring(0, line.length() - 1));
                            line = in.readLine();
                            lines.add(line);
                        } else {
                            value.append(line);
                            break;
                        }
                    }
                    if (setting == null) {
                        setting = new Setting(description.toString().trim());
                    }
                    setting.properties.put(name, value.toString());
                    setting.lines.addAll(lines);
                    setting.lines.add("");
                }
            } while (line != null);
        } catch (IOException e) {
            // this setting will be skipped
            return null;
        }
        return setting;
    }

    public String writeSettings(List<Setting> settings) {
        return settings.stream().flatMap(s -> s.lines.stream()).collect(Collectors.joining("\n"));
    }

    public boolean writeSettingsFile(String content, String path) {
        String propFile = path + '/' + FILE_PATH;
        try (FileWriter out = new FileWriter(propFile)) {
            out.write(FILE_HEADER);
            out.write(content);
        } catch (IOException e) {
            return false;
        }
        return true;
    }
}
