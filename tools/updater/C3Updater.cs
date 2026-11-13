// C3 Updater – lädt die neueste C3-APK von GitHub und installiert sie per ADB auf dem TV.
// Bewusst in C# 5 / .NET Framework 4 geschrieben: wird mit dem in Windows enthaltenen
// csc.exe kompiliert, braucht keine Laufzeit-Installation und keine Abhängigkeiten.

using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Web.Script.Serialization;
using System.Threading;
using System.Windows.Forms;

namespace C3
{
    public class UpdaterForm : Form
    {
        const string ApkUrl = "https://github.com/C3yPTaX/C3-TelevisionOS/releases/download/c3-latest/c3-tv.apk";
        const string VersionUrl = "https://github.com/C3yPTaX/C3-TelevisionOS/releases/download/c3-latest/c3-version.txt";
        const string PackageName = "de.c3.tv";
        const string MainActivity = "de.c3.tv/.MainActivity";
        const string RedirectService = "de.c3.tv/de.c3.tv.redirect.HomeRedirectService";

        static readonly Color Bg = Color.FromArgb(18, 20, 26);
        static readonly Color Panel = Color.FromArgb(38, 42, 51);
        static readonly Color Fg = Color.FromArgb(237, 239, 244);
        static readonly Color Muted = Color.FromArgb(140, 146, 160);
        static readonly Color Accent = Color.FromArgb(90, 169, 255);

        readonly ComboBox deviceBox = new ComboBox();
        readonly Button removeButton = new Button();
        readonly TextBox nameBox = new TextBox();
        readonly TextBox ipBox = new TextBox();
        readonly Button saveButton = new Button();
        readonly Button updateButton = new Button();
        readonly Button startButton = new Button();
        readonly Button redirectOnButton = new Button();
        readonly Button redirectOffButton = new Button();
        readonly ComboBox pcBox = new ComboBox();
        readonly Button addPcButton = new Button();
        readonly Button removePcButton = new Button();
        readonly List<Pc> pcs = new List<Pc>();
        Pc pcToRemove;
        readonly Label statusLabel = new Label();
        readonly TextBox log = new TextBox();

        readonly List<Device> devices = new List<Device>();
        readonly string configPath;
        readonly string devicesPath;
        readonly string pcsPath;
        string adbPath;

        class Pc
        {
            public string Name;
            public string Uuid;
            public string AppId;
            public string AppName;
            public override string ToString() { return Name + "  –  " + AppName; }
            public string Serialize() { return Name + "|" + Uuid + "|" + AppId + "|" + AppName; }
        }

        class Device
        {
            public string Name;
            public string Ip;
            public override string ToString() { return Name + "  (" + Ip + ")"; }
        }

        public UpdaterForm()
        {
            string dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "C3Updater");
            Directory.CreateDirectory(dir);
            configPath = Path.Combine(dir, "tv-ip.txt");
            devicesPath = Path.Combine(dir, "devices.txt");
            pcsPath = Path.Combine(dir, "pcs.txt");

            Text = "C3 Updater";
            try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }
            ClientSize = new Size(560, 640);
            FormBorderStyle = FormBorderStyle.FixedSingle;
            MaximizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Bg;
            ForeColor = Fg;
            Font = new Font("Segoe UI", 10f);

            var title = new Label { Text = "C3 TelevisionOS", Font = new Font("Segoe UI Semibold", 16f), ForeColor = Fg, AutoSize = true, Location = new Point(20, 16) };

            // --- saved devices
            var deviceLabel = new Label { Text = "Gespeichertes Gerät", ForeColor = Muted, AutoSize = true, Location = new Point(22, 62) };
            deviceBox.Location = new Point(20, 84);
            deviceBox.Size = new Size(380, 28);
            deviceBox.DropDownStyle = ComboBoxStyle.DropDownList;
            deviceBox.FlatStyle = FlatStyle.Flat;
            deviceBox.BackColor = Panel;
            deviceBox.ForeColor = Fg;
            deviceBox.SelectedIndexChanged += delegate { OnDeviceSelected(); };

            StyleButton(removeButton, "Entfernen", false);
            removeButton.Location = new Point(410, 82);
            removeButton.Size = new Size(130, 32);
            removeButton.Click += delegate { RemoveDevice(); };

