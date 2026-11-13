// C3 Updater – lädt die neueste C3-APK von GitHub und installiert sie per ADB auf dem TV.
// Bewusst in C# 5 / .NET Framework 4 geschrieben: wird mit dem in Windows enthaltenen
// csc.exe kompiliert, braucht keine Laufzeit-Installation und keine Abhängigkeiten.

using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Net;
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
        readonly Label statusLabel = new Label();
        readonly TextBox log = new TextBox();

        readonly List<Device> devices = new List<Device>();
        readonly string configPath;
        readonly string devicesPath;
        string adbPath;

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

            Text = "C3 Updater";
            try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }
            ClientSize = new Size(560, 550);
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

            statusLabel.Location = new Point(22, 290);
            statusLabel.Size = new Size(520, 22);
            statusLabel.ForeColor = Muted;
            statusLabel.Text = "Bereit.";

            log.Location = new Point(20, 316);
            log.Size = new Size(520, 210);
            log.Multiline = true;
            log.ReadOnly = true;
            log.ScrollBars = ScrollBars.Vertical;
            log.BackColor = Panel;
            log.ForeColor = Fg;
            log.BorderStyle = BorderStyle.None;
            log.Font = new Font("Consolas", 9f);

            Controls.AddRange(new Control[] { title, deviceLabel, deviceBox, removeButton, nameLabel, nameBox, ipLabel, ipBox, saveButton, updateButton, startButton, redirectOnButton, redirectOffButton, statusLabel, log });
            AcceptButton = updateButton;
            LoadDevices();
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

        enum Job { Update, Start, RedirectOn, RedirectOff }

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
