// C3 Updater – lädt die neueste C3-APK von GitHub und installiert sie per ADB auf dem TV.
// Bewusst in C# 5 / .NET Framework 4 geschrieben: wird mit dem in Windows enthaltenen
// csc.exe kompiliert, braucht keine Laufzeit-Installation und keine Abhängigkeiten.

using System;
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

        static readonly Color Bg = Color.FromArgb(18, 20, 26);
        static readonly Color Panel = Color.FromArgb(38, 42, 51);
        static readonly Color Fg = Color.FromArgb(237, 239, 244);
        static readonly Color Muted = Color.FromArgb(140, 146, 160);
        static readonly Color Accent = Color.FromArgb(90, 169, 255);

        readonly TextBox ipBox = new TextBox();
        readonly Label statusLabel = new Label();
        readonly TextBox log = new TextBox();
        readonly Button updateButton = new Button();
        readonly Button startButton = new Button();
        readonly string configPath;
        string adbPath;

        public UpdaterForm()
        {
            string dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "C3Updater");
            Directory.CreateDirectory(dir);
            configPath = Path.Combine(dir, "tv-ip.txt");

            Text = "C3 Updater";
            try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }
            ClientSize = new Size(560, 420);
            FormBorderStyle = FormBorderStyle.FixedSingle;
            MaximizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = Bg;
            ForeColor = Fg;
            Font = new Font("Segoe UI", 10f);

            var title = new Label { Text = "C3 TelevisionOS", Font = new Font("Segoe UI Semibold", 16f), ForeColor = Fg, AutoSize = true, Location = new Point(20, 16) };
            var ipLabel = new Label { Text = "TV-IP-Adresse", ForeColor = Muted, AutoSize = true, Location = new Point(22, 64) };

            ipBox.Location = new Point(20, 86);
            ipBox.Size = new Size(220, 28);
            ipBox.BackColor = Panel;
            ipBox.ForeColor = Fg;
            ipBox.BorderStyle = BorderStyle.FixedSingle;
            ipBox.Text = LoadIp();

            StyleButton(updateButton, "TV aktualisieren", true);
            updateButton.Location = new Point(256, 82);
            updateButton.Size = new Size(160, 34);
            updateButton.Click += delegate { RunAsync(true); };

            StyleButton(startButton, "C3 starten", false);
            startButton.Location = new Point(424, 82);
            startButton.Size = new Size(116, 34);
            startButton.Click += delegate { RunAsync(false); };

            statusLabel.Location = new Point(22, 128);
            statusLabel.Size = new Size(520, 22);
            statusLabel.ForeColor = Muted;
            statusLabel.Text = "Bereit.";

            log.Location = new Point(20, 156);
            log.Size = new Size(520, 244);
            log.Multiline = true;
            log.ReadOnly = true;
            log.ScrollBars = ScrollBars.Vertical;
            log.BackColor = Panel;
            log.ForeColor = Fg;
            log.BorderStyle = BorderStyle.None;
            log.Font = new Font("Consolas", 9f);

            Controls.AddRange(new Control[] { title, ipLabel, ipBox, updateButton, startButton, statusLabel, log });
            AcceptButton = updateButton;
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

        // ------------------------------------------------------------------ workflow

        void RunAsync(bool update)
        {
            string ip = ipBox.Text.Trim();
            if (ip.Length == 0)
            {
                MessageBox.Show(this, "Bitte die IP-Adresse des Fernsehers eintragen.", "C3 Updater");
                return;
            }
            SaveIp(ip);
            SetBusy(true);
            log.Clear();

            var worker = new Thread(delegate ()
            {
                try
                {
                    if (update) DoUpdate(ip); else DoStart(ip);
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

        string LoadIp()
        {
            try { return File.Exists(configPath) ? File.ReadAllText(configPath).Trim() : ""; } catch { return ""; }
        }

        void SaveIp(string ip)
        {
            try { File.WriteAllText(configPath, ip); } catch { }
        }

        void SetBusy(bool busy)
        {
            updateButton.Enabled = !busy;
            startButton.Enabled = !busy;
            ipBox.Enabled = !busy;
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