            // --- name + ip
            var nameLabel = new Label { Text = "TV-Name", ForeColor = Muted, AutoSize = true, Location = new Point(22, 126) };
            nameBox.Location = new Point(20, 148);
            nameBox.Size = new Size(200, 28);
            StyleTextBox(nameBox);

            var ipLabel = new Label { Text = "IP-Adresse", ForeColor = Muted, AutoSize = true, Location = new Point(232, 126) };
            ipBox.Location = new Point(230, 148);
            ipBox.Size = new Size(170, 28);
            StyleTextBox(ipBox);

            StyleButton(saveButton, "Gerät speichern", false);
            saveButton.Location = new Point(410, 145);
            saveButton.Size = new Size(130, 32);
            saveButton.Click += delegate { SaveDevice(); };

            // --- actions
            StyleButton(updateButton, "TV aktualisieren", true);
            updateButton.Location = new Point(20, 196);
            updateButton.Size = new Size(260, 38);
            updateButton.Click += delegate { RunAsync(Job.Update); };

            StyleButton(startButton, "C3 starten", false);
            startButton.Location = new Point(290, 196);
            startButton.Size = new Size(250, 38);
            startButton.Click += delegate { RunAsync(Job.Start); };

            StyleButton(redirectOnButton, "Home-Taste → C3", false);
            redirectOnButton.Location = new Point(20, 242);
            redirectOnButton.Size = new Size(260, 34);
            redirectOnButton.Click += delegate { RunAsync(Job.RedirectOn); };

            StyleButton(redirectOffButton, "Home-Taste → Amazon", false);
            redirectOffButton.Location = new Point(290, 242);
            redirectOffButton.Size = new Size(250, 34);
            redirectOffButton.Click += delegate { RunAsync(Job.RedirectOff); };

            // --- PCs for C3 Desktop
            var pcLabel = new Label { Text = "PCs für C3 Desktop (Sunshine)", ForeColor = Muted, AutoSize = true, Location = new Point(22, 290) };
            pcBox.Location = new Point(20, 314);
            pcBox.Size = new Size(260, 28);
            pcBox.DropDownStyle = ComboBoxStyle.DropDownList;
            pcBox.FlatStyle = FlatStyle.Flat;
            pcBox.BackColor = Panel;
            pcBox.ForeColor = Fg;

            StyleButton(addPcButton, "Diesen PC hinzufügen", false);
            addPcButton.Location = new Point(290, 312);
            addPcButton.Size = new Size(160, 32);
            addPcButton.Click += delegate { RunAsync(Job.AddPc); };

            StyleButton(removePcButton, "Entfernen", false);
            removePcButton.Location = new Point(458, 312);
            removePcButton.Size = new Size(82, 32);
            removePcButton.Click += delegate { pcToRemove = pcBox.SelectedItem as Pc; RunAsync(Job.RemovePc); };

            statusLabel.Location = new Point(22, 360);
            statusLabel.Size = new Size(520, 22);
            statusLabel.ForeColor = Muted;
            statusLabel.Text = "Bereit.";

            log.Location = new Point(20, 386);
            log.Size = new Size(520, 236);
            log.Multiline = true;
            log.ReadOnly = true;
            log.ScrollBars = ScrollBars.Vertical;
            log.BackColor = Panel;
            log.ForeColor = Fg;
            log.BorderStyle = BorderStyle.None;
            log.Font = new Font("Consolas", 9f);

