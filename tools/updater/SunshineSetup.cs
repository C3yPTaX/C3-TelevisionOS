// C3 Updater – "Dieser PC · Sunshine": erkennt System, Grafik, Netzwerk und Sunshine,
// installiert Sunshine auf Wunsch über winget (offizielles Paket von LizardByte) oder,
// falls winget fehlt, über das offizielle Installationsprogramm aus dem GitHub-Release.
// Benutzer/Passwort von Sunshine legt der Nutzer selbst in der Weboberfläche an.

using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Management;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Threading;
using Microsoft.Win32;

namespace C3
{
    public partial class UpdaterForm
    {
        const string WingetId = "LizardByte.Sunshine";
        const string SunshineInstallerUrl = "https://github.com/LizardByte/Sunshine/releases/latest/download/sunshine-windows-installer.exe";
        const string SunshineWebUi = "https://localhost:47990";

        bool sunshineButtonReady;
        bool sunshineInstalled;
        bool sunshineRunning;

        void DetectSystemAsync()
        {
            var t = new Thread(delegate ()
            {
                string os = SafeDetect(DetectOs);
                string gpu = SafeDetect(DetectGpu);
                string net = SafeDetect(DetectNetwork);
                bool running = SunshineResponds();
                bool installed = running || File.Exists(SunshineExe());
                UI(delegate { ShowSystem(os, gpu, net, installed, running); });
            });
            t.IsBackground = true;
            t.Start();
        }

        void ShowSystem(string os, string gpu, string net, bool installed, bool running)
        {
            sunshineInstalled = installed;
            sunshineRunning = running;
            string state = running ? "läuft" : installed ? "installiert, läuft nicht" : "nicht installiert";
            sysValues.Text = os + "\n" + gpu + "\n" + net + "\n" + state;

            if (running) sunshinePill.Set("Bereit", Theme.Ok, Theme.OkBg);
            else if (installed) sunshinePill.Set("Nicht gestartet", Theme.Warn, Theme.WarnBg);
            else sunshinePill.Set("Nicht installiert", Theme.Warn, Theme.WarnBg);
            sunshinePill.Left = CardW - 16 - sunshinePill.Width;

            sunshineButton.Text = installed ? "Sunshine-Weboberfläche öffnen" : "Sunshine installieren";
            sunshineButtonReady = true;
            sunshineButton.Enabled = updateButton.Enabled;
            sunshineButton.Invalidate();
        }

        void OnSunshineButton()
        {
            if (sunshineInstalled)
            {
                OpenUrl(SunshineWebUi);
                Status("Sunshine-Weboberfläche geöffnet. Die Zertifikatswarnung des Browsers bestätigen.");
                return;
            }
            RunAsync(Job.InstallSunshine);
        }

        void DoInstallSunshine()
        {
            Status("Installiere Sunshine … (Windows fragt nach Administratorrechten)");
            string winget = FindWinget();
            bool ok;
            if (winget != null)
            {
                Log("Installation über winget: " + WingetId);
                string args = "install --id " + WingetId + " -e --silent --accept-package-agreements --accept-source-agreements";
                ok = RunAndLog(winget, args, 20 * 60 * 1000);
            }
            else
            {
                Log("winget nicht gefunden – lade das offizielle Installationsprogramm von GitHub.");
                string installer = Path.Combine(Path.GetTempPath(), "sunshine-windows-installer.exe");
                Download(SunshineInstallerUrl, installer);
                Log("Geladen: " + new FileInfo(installer).Length / 1024 + " KB");
                var psi = new ProcessStartInfo(installer) { UseShellExecute = true, Verb = "runas" };
                using (var p = Process.Start(psi))
                {
                    p.WaitForExit();
                    ok = p.ExitCode == 0;
                }
            }

            Status("Warte auf Sunshine …");
            bool running = false;
            for (int i = 0; i < 45 && !running; i++)
            {
                Thread.Sleep(2000);
                running = SunshineResponds();
            }
            bool installed = running || File.Exists(SunshineExe());
            Log(installed ? "Sunshine ist installiert." : "Sunshine wurde nicht gefunden (Installation abgebrochen?).");
            UI(delegate { ShowSystem(sysValues.Text.Split('\n')[0], sysValues.Text.Split('\n')[1], sysValues.Text.Split('\n')[2], installed, running); });

            if (installed)
            {
                OpenUrl(SunshineWebUi);
                Status("Sunshine installiert. Im Browser Benutzer und Passwort anlegen, dann Moonlight am TV koppeln.");
                Log("Nächste Schritte: 1) Benutzer/Passwort in der Weboberfläche anlegen  2) Moonlight am TV öffnen, PC wählen, PIN in Sunshine eingeben  3) 'Diesen PC hinzufügen'.");
            }
            else if (!ok)
            {
                Status("Installation nicht abgeschlossen – Details im Protokoll.");
            }
        }

        // ------------------------------------------------------------------ detection

        static string SafeDetect(Func<string> f)
        {
            try { return f(); } catch { return "unbekannt"; }
        }

        static string DetectOs()
        {
            using (var key = Registry.LocalMachine.OpenSubKey(@"SOFTWARE\Microsoft\Windows NT\CurrentVersion"))
            {
                string name = key == null ? "Windows" : Convert.ToString(key.GetValue("ProductName", "Windows"));
                string display = key == null ? "" : Convert.ToString(key.GetValue("DisplayVersion", ""));
                int build;
                int.TryParse(key == null ? "0" : Convert.ToString(key.GetValue("CurrentBuildNumber", "0")), out build);
                if (build >= 22000) name = name.Replace("Windows 10", "Windows 11");
                string arch = Environment.GetEnvironmentVariable("PROCESSOR_ARCHITEW6432") ?? Environment.GetEnvironmentVariable("PROCESSOR_ARCHITECTURE") ?? "";
                string bits = arch.IndexOf("ARM", StringComparison.OrdinalIgnoreCase) >= 0 ? "ARM64" : Environment.Is64BitOperatingSystem ? "64-Bit" : "32-Bit";
                return name + (display.Length > 0 ? " " + display : "") + " · " + bits;
            }
        }

        static string DetectGpu()
        {
            var names = new List<string>();
            using (var searcher = new ManagementObjectSearcher("SELECT Name FROM Win32_VideoController"))
            {
                foreach (ManagementObject o in searcher.Get())
                {
                    string n = Convert.ToString(o["Name"]);
                    if (!string.IsNullOrEmpty(n) && n.IndexOf("Basic", StringComparison.OrdinalIgnoreCase) < 0) names.Add(n.Trim());
                }
            }
            if (names.Count == 0) return "keine Grafikkarte erkannt";
            string best = names.FirstOrDefault(n => n.IndexOf("NVIDIA", StringComparison.OrdinalIgnoreCase) >= 0)
                ?? names.FirstOrDefault(n => n.IndexOf("AMD", StringComparison.OrdinalIgnoreCase) >= 0 || n.IndexOf("Radeon", StringComparison.OrdinalIgnoreCase) >= 0)
                ?? names[0];
            string enc = best.IndexOf("NVIDIA", StringComparison.OrdinalIgnoreCase) >= 0 ? "NVENC"
                : (best.IndexOf("AMD", StringComparison.OrdinalIgnoreCase) >= 0 || best.IndexOf("Radeon", StringComparison.OrdinalIgnoreCase) >= 0) ? "AMF"
                : best.IndexOf("Intel", StringComparison.OrdinalIgnoreCase) >= 0 ? "Quick Sync"
                : "Software";
            return best + " (" + enc + ")";
        }

        static string DetectNetwork()
        {
            foreach (var nic in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (nic.OperationalStatus != OperationalStatus.Up) continue;
                if (nic.NetworkInterfaceType == NetworkInterfaceType.Loopback || nic.NetworkInterfaceType == NetworkInterfaceType.Tunnel) continue;
                var props = nic.GetIPProperties();
                if (props.GatewayAddresses.Count == 0) continue;
                var ip = props.UnicastAddresses.FirstOrDefault(a => a.Address.AddressFamily == AddressFamily.InterNetwork);
                if (ip == null) continue;
                string type = nic.NetworkInterfaceType == NetworkInterfaceType.Wireless80211 ? "WLAN" : "LAN";
                return type + " · " + ip.Address;
            }
            return "keine Verbindung erkannt";
        }

        static bool SunshineResponds()
        {
            try
            {
                var req = (HttpWebRequest)WebRequest.Create("http://localhost:47989/serverinfo");
                req.Timeout = 2000;
                using (var resp = (HttpWebResponse)req.GetResponse())
                    return true;
            }
            catch (WebException e)
            {
                // Sunshine answers unpaired requests with an error status – it is running.
                return e.Response != null;
            }
            catch { return false; }
        }

        static string SunshineExe()
        {
            return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), @"Sunshine\sunshine.exe");
        }

        static string FindWinget()
        {
            string alias = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), @"Microsoft\WindowsApps\winget.exe");
            if (File.Exists(alias)) return alias;
            foreach (string dir in (Environment.GetEnvironmentVariable("PATH") ?? "").Split(';'))
            {
                try
                {
                    string c = Path.Combine(dir.Trim(), "winget.exe");
                    if (File.Exists(c)) return c;
                }
                catch { }
            }
            return null;
        }

        bool RunAndLog(string exe, string args, int timeoutMs)
        {
            var psi = new ProcessStartInfo(exe, args)
            {
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
            };
            using (var p = Process.Start(psi))
            {
                p.OutputDataReceived += (s, e) => LogProgress(e.Data);
                p.ErrorDataReceived += (s, e) => LogProgress(e.Data);
                p.BeginOutputReadLine();
                p.BeginErrorReadLine();
                if (!p.WaitForExit(timeoutMs))
                {
                    try { p.Kill(); } catch { }
                    Log("Zeitüberschreitung bei der Installation.");
                    return false;
                }
                p.WaitForExit();
                Log("winget beendet mit Code " + p.ExitCode + ".");
                return p.ExitCode == 0;
            }
        }

        void LogProgress(string line)
        {
            if (string.IsNullOrEmpty(line)) return;
            string t = line.Trim().Trim('-', '\\', '|', '/', ' ');
            if (t.Length < 3) return;
            // Skip winget's progress bar lines.
            if (t.IndexOf('█') >= 0 || t.IndexOf('▒') >= 0) return;
            Log(t);
        }

        static void OpenUrl(string url)
        {
            try { Process.Start(new ProcessStartInfo(url) { UseShellExecute = true }); } catch { }
        }
    }
}