            Controls.AddRange(new Control[] { title, deviceLabel, deviceBox, removeButton, nameLabel, nameBox, ipLabel, ipBox, saveButton, updateButton, startButton, redirectOnButton, redirectOffButton, pcLabel, pcBox, addPcButton, removePcButton, statusLabel, log });
            AcceptButton = updateButton;
            LoadDevices();
            LoadPcs();
        }

        void StyleButton(Button b, string text, bool primary)
        {
            b.Text = text;
            b.FlatStyle = FlatStyle.Flat;
            b.FlatAppearance.BorderSize = 0;
            b.BackColor = primary ? Accent : Panel;
            b.ForeColor = primary ? Color.Black : Fg;
            b.Cursor = Cursors.Hand;
        }

        static void StyleTextBox(TextBox t)
        {
            t.BackColor = Panel;
            t.ForeColor = Fg;
            t.BorderStyle = BorderStyle.FixedSingle;
        }

        // ------------------------------------------------------------------ devices

        void LoadDevices()
        {
            devices.Clear();
            try
            {
                if (File.Exists(devicesPath))
                {
                    foreach (string line in File.ReadAllLines(devicesPath))
                    {
                        int sep = line.IndexOf('|');
                        if (sep <= 0) continue;
                        devices.Add(new Device { Name = line.Substring(0, sep), Ip = line.Substring(sep + 1).Trim() });
                    }
                }
            }
            catch { }

            if (devices.Count > 0)
            {
                RefreshDeviceBox(devices[0]);
            }
            else
            {
                RefreshDeviceBox(null);
                try { if (File.Exists(configPath)) ipBox.Text = File.ReadAllText(configPath).Trim(); } catch { }
            }
        }

        void WriteDevices()
        {
            var lines = new List<string>();
            foreach (Device d in devices) lines.Add(d.Name.Replace("|", "/") + "|" + d.Ip);
            try { File.WriteAllLines(devicesPath, lines.ToArray()); } catch { }
        }

        void RefreshDeviceBox(Device select)
        {
            deviceBox.Items.Clear();
            foreach (Device d in devices) deviceBox.Items.Add(d);
            if (select != null) deviceBox.SelectedItem = select;
            removeButton.Enabled = devices.Count > 0;
        }

        void OnDeviceSelected()
        {
            var d = deviceBox.SelectedItem as Device;
            if (d == null) return;
            nameBox.Text = d.Name;
            ipBox.Text = d.Ip;
        }

        void SaveDevice()
        {
            string name = nameBox.Text.Trim();
            string ip = ipBox.Text.Trim();
            if (name.Length == 0 || ip.Length == 0)
            {
                MessageBox.Show(this, "Bitte TV-Name und IP-Adresse eintragen.", "C3 Updater");
                return;
            }
            Device target = null;
            foreach (Device d in devices)
                if (string.Equals(d.Name, name, StringComparison.OrdinalIgnoreCase)) target = d;
            if (target == null)
            {
                target = new Device { Name = name, Ip = ip };
                devices.Add(target);
            }
            else
            {
                target.Name = name;
                target.Ip = ip;
            }
            WriteDevices();
            RefreshDeviceBox(target);
            Status("Gerät \"" + name + "\" gespeichert.");
        }

        void RemoveDevice()
        {
            var d = deviceBox.SelectedItem as Device;
            if (d == null) return;
            if (MessageBox.Show(this, "\"" + d.Name + "\" entfernen?", "C3 Updater", MessageBoxButtons.YesNo) != DialogResult.Yes) return;
            devices.Remove(d);
            WriteDevices();
            RefreshDeviceBox(devices.Count > 0 ? devices[0] : null);
            if (devices.Count == 0)
            {
                nameBox.Text = "";
                ipBox.Text = "";
            }
            Status("Gerät entfernt.");
        }

        // ------------------------------------------------------------------ workflow

        enum Job { Update, Start, RedirectOn, RedirectOff, AddPc, RemovePc }

        void RunAsync(Job job)
        {
            string ip = ipBox.Text.Trim();
            if (ip.Length == 0)
            {
                MessageBox.Show(this, "Bitte die IP-Adresse des Fernsehers eintragen.", "C3 Updater");
                return;
            }
            RememberIp(ip);
            SetBusy(true);
            log.Clear();

            var worker = new Thread(delegate ()
            {
                try
                {
                    switch (job)
                    {
                        case Job.Update: DoUpdate(ip); break;
                        case Job.Start: DoStart(ip); break;
                        case Job.RedirectOn: DoRedirect(ip, true); break;
                        case Job.RedirectOff: DoRedirect(ip, false); break;
                        case Job.AddPc: DoAddThisPc(ip); break;
                        case Job.RemovePc: DoRemovePc(ip); break;
                    }
                }
                catch (Exception e)
                {
                    Log("FEHLER: " + e.Message);
                    Status("Fehlgeschlagen – Details im Protokoll.");
                }
                finally
                {
                    UI(delegate { SetBusy(false); });
                }
            });
            worker.IsBackground = true;
            worker.Start();
        }

        void DoUpdate(string ip)
        {
            string serial = Connect(ip);
            if (serial == null) return;

            string installed = InstalledVersion(serial);
            Log("Installiert auf dem TV: " + (installed ?? "–"));

            Status("Lade neueste Version von GitHub …");
            string latest = Download(VersionUrl, null);
            if (latest != null) Log("Neueste Version: " + latest.Trim());

            string apk = Path.Combine(Path.GetTempPath(), "c3-tv.apk");
            Download(ApkUrl, apk);
            Log("APK geladen: " + new FileInfo(apk).Length / 1024 + " KB");

            Status("Installiere auf dem Fernseher …");
            string result = Adb("-s " + serial + " install -r \"" + apk + "\"", 180000);
            Log(result.Trim());
            if (result.IndexOf("Success", StringComparison.OrdinalIgnoreCase) < 0)
            {
                Status("Installation fehlgeschlagen.");
                return;
            }

            Adb("-s " + serial + " shell am start -n " + MainActivity, 20000);
            if (pcs.Count > 0) PushPcs(serial);
            string now = InstalledVersion(serial);
            Status("Fertig – C3 " + (now ?? "") + " ist installiert und gestartet.");
            Log("Fertig.");
        }

        void DoStart(string ip)
        {
            string serial = Connect(ip);
            if (serial == null) return;
            Log(Adb("-s " + serial + " shell am start -n " + MainActivity, 20000).Trim());
            Status("C3 gestartet.");
        }

        /// Adds/removes the C3 HOME redirect in the enabled accessibility services, keeping
        /// any other services that may be enabled. Fully reversible.
        void DoRedirect(string ip, bool enable)
        {
            string serial = Connect(ip);
            if (serial == null) return;

            if (enable && InstalledVersion(serial) == null)
            {
                Log("C3 ist nicht installiert – zuerst \"TV aktualisieren\".");
                Status("C3 fehlt.");
                return;
            }

            string current = Adb("-s " + serial + " shell settings get secure enabled_accessibility_services", 15000).Trim();
            if (current == "null") current = "";
            Log("Vorher: " + (current.Length == 0 ? "(keine)" : current));

            var services = new List<string>();
            foreach (string s in current.Split(':'))
            {
                string t = s.Trim();
                if (t.Length > 0 && !string.Equals(t, RedirectService, StringComparison.OrdinalIgnoreCase)) services.Add(t);
            }
            if (enable) services.Add(RedirectService);

            if (services.Count == 0)
            {
                Adb("-s " + serial + " shell settings delete secure enabled_accessibility_services", 15000);
                Adb("-s " + serial + " shell settings put secure accessibility_enabled 0", 15000);
            }
            else
            {
                string value = string.Join(":", services.ToArray());
                Adb("-s " + serial + " shell settings put secure enabled_accessibility_services " + value, 15000);
                Adb("-s " + serial + " shell settings put secure accessibility_enabled 1", 15000);
            }

            string after = Adb("-s " + serial + " shell settings get secure enabled_accessibility_services", 15000).Trim();
            Log("Nachher: " + after);
            if (enable)
            {
                Adb("-s " + serial + " shell am start -n " + MainActivity, 20000);
                Status("Home-Taste öffnet jetzt C3. (2× Home = Amazon)");
            }
            else
            {
                Status("Home-Taste öffnet wieder Amazon.");
            }
        }

        // ------------------------------------------------------------------ PCs (C3 Desktop)

        /// Reads name + UUID from the local Sunshine (serverinfo) and computes the Sunshine
        /// app id exactly like Sunshine does: abs((int32)crc32(name + sha256hex(image))).
        void DoAddThisPc(string ip)
        {
            Status("Lese Sunshine auf diesem PC …");
            string info = null;
            try
            {
                using (var wc = new WebClient()) info = wc.DownloadString("http://localhost:47989/serverinfo");
            }
            catch (Exception e)
            {
                Log("Sunshine nicht erreichbar (" + e.Message + "). Läuft Sunshine auf diesem PC?");
                Status("Sunshine nicht gefunden.");
                return;
            }
            string uuid = XmlValue(info, "uniqueid");
            string host = XmlValue(info, "hostname");
            if (string.IsNullOrEmpty(host)) host = Environment.MachineName;
            if (uuid == null || !Regex.IsMatch(uuid, "^[0-9A-Fa-f-]{36}$"))
            {
                Log("Keine gültige UUID in serverinfo gefunden.");
                Status("Fehlgeschlagen.");
                return;
            }
            Log("PC: " + host + "  UUID: " + uuid);

            string sunshineDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Sunshine");
            string appsJson = FindAppsJson(sunshineDir);
            if (appsJson == null)
            {
                Log("apps.json von Sunshine nicht gefunden (erwartet unter " + sunshineDir + "\\config).");
                Status("Fehlgeschlagen.");
                return;
            }
            Log("apps.json: " + appsJson);

            var apps = ComputeAppIds(appsJson, Path.Combine(sunshineDir, "assets"));
            if (apps.Count == 0)
            {
                Log("Keine Apps in apps.json gefunden.");
                Status("Fehlgeschlagen.");
                return;
            }
            KeyValuePair<string, string> chosen = apps[0];
            foreach (var a in apps)
            {
                Log("  App \"" + a.Key + "\" → ID " + a.Value);
                if (string.Equals(a.Key, "Desktop", StringComparison.OrdinalIgnoreCase)) chosen = a;
            }

            var pc = new Pc { Name = Clean(host), Uuid = uuid, AppId = chosen.Value, AppName = Clean(chosen.Key) };
            pcs.RemoveAll(delegate (Pc x) { return string.Equals(x.Uuid, uuid, StringComparison.OrdinalIgnoreCase); });
            pcs.Add(pc);
            WritePcs();
            UI(delegate { RefreshPcBox(pc); });
            Log("Gespeichert: " + pc.Name + " → " + pc.AppName + " (" + pc.AppId + ")");

            string serial = Connect(ip);
            if (serial == null) return;
            PushPcs(serial);
            Status("\"" + pc.Name + "\" ist jetzt in C3 – Kachel startet direkt den Stream.");
        }

        void DoRemovePc(string ip)
        {
            Pc selected = pcToRemove;
            if (selected == null)
            {
                Status("Kein PC ausgewählt.");
                return;
            }
            pcs.Remove(selected);
            WritePcs();
            UI(delegate { RefreshPcBox(pcs.Count > 0 ? pcs[0] : null); });
            Log("Entfernt: " + selected.Name);
            string serial = Connect(ip);
            if (serial == null) return;
            PushPcs(serial);
            Status("PC entfernt.");
        }

        void PushPcs(string serial)
        {
            var parts = new List<string>();
            foreach (Pc pc in pcs) parts.Add(pc.Serialize());
            string value = string.Join(";", parts.ToArray());
            string result = Adb("-s " + serial + " shell am start -n " + MainActivity +
                " -a de.c3.tv.action.SET_PCS --es de.c3.tv.extra.PCS '" + value + "'", 20000);
            Log("PCs an den TV gesendet (" + pcs.Count + ").");
            if (result.IndexOf("Error", StringComparison.OrdinalIgnoreCase) >= 0) Log(result.Trim());
        }

        static string Clean(string s)
        {
            return (s ?? "").Replace("|", " ").Replace(";", " ").Replace("'", " ").Trim();
        }

        static string XmlValue(string xml, string tag)
        {
            var m = Regex.Match(xml ?? "", "<" + tag + ">([^<]*)</" + tag + ">");
            return m.Success ? m.Groups[1].Value.Trim() : null;
        }

        static string FindAppsJson(string sunshineDir)
        {
            string configDir = Path.Combine(sunshineDir, "config");
            string conf = Path.Combine(configDir, "sunshine.conf");
            try
            {
                if (File.Exists(conf))
                {
                    foreach (string line in File.ReadAllLines(conf))
                    {
                        string t = line.Trim();
                        if (!t.StartsWith("file_apps")) continue;
                        int eq = t.IndexOf('=');
                        if (eq < 0) continue;
                        string f = t.Substring(eq + 1).Trim();
                        if (!Path.IsPathRooted(f)) f = Path.Combine(configDir, f);
                        if (File.Exists(f)) return f;
                    }
                }
            }
            catch { }
            string def = Path.Combine(configDir, "apps.json");
            return File.Exists(def) ? def : null;
        }

        /// Returns (name, id) for every app in apps.json, mirroring Sunshine's process.cpp.
        static List<KeyValuePair<string, string>> ComputeAppIds(string appsJson, string assetsDir)
        {
            var result = new List<KeyValuePair<string, string>>();
            var root = new JavaScriptSerializer().DeserializeObject(File.ReadAllText(appsJson)) as Dictionary<string, object>;
            if (root == null || !root.ContainsKey("apps")) return result;
            var apps = root["apps"] as object[];
            if (apps == null) return result;

            string defaultImage = Path.GetFullPath(Path.Combine(assetsDir, "box.png"));
            var ids = new HashSet<string>();
            int index = 0;
            foreach (object o in apps)
            {
                var app = o as Dictionary<string, object>;
                if (app == null) continue;
                string name = app.ContainsKey("name") ? Convert.ToString(app["name"]) : "";
                string image = app.ContainsKey("image-path") ? Convert.ToString(app["image-path"]) : "";

                var sb = new StringBuilder(name);
                string resolved = ResolveImage(image, assetsDir);
                if (resolved != null && !string.Equals(Path.GetFullPath(resolved), defaultImage, StringComparison.OrdinalIgnoreCase))
                {
                    string hash = Sha256Hex(resolved);
                    sb.Append(hash ?? resolved);
                }
                string noIndex = sb.ToString();
                string withIndex = noIndex + index;
                index++;

                string id = SunshineId(noIndex);
                if (ids.Contains(id)) id = SunshineId(withIndex);
                ids.Add(id);
                result.Add(new KeyValuePair<string, string>(name, id));
            }
            return result;
        }

        static string ResolveImage(string imagePath, string assetsDir)
        {
            if (string.IsNullOrEmpty(imagePath)) return null;
            if (!string.Equals(Path.GetExtension(imagePath), ".png", StringComparison.OrdinalIgnoreCase)) return null;
            try
            {
                string inAssets = Path.Combine(assetsDir, imagePath);
                if (File.Exists(inAssets)) return IsPng(inAssets) ? inAssets : null;
                if (imagePath == "./assets/steam.png") return Path.Combine(assetsDir, "steam.png");
                if (File.Exists(imagePath) && IsPng(imagePath)) return imagePath;
            }
            catch { }
            return null;
        }

        static bool IsPng(string path)
        {
            try
            {
                byte[] sig = { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
                using (var f = File.OpenRead(path))
                {
                    var head = new byte[8];
                    if (f.Read(head, 0, 8) != 8) return false;
                    for (int i = 0; i < 8; i++) if (head[i] != sig[i]) return false;
                    return true;
                }
            }
            catch { return false; }
        }

        static string Sha256Hex(string path)
        {
            try
            {
                using (var sha = SHA256.Create())
                using (var f = File.OpenRead(path))
                {
                    var sb = new StringBuilder();
                    foreach (byte b in sha.ComputeHash(f)) sb.Append(b.ToString("x2"));
                    return sb.ToString();
                }
            }
            catch { return null; }
        }

        static uint[] crcTable;

        static string SunshineId(string input)
        {
            if (crcTable == null)
            {
                crcTable = new uint[256];
                for (uint n = 0; n < 256; n++)
                {
                    uint c = n;
                    for (int k = 0; k < 8; k++) c = (c & 1) != 0 ? 0xEDB88320u ^ (c >> 1) : c >> 1;
                    crcTable[n] = c;
                }
            }
            uint crc = 0xFFFFFFFFu;
            foreach (byte b in Encoding.UTF8.GetBytes(input)) crc = crcTable[(crc ^ b) & 0xFF] ^ (crc >> 8);
            crc ^= 0xFFFFFFFFu;
            long signed = unchecked((int)crc);
            return Math.Abs(signed).ToString();
        }

        void LoadPcs()
        {
            pcs.Clear();
            try
            {
                if (File.Exists(pcsPath))
                {
                    foreach (string line in File.ReadAllLines(pcsPath))
                    {
                        string[] p = line.Split('|');
                        if (p.Length < 4) continue;
                        pcs.Add(new Pc { Name = p[0], Uuid = p[1], AppId = p[2], AppName = p[3] });
                    }
                }
            }
            catch { }
            RefreshPcBox(pcs.Count > 0 ? pcs[0] : null);
        }

        void WritePcs()
        {
            var lines = new List<string>();
            foreach (Pc pc in pcs) lines.Add(pc.Serialize());
            try { File.WriteAllLines(pcsPath, lines.ToArray()); } catch { }
        }

        void RefreshPcBox(Pc select)
        {
            pcBox.Items.Clear();
            foreach (Pc pc in pcs) pcBox.Items.Add(pc);
            if (select != null) pcBox.SelectedItem = select;
        }

        /// Returns the adb serial ("ip:5555") or null if the TV is not reachable/authorized.
        string Connect(string ip)
        {
            adbPath = FindAdb();
            if (adbPath == null)
            {
                Log("adb wurde nicht gefunden.");
                Log("Installation: winget install Google.PlatformTools");
                Status("adb fehlt.");
                return null;
            }
            Log("adb: " + adbPath);

            string serial = ip.Contains(":") ? ip : ip + ":5555";
            Status("Verbinde mit " + serial + " …");
            Log(Adb("connect " + serial, 20000).Trim());

            string state = Adb("-s " + serial + " get-state", 10000).Trim();
            if (state != "device")
            {
                Log("Status: " + state);
                Log("Am Fernseher die Abfrage \"Debugging zulassen?\" bestätigen und erneut versuchen.");
                Log("ADB-Debugging muss in den Entwickleroptionen aktiv sein.");
                Status("Fernseher nicht verbunden.");
                return null;
            }
            return serial;
        }

        string InstalledVersion(string serial)
        {
            string output = Adb("-s " + serial + " shell dumpsys package " + PackageName, 20000);
            foreach (string raw in output.Split('\n'))
            {
                string line = raw.Trim();
                if (line.StartsWith("versionName=")) return line.Substring("versionName=".Length);
            }
            return null;
        }

        // ------------------------------------------------------------------ helpers

        static string FindAdb()
        {
            string local = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            string[] candidates =
            {
                Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "adb.exe"),
                Path.Combine(local, @"Microsoft\WinGet\Links\adb.exe"),
                @"C:\platform-tools\adb.exe",
                Path.Combine(local, @"Android\Sdk\platform-tools\adb.exe"),
            };
            foreach (string c in candidates) if (File.Exists(c)) return c;

            string path = Environment.GetEnvironmentVariable("PATH") ?? "";
            foreach (string dir in path.Split(';'))
            {
                try
                {
                    string c = Path.Combine(dir.Trim(), "adb.exe");
                    if (File.Exists(c)) return c;
                }
                catch { }
            }

            string winget = Path.Combine(local, @"Microsoft\WinGet\Packages");
            if (Directory.Exists(winget))
            {
                try
                {
                    foreach (string c in Directory.GetFiles(winget, "adb.exe", SearchOption.AllDirectories)) return c;
                }
                catch { }
            }
            return null;
        }

        string Adb(string args, int timeoutMs)
        {
            var psi = new ProcessStartInfo(adbPath, args)
            {
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
            };
            using (var p = Process.Start(psi))
            {
                string stdout = "";
                string stderr = "";
                var outThread = new Thread(delegate () { stdout = p.StandardOutput.ReadToEnd(); });
                var errThread = new Thread(delegate () { stderr = p.StandardError.ReadToEnd(); });
                outThread.Start();
                errThread.Start();
                if (!p.WaitForExit(timeoutMs))
                {
                    try { p.Kill(); } catch { }
                    return "Zeitüberschreitung bei: adb " + args;
                }
                outThread.Join();
                errThread.Join();
                return stdout + stderr;
            }
        }

        /// Downloads url to file (returns null) or, if file is null, returns the content as string.
        static string Download(string url, string file)
        {
            ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072; // TLS 1.2
            using (var wc = new WebClient())
            {
                wc.Headers[HttpRequestHeader.UserAgent] = "C3-Updater";
                if (file == null)
                {
                    try { return wc.DownloadString(url); } catch { return null; }
                }
                wc.DownloadFile(url, file);
                return null;
            }
        }

        /// Keeps the last used IP so an unsaved device is still prefilled next time.
        void RememberIp(string ip)
        {
            try { File.WriteAllText(configPath, ip); } catch { }
        }

        void SetBusy(bool busy)
        {
            updateButton.Enabled = !busy;
            startButton.Enabled = !busy;
            redirectOnButton.Enabled = !busy;
            redirectOffButton.Enabled = !busy;
            addPcButton.Enabled = !busy;
            removePcButton.Enabled = !busy;
            pcBox.Enabled = !busy;
            ipBox.Enabled = !busy;
            nameBox.Enabled = !busy;
            deviceBox.Enabled = !busy;
            saveButton.Enabled = !busy;
            removeButton.Enabled = !busy && devices.Count > 0;
            Cursor = busy ? Cursors.WaitCursor : Cursors.Default;
        }

        void Log(string line)
        {
            UI(delegate { log.AppendText(line + Environment.NewLine); });
        }

        void Status(string text)
        {
            UI(delegate { statusLabel.Text = text; });
        }

        void UI(MethodInvoker action)
        {
            if (IsDisposed) return;
            if (InvokeRequired) BeginInvoke(action); else action();
        }

        [STAThread]
        static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new UpdaterForm());
        }
    }
}
